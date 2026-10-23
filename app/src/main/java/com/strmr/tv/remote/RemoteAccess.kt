package com.strmr.tv.remote

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.util.Base64
import com.strmr.tv.api.ApiClient
import com.strmr.tv.api.await
import com.strmr.tv.app.App
import com.strmr.tv.app.AppLog
import com.strmr.tv.app.Config
import com.strmr.tv.auth.SecureStore
import expo.modules.irohbridge.IrohBridgeNative
import java.security.SecureRandom
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody

class RemoteAccessError(message: String) : Exception(message)

/**
 * Remote access over iroh, as the Expo app pairs it: a connection code from the server's admin
 * UI resolves (mainline DHT) to the server's invite; a loopback proxy tunnels HTTP to it;
 * claiming the code registers this device with a fresh pairing credential. The server drops a
 * claimed code from the DHT, so later launches reconnect through the stored invite (its NodeID
 * is stable). The proxy's port changes per launch, so the pairing, not a URL, is what is saved.
 */
object RemoteAccess {
    /** Saved in [SecureStore]. */
    @Serializable
    data class Pairing(
        val invite: String,
        /** 32 random bytes, base64url: sent as `X-Remote-Access-Credential` on every proxied request. */
        val credential: String,
        val connectionCode: String,
    )

    var pairing: Pairing? = SecureStore.read(ACCOUNT)?.let { runCatching { Json.decodeFromString<Pairing>(it) }.getOrNull() }
        private set

    private const val ACCOUNT = "remoteAccess"
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val connectLock = Mutex()
    private var connecting: Deferred<HttpUrl>? = null
    private var networkCallback: ConnectivityManager.NetworkCallback? = null
    private var logPump: kotlinx.coroutines.Job? = null

    // Pairing

    /** Pairs with the server behind `code`; returns the proxy's base URL. */
    suspend fun pair(code: String): HttpUrl {
        val normalized = normalize(code)
            ?: throw RemoteAccessError("Enter the connection code from your server, like mshost-ABC123-DEF456-GHJ789.")
        AppLog.i("RemoteAccess", "pairing with code ${normalized.take(13)}…")
        val invite = call { IrohBridgeNative.resolveCode(normalized) }["invite"].string()
            ?: throw RemoteAccessError("No server answered for that code.")
        val base = startProxy(invite)
        val credential = makeCredential()
        prime(base, credential)
        claim(normalized, credential, base)
        val saved = Pairing(invite, credential, normalized)
        SecureStore.write(ACCOUNT, Json.encodeToString(saved))
        pairing = saved
        watchNetwork()
        AppLog.i("RemoteAccess", "paired; proxy at $base")
        return base
    }

    /**
     * The proxy's base URL for the saved pairing: the running proxy when it still answers,
     * else after telling iroh to look for a live path. Concurrent callers share one attempt.
     */
    suspend fun connect(): HttpUrl {
        val saved = pairing ?: throw RemoteAccessError("Not paired")
        val job = connectLock.withLock {
            connecting ?: scope.async {
                runCatching { call { IrohBridgeNative.notifyNetworkChange() } }
                val first = runCatching { startProxy(saved.invite).also { prime(it, saved.credential) } }
                first.getOrElse {
                    AppLog.w("RemoteAccess", "proxy unhealthy (${it.message}); retrying")
                    runCatching { call { IrohBridgeNative.notifyNetworkChange() } }
                    startProxy(saved.invite).also { base -> prime(base, saved.credential) }
                }
            }.also { connecting = it }
        }
        try {
            return job.await().also { watchNetwork() }
        } finally {
            connectLock.withLock { if (connecting === job) connecting = null }
        }
    }

    /** Unpairs this device (Change Server). */
    fun forget() {
        pairing = null
        SecureStore.delete(ACCOUNT)
        networkCallback?.let { runCatching { connectivity().unregisterNetworkCallback(it) } }
        networkCallback = null
    }

    // Bridge

    private suspend fun startProxy(invite: String): HttpUrl {
        val raw = call { IrohBridgeNative.startProxy(invite) }["url"].string()
            ?: throw RemoteAccessError("The bridge returned no address")
        // The bridge's URL ends in /api; request paths already start with it.
        return raw.trimEnd('/').removeSuffix("/api").toHttpUrlOrNull() ?: throw RemoteAccessError("The bridge returned a bad address")
    }

    /** One blocking bridge call off the main thread, its `{"ok":…}` envelope checked. */
    private suspend fun call(body: () -> String): JsonObject = withContext(Dispatchers.IO) {
        val text = try {
            body()
        } catch (e: UnsatisfiedLinkError) {
            throw RemoteAccessError("Remote access isn't in this build (run scripts/sync-vendor.sh).")
        }
        val json = runCatching { Json.parseToJsonElement(text) as JsonObject }.getOrNull()
            ?: throw RemoteAccessError("The bridge returned nothing")
        if (json["ok"]?.jsonPrimitive?.booleanOrNull != true) throw RemoteAccessError(json["error"].string() ?: "Remote access failed")
        json
    }

    private fun kotlinx.serialization.json.JsonElement?.string() = runCatching { this?.jsonPrimitive?.contentOrNull }.getOrNull()?.ifEmpty { null }

    // Requests

    /**
     * `GET /health` through the proxy with this device's identity. Also primes the proxy: it
     * remembers the first client ID and credential it sees and adds them to later requests, so
     * header-less media requests pass the server's pairing check.
     */
    private suspend fun prime(base: HttpUrl, credential: String) {
        val request = Request.Builder().url(base.resolve("/health")!!)
            .header("X-Client-ID", Config.clientId).header("X-Remote-Access-Credential", credential).build()
        val response = ApiClient.http.newCall(request).apply { timeout().timeout(10, TimeUnit.SECONDS) }.await()
        if (response.code != 200) throw RemoteAccessError("The server didn't answer over remote access.")
    }

    /** `POST /api/remote-access/invites/claim`: registers this device under the code. */
    private suspend fun claim(code: String, credential: String, base: HttpUrl) {
        val body = buildJsonObject {
            put("token", code)
            put("peerId", Config.clientId)
            put("credential", credential)
        }.toString().toRequestBody("application/json".toMediaType())
        val request = Request.Builder().url(base.resolve("/api/remote-access/invites/claim")!!).post(body)
            .header("X-Client-ID", Config.clientId).header("X-Remote-Access-Credential", credential).build()
        val response = ApiClient.http.newCall(request).apply { timeout().timeout(20, TimeUnit.SECONDS) }.await()
        if (!response.isSuccessful) {
            val message = runCatching { (Json.parseToJsonElement(response.text) as JsonObject)["error"].string() }.getOrNull()
            throw RemoteAccessError(message ?: "The server refused the code (HTTP ${response.code}).")
        }
    }

    // Network changes

    /** The bridge buffers its log; it goes into the app log (and so the uploaded log) every few seconds. */
    private fun pumpLogs() {
        if (logPump != null) return
        logPump = scope.launch {
            while (true) {
                runCatching { IrohBridgeNative.drainLogs() }.getOrNull()
                    ?.lineSequence()?.filter { it.isNotBlank() }?.forEach { AppLog.i("Iroh", it) }
                kotlinx.coroutines.delay(3_000)
            }
        }
    }

    /** iroh is told when the network changes, so it moves to a live path at once. */
    private fun watchNetwork() {
        pumpLogs()
        if (networkCallback != null) return
        val callback = object : ConnectivityManager.NetworkCallback() {
            private var first = true

            override fun onAvailable(network: Network) {
                // The first report is the current network, not a change.
                if (first) {
                    first = false
                    return
                }
                scope.launch { runCatching { call { IrohBridgeNative.notifyNetworkChange() } } }
            }
        }
        runCatching { connectivity().registerDefaultNetworkCallback(callback) }.onSuccess { networkCallback = callback }
    }

    private fun connectivity() = App.context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager

    // Codes

    /** Crockford base32 without I, L, O, U. */
    private const val ALPHABET = "0123456789ABCDEFGHJKMNPQRSTVWXYZ"

    /**
     * `mshost-` plus two or three groups of six, folded as the server encodes them (case, O→0,
     * I/L→1, separators optional), as the Expo login screen accepts them. Null otherwise.
     */
    fun normalize(input: String): String? {
        var compact = input.filter { it.isLetterOrDigit() }.uppercase()
        if (compact.startsWith("MSHOST")) compact = compact.drop(6)
        val body = compact.map { c -> when (c) { 'O' -> '0'; 'I', 'L' -> '1'; else -> c } }.filter { it in ALPHABET }
        if (body.size != 12 && body.size != 18) return null
        return "mshost-" + body.chunked(6).joinToString("-") { it.joinToString("") }
    }

    private fun makeCredential(): String {
        val bytes = ByteArray(32).also { SecureRandom().nextBytes(it) }
        return Base64.encodeToString(bytes, Base64.URL_SAFE or Base64.NO_PADDING or Base64.NO_WRAP)
    }
}

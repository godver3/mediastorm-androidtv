package com.strmr.tv.api

import com.strmr.tv.app.Config
import com.strmr.tv.auth.AuthSession
import com.strmr.tv.auth.ProfileUnlockStore
import java.io.IOException
import java.net.ConnectException
import java.net.NoRouteToHostException
import java.net.SocketException
import java.net.SocketTimeoutException
import java.util.concurrent.TimeUnit
import kotlin.coroutines.resumeWithException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.json.Json
import kotlinx.serialization.serializer
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonPrimitive
import okhttp3.Call
import okhttp3.Callback
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.Dispatcher
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response

/** The app's JSON: lenient about unknown keys and nulls (a top-level val so tests can use it off-device). */
val ApiJson = Json { ignoreUnknownKeys = true; explicitNulls = false; coerceInputValues = true }

/**
 * Thin client for the strmr backend. Holds the server, the account session (with refresh)
 * and the active profile; [com.strmr.tv.app.AppSession] decides which of those are set.
 * Calls suspend and run their I/O on OkHttp's threads.
 */
object ApiClient {
    /** Server origin (plus any reverse-proxy path), without `/api`. */
    @Volatile var baseUrl: HttpUrl = "http://127.0.0.1:7777/".toHttpUrl()
    /**
     * Over iroh remote access: the pairing credential, sent with every request
     * (`X-Remote-Access-Credential`) beside `X-Client-ID`.
     */
    @Volatile var remoteCredential: String? = null
    /**
     * Over remote access: brings the tunnel back after a transport failure and returns its
     * base URL (the proxy's port can change). Set by AppSession.
     */
    @Volatile var recoverTransport: (suspend () -> HttpUrl?)? = null
    /** Persisted to [com.strmr.tv.auth.SecureStore] whenever it changes (login, refresh). */
    @Volatile var session: AuthSession? = null
        set(value) {
            val old = field
            field = value
            if (value != null && value != old) value.save()
        }
    @Volatile var profileId: String = "default"
        private set
    /** Display name of the active profile, for the dashboard's stream attribution. */
    @Volatile var profileName: String? = null
        private set
    /** Called (on an I/O thread) when the session is rejected and a refresh fails. */
    @Volatile var onSignedOut: (() -> Unit)? = null
    /**
     * Asked when a request hits a locked profile (`403 profile_locked`): prompt for the PIN
     * and return true once it is unlocked, so the request is retried.
     */
    @Volatile var onProfileLocked: (suspend (String) -> Boolean)? = null
    /** Unlock tokens for PIN-protected profiles, sent as `X-Profile-Unlock`. */
    val unlocks by lazy { ProfileUnlockStore() }

    val json = ApiJson
    /**
     * No read timeout of its own: every call sets a whole-call timeout instead. Every call goes
     * to one host (the server, or the iroh bridge), so OkHttp's default of 5 per host would
     * queue Continue Watching behind a Home view's slow list rows.
     */
    val http: OkHttpClient = OkHttpClient.Builder()
        .dispatcher(Dispatcher().apply { maxRequestsPerHost = 16 })
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(0, TimeUnit.SECONDS)
        .build()

    const val UNLOCK_HEADER = "X-Profile-Unlock"
    private val jsonType = "application/json".toMediaType()
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    val token: String? get() = session?.token
    val profilePath: String get() = "/api/users/${segment(profileId)}"

    fun activate(profileId: String, profileName: String? = null) {
        this.profileId = profileId
        this.profileName = profileName
        unlocks.keepOnly(profileId)
    }

    /** RFC 3986 unreserved characters; everything else is escaped in segments and query values. */
    private val unreserved = (('A'..'Z') + ('a'..'z') + ('0'..'9') + listOf('-', '.', '_', '~')).toSet()

    /** Percent-encodes one path segment or query value (a profile id may contain anything). */
    fun segment(value: String): String = buildString {
        for (byte in value.toByteArray(Charsets.UTF_8)) {
            val c = byte.toInt().toChar()
            if (byte >= 0 && c in unreserved) append(c) else append("%%%02X".format(byte.toInt() and 0xFF))
        }
    }

    /**
     * `path` must already be percent-encoded (use [segment] for ids); query values are
     * encoded here. `encodedQuery` (already encoded, e.g. with repeated keys) is appended as is.
     */
    fun url(path: String, query: Map<String, String> = emptyMap(), encodedQuery: String? = null): HttpUrl {
        val encoded = listOfNotNull(
            query.entries.sortedBy { it.key }.joinToString("&") { "${segment(it.key)}=${segment(it.value)}" }.ifEmpty { null },
            encodedQuery?.ifEmpty { null },
        ).joinToString("&").ifEmpty { null }
        return baseUrl.newBuilder()
            .encodedPath(baseUrl.encodedPath.trimEnd('/') + path)
            .encodedQuery(encoded)
            .build()
    }

    // Plumbing

    /**
     * A JSON array response, decoded lossily ([LossyList]): a `null` body (Go's empty slice) is
     * empty and an item that fails to decode is skipped and logged instead of failing the list.
     */
    suspend inline fun <reified T> sendList(
        method: String, path: String,
        query: Map<String, String> = emptyMap(),
        body: JsonElement? = null,
        promptOnLock: Boolean = true,
        timeoutMs: Long = 30_000,
    ): List<T> = LossyList.decode(json, serializer<T>(), sendRaw(method, path, query, body, promptOnLock = promptOnLock, timeoutMs = timeoutMs))

    suspend inline fun <reified T> send(
        method: String, path: String,
        query: Map<String, String> = emptyMap(),
        body: JsonElement? = null,
        authorized: Boolean = true,
        promptOnLock: Boolean = true,
        timeoutMs: Long = 30_000,
        headers: Map<String, String> = emptyMap(),
    ): T = json.decodeFromString(sendRaw(method, path, query, body, authorized, promptOnLock, timeoutMs, headers = headers))

    /**
     * Authorized calls refresh the session ahead of expiry, and when the server says the
     * session is gone (`unauthorized` / `session_expired` / `account_expired`, or a bare 401
     * from older servers) refresh once and retry; if that fails the app is signed out
     * ([onSignedOut]). Other coded 401s (a wrong PIN) are thrown as-is.
     *
     * Requests naming a profile carry its unlock token. A `profile_locked` answer asks
     * [onProfileLocked] for the PIN and retries once; `promptOnLock = false` (background
     * calls, e.g. during playback) just throws.
     */
    suspend fun sendRaw(
        method: String, path: String,
        query: Map<String, String> = emptyMap(),
        body: JsonElement? = null,
        authorized: Boolean = true,
        promptOnLock: Boolean = true,
        timeoutMs: Long = 30_000,
        encodedQuery: String? = null,
        headers: Map<String, String> = emptyMap(),
    ): String {
        val prepared = Prepared(method, url(path, query, encodedQuery), body?.toString(), timeoutMs)
        prepared.headers.putAll(headers)
        prepared.headers["X-Client-ID"] = Config.clientId
        remoteCredential?.let { prepared.headers["X-Remote-Access-Credential"] = it }
        if (!authorized) return perform(prepared)

        val named = profileIds(path, query, body as? JsonObject)
        applyUnlocks(prepared, named)
        try {
            return performAuthorized(prepared)
        } catch (e: ApiError) {
            if (!e.isProfileLocked) throw e
            val locked = e.failure?.profileId ?: named.firstOrNull() ?: profileId
            unlocks.remove(locked) // missing, expired or revoked
            val prompt = onProfileLocked
            if (!promptOnLock || prompt == null || !prompt(locked)) throw e
            applyUnlocks(prepared, if (locked in named) named else named + locked)
            return performAuthorized(prepared)
        }
    }

    /** A request that can be re-sent with new headers or a new host. */
    private class Prepared(val method: String, var url: HttpUrl, val body: String?, val timeoutMs: Long) {
        val headers = mutableMapOf<String, String>()
    }

    private suspend fun performAuthorized(req: Prepared): String {
        if (session?.needsRefresh == true && refreshSession() == RefreshOutcome.REJECTED) {
            signOutLocally()
            throw ApiError.SignedOut()
        }
        req.headers["Authorization"] = "Bearer ${token ?: throw ApiError.SignedOut()}"
        try {
            return perform(req)
        } catch (e: ApiError) {
            if (!e.invalidatesSession) throw e
            when (refreshSession()) {
                RefreshOutcome.REFRESHED -> Unit
                RefreshOutcome.FAILED -> throw e
                RefreshOutcome.REJECTED -> {
                    signOutLocally()
                    throw ApiError.SignedOut()
                }
            }
            req.headers["Authorization"] = "Bearer ${token ?: throw ApiError.SignedOut()}"
            return perform(req)
        }
    }

    /** `X-Profile-Unlock` with every stored token for the profiles a request names. */
    private fun applyUnlocks(req: Prepared, profiles: List<String>) {
        val tokens = profiles.mapNotNull { unlocks.token(it) }
        if (tokens.isEmpty()) req.headers.remove(UNLOCK_HEADER) else req.headers[UNLOCK_HEADER] = tokens.joinToString(",")
    }

    /**
     * Profile ids a request names, the way the server's PIN middleware finds them:
     * `/api/users/{id}/…` or `/api/profiles/{id}/…`, `profileId` / `userId` in the query,
     * and top-level `profileId` / `userId` in a JSON body.
     */
    fun profileIds(path: String, query: Map<String, String>, body: JsonObject?): List<String> {
        val ids = mutableListOf<String>()
        fun add(value: String?) {
            val v = value?.trim()?.ifEmpty { null } ?: return
            if (v !in ids) ids += v
        }
        val parts = path.split('/').filter { it.isNotEmpty() }
        if (parts.size >= 3 && parts[0] == "api" && parts[1] in setOf("users", "profiles")) {
            add(runCatching { java.net.URLDecoder.decode(parts[2], "UTF-8") }.getOrDefault(parts[2]))
        }
        for (key in listOf("profileId", "userId")) {
            add(query[key])
            add(runCatching { body?.get(key)?.jsonPrimitive?.contentOrNull }.getOrNull())
        }
        return ids
    }

    private suspend fun perform(req: Prepared, retried: Boolean = false): String {
        val request = Request.Builder().url(req.url).apply {
            req.headers.forEach { (k, v) -> header(k, v) }
            val body = req.body?.toRequestBody(jsonType)
            method(req.method, body ?: if (req.method == "POST" || req.method == "PUT") ByteArray(0).toRequestBody() else null)
        }.build()
        val response = try {
            http.newCall(request).apply { timeout().timeout(req.timeoutMs, TimeUnit.MILLISECONDS) }.await()
        } catch (e: IOException) {
            // Through the iroh proxy, the first request after a sleep or a path change can fail
            // while the connection is re-established: reconnect, then retry once.
            if (retried || remoteCredential == null || !isTransportFailure(e)) throw e
            val base = recoverTransport?.invoke() ?: throw e
            req.url = req.url.newBuilder().scheme(base.scheme).host(base.host).port(base.port).build()
            return perform(req, retried = true)
        }
        if (!response.isSuccessful) throw ApiError.Http(HttpFailure(response.code, response.text, response.retryAfter))
        return response.text
    }

    /** Failures a reconnect can fix, as tvOS counts them: refused, lost, timed out (a whole-call timeout too). */
    private fun isTransportFailure(e: IOException) =
        e is ConnectException || e is SocketTimeoutException || e is NoRouteToHostException || e is SocketException ||
            (e is java.io.InterruptedIOException && e.message == "timeout") ||
            e.message?.contains("unexpected end of stream", ignoreCase = true) == true

    enum class RefreshOutcome { REFRESHED, REJECTED, FAILED }

    private val refreshLock = Mutex()
    private var refreshing: Deferred<RefreshOutcome>? = null

    /**
     * `POST /api/auth/refresh`, coalesced so concurrent 401s share one rotation.
     * REJECTED means the server refused the session; FAILED (network) keeps it.
     */
    suspend fun refreshSession(): RefreshOutcome {
        val job = refreshLock.withLock {
            refreshing ?: scope.async { doRefresh() }.also { refreshing = it }
        }
        val outcome = job.await()
        refreshLock.withLock { if (refreshing === job) refreshing = null }
        return outcome
    }

    private suspend fun doRefresh(): RefreshOutcome {
        val current = session ?: return RefreshOutcome.REJECTED
        val req = Prepared("POST", url("/api/auth/refresh"), null, 20_000).apply {
            headers["X-Client-ID"] = Config.clientId
            headers["Authorization"] = "Bearer ${current.token}"
            remoteCredential?.let { headers["X-Remote-Access-Credential"] = it }
        }
        return try {
            session = AuthSession.from(json.decodeFromString<LoginResponse>(perform(req)))
            RefreshOutcome.REFRESHED
        } catch (e: ApiError) {
            if (e.invalidatesSession || e.statusCode in setOf(403, 404)) RefreshOutcome.REJECTED else RefreshOutcome.FAILED
        } catch (e: Exception) {
            RefreshOutcome.FAILED
        }
    }

    fun signOutLocally() {
        if (session == null) return
        forgetSession()
        onSignedOut?.invoke()
    }

    /** Drops the session and the profile unlocks bound to it. */
    fun forgetSession() {
        session = null
        AuthSession.clear()
        unlocks.removeAll()
    }
}

/** A finished response, its body already read off the main thread. */
class HttpResult(val code: Int, val text: String, val retryAfter: String?) {
    val isSuccessful get() = code in 200..299
}

/**
 * Runs the call on OkHttp's dispatcher and reads the body there; cancelling the coroutine
 * cancels the call.
 */
suspend fun Call.await(): HttpResult = suspendCancellableCoroutine { cont ->
    enqueue(object : Callback {
        override fun onResponse(call: Call, response: Response) {
            val result = try {
                response.use { HttpResult(it.code, it.body?.string() ?: "", it.header("Retry-After")) }
            } catch (e: IOException) {
                cont.resumeWithException(e)
                return
            }
            cont.resume(result) { _, _, _ -> }
        }
        override fun onFailure(call: Call, e: IOException) = cont.resumeWithException(e)
    })
    cont.invokeOnCancellation { runCatching { cancel() } }
}

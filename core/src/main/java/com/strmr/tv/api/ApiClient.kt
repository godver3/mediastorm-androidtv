package com.strmr.tv.api

import com.strmr.tv.app.Config
import com.strmr.tv.library.ProfileDataContext
import com.strmr.tv.auth.AuthSession
import com.strmr.tv.auth.ProfileUnlockStore
import java.io.IOException
import java.util.concurrent.TimeUnit
import kotlin.coroutines.resumeWithException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
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
    private val requestContexts = RequestContext()
    /** Server origin (plus any reverse-proxy path), without `/api`. */
    @Volatile var baseUrl: HttpUrl = "http://127.0.0.1:7777/".toHttpUrl()
        set(value) {
            requestContexts.changeAuthentication(change = {
                val changed = field != value
                field = value
                changed
            })
        }
    /**
     * Over iroh remote access: the pairing credential, sent with every request
     * (`X-Remote-Access-Credential`) beside `X-Client-ID`.
     */
    @Volatile var remoteCredential: String? = null
        set(value) {
            requestContexts.changeAuthentication(change = {
                val changed = field != value
                field = value
                changed
            })
        }
    /**
     * Over remote access: brings the tunnel back after a transport failure and returns its
     * base URL (the proxy's port can change). Set by AppSession.
     */
    @Volatile var recoverTransport: (suspend (Long) -> HttpUrl?)? = null
    /**
     * Told (on an I/O thread) about every request that failed in transport (no answer at all),
     * so the phone app can check whether the server is still there. The TV app leaves it unset.
     */
    @Volatile var onTransportFailure: ((IOException) -> Unit)? = null
    /** Persisted to [com.strmr.tv.auth.SecureStore] whenever it changes (login, refresh). */
    @Volatile private var storedSession: AuthSession? = null
    var session: AuthSession?
        get() = storedSession
        set(value) {
            requestContexts.changeAuthentication(change = {
                val changed = storedSession != value
                storedSession = value
                changed
            }, afterChange = { value?.save() })
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
        requestContexts.changeProfile {
            this.profileId = profileId
            this.profileName = profileName
            unlocks.keepOnly(profileId)
        }
    }

    /** Publish a server and its stored session as one identity transition. */
    fun configureServer(url: HttpUrl, account: AuthSession?) = requestContexts.serialized {
        baseUrl = url
        session = account
    }

    /** Commit a parsed reply only while its original profile/server identity remains active. */
    fun <T> inContext(expectedContext: Long, block: () -> T): T =
        requestContexts.capture(expectedContext).use(block)

    /** Best-effort remote logout cannot clear a replacement account after an await. */
    suspend fun revokeSession() {
        val context = requestContexts.captureAuthentication()
        val request = context.use {
            storedSession?.let { account ->
                Prepared("POST", url("/api/auth/logout"), null, 5_000, context).apply {
                    headers["X-Client-ID"] = Config.clientId
                    headers["Authorization"] = "Bearer ${account.token}"
                    remoteCredential?.let { headers["X-Remote-Access-Credential"] = it }
                }
            }
        }
        if (request != null) try {
            perform(request, recoverConnection = false)
        } catch (error: CancellationException) {
            throw error
        } catch (_: Exception) {
            context.check()
        }
        context.use { forgetSession() }
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
     * Responses decode on [Dispatchers.Default]: Home's rows all arrive at launch, and decoding
     * them on the main thread dropped frames while focus moved.
     */
    suspend inline fun <reified T> sendList(
        method: String, path: String,
        query: Map<String, String> = emptyMap(),
        body: JsonElement? = null,
        promptOnLock: Boolean = true,
        timeoutMs: Long = 30_000,
        expectedContext: Long? = null,
    ): List<T> {
        val captured = expectedContext ?: ProfileDataContext.current
        val raw = sendRaw(method, path, query, body, promptOnLock = promptOnLock, timeoutMs = timeoutMs, expectedContext = captured)
        val decoded = withContext(Dispatchers.Default) { LossyList.decode(json, serializer<T>(), raw) }
        ProfileDataContext.requireCurrent(captured)
        return decoded
    }

    suspend inline fun <reified T> send(
        method: String, path: String,
        query: Map<String, String> = emptyMap(),
        body: JsonElement? = null,
        authorized: Boolean = true,
        promptOnLock: Boolean = true,
        timeoutMs: Long = 30_000,
        headers: Map<String, String> = emptyMap(),
        expectedContext: Long? = null,
    ): T {
        val captured = expectedContext ?: ProfileDataContext.current
        val raw = sendRaw(method, path, query, body, authorized, promptOnLock, timeoutMs, headers = headers, expectedContext = captured)
        val decoded = withContext(Dispatchers.Default) { json.decodeFromString<T>(raw) }
        ProfileDataContext.requireCurrent(captured)
        return decoded
    }

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
        expectedContext: Long? = null,
    ): String {
        val context = requestContexts.capture(expectedContext)
        if (method == "GET") SportsFixtures.response(path)?.let { context.check(); return it }
        val prepared = context.use {
            Prepared(method, url(path, query, encodedQuery), body?.toString(), timeoutMs, context).apply {
                this.headers.putAll(headers)
                this.headers["X-Client-ID"] = Config.clientId
                remoteCredential?.let { this.headers["X-Remote-Access-Credential"] = it }
            }
        }
        if (!authorized) return perform(prepared)

        val named = profileIds(path, query, body as? JsonObject)
        context.use { applyUnlocks(prepared, named) }
        try {
            return performAuthorized(prepared)
        } catch (e: ApiError) {
            context.check()
            if (!e.isProfileLocked) throw e
            val locked = context.use { e.failure?.profileId ?: named.firstOrNull() ?: profileId }
            val prompt = context.use { unlocks.remove(locked); onProfileLocked }
            if (!promptOnLock || prompt == null || !prompt(locked)) { context.check(); throw e }
            context.use { applyUnlocks(prepared, if (locked in named) named else named + locked) }
            return performAuthorized(prepared)
        }
    }

    /** A request retains the identity under which its URL and headers were prepared. */
    private class Prepared(val method: String, var url: HttpUrl, val body: String?, val timeoutMs: Long, val context: RequestContext.Scope) {
        val headers = mutableMapOf<String, String>()
    }

    private suspend fun performAuthorized(req: Prepared): String = RequestAuthorization(
        req.context,
        token = { storedSession?.token },
        needsRefresh = { storedSession?.needsRefresh == true },
        refresh = { refreshSession(req.context.authenticationVersion) },
        signOut = { signOutLocally() },
        send = { value -> req.headers["Authorization"] = "Bearer $value"; perform(req) },
    ).execute()

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

    private suspend fun perform(req: Prepared, recoverConnection: Boolean = true): String {
        req.context.check()
        val request = Request.Builder().url(req.url).apply {
            req.headers.forEach { (k, v) -> header(k, v) }
            val body = req.body?.toRequestBody(jsonType)
            method(req.method, body ?: if (req.method == "POST" || req.method == "PUT") ByteArray(0).toRequestBody() else null)
        }.build()
        val response = ContextBoundTransport(
            req.context,
            remote = recoverConnection && req.headers.containsKey("X-Remote-Access-Credential"),
            recover = {
                val callback = req.context.use { recoverTransport }
                val expected = req.context.use { req.context.profileEpoch ?: ProfileDataContext.current }
                val base = callback?.invoke(expected)
                if (base == null) false else {
                    req.context.use { if (base != baseUrl) throw CancellationException("Request route changed") }
                    true
                }
            },
            onFailure = { error -> if (recoverConnection) onTransportFailure?.invoke(error) },
        ).execute {
            http.newCall(request).apply { timeout().timeout(req.timeoutMs, TimeUnit.MILLISECONDS) }.await()
        }
        if (!response.isSuccessful) throw ApiError.Http(HttpFailure(response.code, response.text, response.retryAfter))
        return response.text
    }

    enum class RefreshOutcome { REFRESHED, REJECTED, FAILED }

    private val rotations = ScopedRefresh<RefreshOutcome>(scope)

    /** Rotations coalesce only within the captured account/server identity. */
    suspend fun refreshSession(): RefreshOutcome = refreshSession(null)

    private suspend fun refreshSession(expectedVersion: Long?): RefreshOutcome {
        val context = requestContexts.captureAuthentication(expectedVersion)
        return rotations.run(context) { doRefresh(context) }
    }

    private suspend fun doRefresh(context: RequestContext.Scope): RefreshOutcome {
        val current = context.use { storedSession } ?: return RefreshOutcome.REJECTED
        val req = context.use {
            Prepared("POST", url("/api/auth/refresh"), null, 20_000, context).apply {
                headers["X-Client-ID"] = Config.clientId
                headers["Authorization"] = "Bearer ${current.token}"
                remoteCredential?.let { headers["X-Remote-Access-Credential"] = it }
            }
        }
        return try {
            val refreshed = AuthSession.from(json.decodeFromString<LoginResponse>(perform(req)))
            context.use {
                check(refreshed.accountId == current.accountId) { "Unexpected refresh account" }
                // Ordinary rotation preserves profile identity and in-flight peers.
                storedSession = refreshed
                refreshed.save()
            }
            RefreshOutcome.REFRESHED
        } catch (error: CancellationException) {
            throw error
        } catch (e: ApiError) {
            context.check()
            if (e.invalidatesSession || e.statusCode in setOf(403, 404)) RefreshOutcome.REJECTED else RefreshOutcome.FAILED
        } catch (e: Exception) {
            context.check()
            RefreshOutcome.FAILED
        }
    }

    fun signOutLocally() = requestContexts.serialized {
        if (session != null) {
            forgetSession()
            onSignedOut?.invoke()
        }
    }

    /** Drops the session and the profile unlocks bound to it. */
    fun forgetSession() = requestContexts.changeAuthentication(change = {
        storedSession = null
        true // Explicit logout also invalidates a login that has not returned yet.
    }, afterChange = {
        AuthSession.clear()
        unlocks.removeAll()
    })
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

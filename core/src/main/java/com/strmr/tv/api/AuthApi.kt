package com.strmr.tv.api

import com.strmr.tv.app.Config
import com.strmr.tv.auth.AuthSession
import java.io.IOException
import java.net.ConnectException
import java.net.NoRouteToHostException
import java.net.SocketTimeoutException
import java.net.UnknownHostException
import java.util.concurrent.TimeUnit
import javax.net.ssl.SSLException
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import okhttp3.HttpUrl
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody

// Connection, sign-in, profile and device-registration endpoints.

@Serializable private data class Health(val status: String? = null)

@Serializable private data class Verified(val valid: Boolean, val unlockToken: String? = null, val expiresAt: String? = null)

/** `GET /health` on a candidate server, without changing `baseUrl`. Throws a message for the server screen. */
suspend fun ApiClient.checkHealth(server: HttpUrl) {
    val (status, text) = probe(server, "/health", 8_000)
    val health = runCatching { json.decodeFromString<Health>(text) }.getOrNull()
    if (status != 200 || health?.status != "ok") {
        throw ApiError.NotReady(
            "${server.host} answered, but it doesn't look like a MediaStorm server (HTTP $status). Check the address and port.",
        )
    }
}

/** `GET /api/version` on a candidate server (a discovered one), without changing `baseUrl`. */
suspend fun ApiClient.checkVersion(server: HttpUrl): ServerVersion {
    val (status, text) = probe(server, "/api/version", 6_000)
    return runCatching { json.decodeFromString<ServerVersion>(text) }.getOrNull()?.takeIf { status == 200 }
        ?: throw ApiError.NotReady("${server.host} answered, but it doesn't look like a MediaStorm server.")
}

private suspend fun ApiClient.probe(server: HttpUrl, path: String, timeoutMs: Long): Pair<Int, String> {
    val url = server.newBuilder().encodedPath(server.encodedPath.trimEnd('/') + path).build()
    val request = Request.Builder().url(url).header("X-Client-ID", Config.clientId).build()
    return try {
        http.newCall(request).apply { timeout().timeout(timeoutMs, TimeUnit.MILLISECONDS) }.await()
            .let { it.code to it.text }
    } catch (e: IOException) {
        throw ApiError.NotReady(describe(e, server.host))
    }
}

/** A transport failure in words for the server screen. */
fun describe(e: IOException, host: String): String = when (e) {
    is SocketTimeoutException -> "$host didn't respond in time. Check that the server is running and reachable from this TV."
    is UnknownHostException -> "Couldn't find $host. Check the address."
    is ConnectException -> "$host refused the connection. Check the port and that the server is running."
    is NoRouteToHostException -> "This TV can't reach $host. Check its network connection."
    is SSLException -> "Couldn't make a secure connection to $host. Try http:// if the server has no certificate."
    else -> if (e.message?.contains("timeout", ignoreCase = true) == true) {
        "$host didn't respond in time. Check that the server is running and reachable from this TV."
    } else {
        e.message ?: "Couldn't reach $host."
    }
}

/** `GET /api/version` (public); null on older servers or errors. */
suspend fun ApiClient.serverVersion(): ServerVersion? =
    runCatching { send<ServerVersion>("GET", "/api/version", authorized = false, timeoutMs = 8_000) }.getOrNull()

/** `GET /api/branding` (public): the admin's uploaded logos, if any. */
suspend fun ApiClient.publicBranding(): PublicBranding? =
    runCatching { send<PublicBrandingResponse>("GET", "/api/branding", authorized = false, timeoutMs = 8_000) }
        .getOrNull()?.branding

suspend fun ApiClient.login(username: String, password: String): AuthSession {
    val body = buildJsonObject {
        put("username", username)
        put("password", password)
        put("rememberMe", true)
    }
    val text = sendRaw("POST", "/api/auth/login", body = body, authorized = false)
    val fresh = AuthSession.from(json.decodeFromString<LoginResponse>(text))
    // Unlocks belong to the previous session; the server no longer honours them.
    unlocks.removeAll()
    session = fresh
    return fresh
}

/** Revokes the session on the server (best effort) and forgets it locally. */
suspend fun ApiClient.logout() {
    token?.let { token ->
        val request = Request.Builder().url(url("/api/auth/logout"))
            .post(ByteArray(0).toRequestBody())
            .header("X-Client-ID", Config.clientId)
            .header("Authorization", "Bearer $token")
            .apply { remoteCredential?.let { header("X-Remote-Access-Credential", it) } }
            .build()
        runCatching { http.newCall(request).apply { timeout().timeout(5, TimeUnit.SECONDS) }.await() }
    }
    forgetSession()
}

suspend fun ApiClient.profiles(): List<Profile> = send("GET", "/api/users")

/**
 * `POST /api/users/{id}/pin/verify`; false for a wrong (or empty) PIN. Stores the unlock
 * token current servers return (older ones only answer `valid`).
 */
suspend fun ApiClient.verifyPin(pin: String, profileId: String): Boolean {
    return try {
        val result = send<Verified>(
            "POST", "/api/users/${segment(profileId)}/pin/verify",
            body = buildJsonObject { put("pin", pin) }, promptOnLock = false,
        )
        if (result.valid && !result.unlockToken.isNullOrEmpty()) unlocks.set(result.unlockToken, result.expiresAt, profileId)
        result.valid
    } catch (e: ApiError) {
        if (e.isWrongPin) false else throw e
    }
}

/** Public profile image (`GET /api/users/{id}/icon`) when the profile has one. */
fun ApiClient.iconUrl(profile: Profile): String? =
    if (profile.hasIcon == true) url("/api/users/${segment(profile.id)}/icon", mapOf("v" to (profile.iconUrl ?: ""))).toString() else null

suspend fun ApiClient.registerClient(profileId: String) {
    sendRaw("POST", "/api/clients/register", body = Config.registration(profileId))
}

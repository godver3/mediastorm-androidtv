package com.strmr.tv.auth

import com.strmr.tv.api.LoginResponse
import java.time.Instant
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

/** A signed-in account session. Kept in [SecureStore], never in plain preferences. */
@Serializable
data class AuthSession(
    val token: String,
    /** Epoch milliseconds; null when the server gave no expiry. */
    val expiresAt: Long? = null,
    val accountId: String,
    val username: String,
) {
    /**
     * Refresh this far ahead of expiry. Sessions last 30 days (90 with rememberMe), and each
     * refresh rotates the token, so only refresh near the end.
     */
    val needsRefresh: Boolean
        get() = expiresAt?.let { it - System.currentTimeMillis() < REFRESH_WINDOW_MS } ?: false

    fun save() = SecureStore.write(ACCOUNT, Json.encodeToString(this))

    companion object {
        private const val ACCOUNT = "session"
        private const val REFRESH_WINDOW_MS = 7L * 24 * 3600 * 1000

        fun from(login: LoginResponse) = AuthSession(
            token = login.token,
            expiresAt = login.expiresAt?.let { parseInstant(it) },
            accountId = login.accountId,
            username = login.username,
        )

        fun load(): AuthSession? =
            SecureStore.read(ACCOUNT)?.let { runCatching { Json.decodeFromString<AuthSession>(it) }.getOrNull() }

        fun clear() = SecureStore.delete(ACCOUNT)
    }
}

/** An ISO 8601 timestamp as epoch milliseconds; null if it doesn't parse. */
fun parseInstant(text: String): Long? = runCatching { Instant.parse(text).toEpochMilli() }.getOrNull()

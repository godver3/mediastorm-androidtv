package com.strmr.tv.api

import java.text.SimpleDateFormat
import java.util.Locale
import kotlin.math.ceil
import kotlin.math.max
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonPrimitive

/**
 * A non-2xx response. Current servers answer with `{"error": "...", "code": "..."}` (plus
 * extras such as `profileId`); older ones send plain text or only `error`.
 */
class HttpFailure(val status: Int, val body: String, retryAfterHeader: String? = null) {
    private val json = runCatching { Json.parseToJsonElement(body) as? JsonObject }.getOrNull()

    /** Stable machine code ([ApiError.Code]); null from older servers. */
    val code: String? = json.string("code")
    /** The human-readable `error` (or `message`) field. */
    val message: String? = json.string("message") ?: json.string("error")
    /** `profileId` from a `profile_locked` body. */
    val profileId: String? = json.string("profileId")
    /** Seconds from `Retry-After`, when sent. */
    val retryAfter: Double? = parseRetryAfter(retryAfterHeader)

    private companion object {
        fun JsonObject?.string(key: String) =
            runCatching { this?.get(key)?.jsonPrimitive?.contentOrNull }.getOrNull()?.ifEmpty { null }

        fun parseRetryAfter(header: String?): Double? {
            val text = header?.trim()?.ifEmpty { null } ?: return null
            text.toDoubleOrNull()?.let { return max(0.0, it) }
            val date = runCatching {
                SimpleDateFormat("EEE, dd MMM yyyy HH:mm:ss zzz", Locale.US).parse(text)
            }.getOrNull() ?: return null
            return max(0.0, (date.time - System.currentTimeMillis()) / 1000.0)
        }
    }
}

sealed class ApiError(message: String) : Exception(message) {
    class Http(val response: HttpFailure) : ApiError(describe(response))
    class NotReady(message: String) : ApiError(message)
    /** The session is gone and could not be refreshed; the app returns to sign-in. */
    class SignedOut : ApiError("Your session has ended. Sign in again.")

    /** Stable `code` values from the server's error envelope. */
    object Code {
        const val UNAUTHORIZED = "unauthorized"
        const val SESSION_EXPIRED = "session_expired"
        const val ACCOUNT_EXPIRED = "account_expired"
        const val PIN_INVALID = "pin_invalid"
        const val PIN_REQUIRED = "pin_required"
        const val PROFILE_LOCKED = "profile_locked"
        const val RATE_LIMITED = "rate_limited"
        const val DEFAULT_PASSWORD = "default_password"
    }

    val failure: HttpFailure? get() = (this as? Http)?.response
    val statusCode: Int? get() = failure?.status
    val code: String? get() = failure?.code

    /**
     * Whether this rejects the session itself, so a refresh (or sign-out) is due. Coded
     * answers decide; without a code (older servers) any 401 counts, except a wrong-PIN
     * reply, which those servers mark only by the word "PIN".
     */
    val invalidatesSession: Boolean
        get() {
            val f = failure ?: return false
            if (f.status != 401 && f.code == null) return false
            f.code?.let { return it in setOf(Code.UNAUTHORIZED, Code.SESSION_EXPIRED, Code.ACCOUNT_EXPIRED) }
            return !isWrongPin
        }

    /** Wrong or missing profile PIN; the session is fine. */
    val isWrongPin: Boolean
        get() {
            val f = failure ?: return false
            f.code?.let { return it == Code.PIN_INVALID || it == Code.PIN_REQUIRED }
            return f.status == 401 && f.body.contains("pin", ignoreCase = true)
        }

    val isProfileLocked: Boolean get() = code == Code.PROFILE_LOCKED

    val isRateLimited: Boolean
        get() = failure?.let { f -> f.code?.let { it == Code.RATE_LIMITED } ?: (f.status == 429) } ?: false

    /** The admin account still has the default password (HTTP 428). */
    val isDefaultPassword: Boolean
        get() = failure?.let { f -> f.code?.let { it == Code.DEFAULT_PASSWORD } ?: (f.status == 428) } ?: false

    companion object {
        private fun describe(f: HttpFailure): String = when {
            f.code == Code.RATE_LIMITED || (f.code == null && f.status == 429) -> rateLimitMessage(f.retryAfter)
            f.code == Code.PROFILE_LOCKED -> "This profile is locked. Enter its PIN to continue."
            else -> serverMessage(f) ?: "Server returned ${f.status}: ${f.body.take(200)}"
        }

        fun rateLimitMessage(retryAfter: Double?): String {
            if (retryAfter == null || retryAfter <= 0) return "Too many attempts. Wait a minute and try again."
            val seconds = ceil(retryAfter).toInt()
            if (seconds >= 90) return "Too many attempts. Try again in ${ceil(seconds / 60.0).toInt()} minutes."
            return "Too many attempts. Try again in $seconds seconds."
        }

        /** The envelope's message, or a short plain-text body. */
        private fun serverMessage(f: HttpFailure): String? {
            f.message?.let { return it }
            val trimmed = f.body.trim()
            return trimmed.takeUnless { it.isEmpty() || it.length > 160 || it.startsWith("<") || it.startsWith("{") }
        }
    }
}

package com.strmr.mobile.downloads

/**
 * Resuming a download with an HTTP Range request, pure for tests: the request header for the
 * bytes already on disk, and what the response means for the partial file.
 */
object RangeResume {
    sealed interface Decision {
        /** Keep the partial file and append from [offset]; [total] when the server says. */
        data class Append(val offset: Long, val total: Long?) : Decision
        /** The server sent the whole file (or the ranges don't line up): truncate and write from 0. */
        data class Restart(val total: Long?) : Decision
        /** The partial file already holds every byte. */
        data object Complete : Decision
        /** Asked for bytes past the end of a file of another size: drop the partial and ask again without a range. */
        data object Discard : Decision
        data class Fail(val code: Int) : Decision
    }

    /** `bytes=N-` for N bytes on disk; none for an empty file. */
    fun header(existing: Long): String? = if (existing > 0) "bytes=$existing-" else null

    /** `bytes 100-999/1000` → (100, 999, 1000); an unsatisfiable range (star before the slash) → (-1, -1, total); a star total is null. */
    fun parseContentRange(value: String?): Triple<Long, Long, Long?>? {
        val v = value?.trim()?.removePrefix("bytes")?.trim() ?: return null
        val (range, totalText) = v.split('/', limit = 2).takeIf { it.size == 2 } ?: return null
        val total = totalText.trim().takeIf { it != "*" }?.toLongOrNull()
        if (range.trim() == "*") return Triple(-1, -1, total)
        val (start, end) = range.split('-', limit = 2).takeIf { it.size == 2 } ?: return null
        return Triple(start.trim().toLongOrNull() ?: return null, end.trim().toLongOrNull() ?: return null, total)
    }

    /**
     * The response to a request for the bytes after [existing]. [contentLength] is the body's
     * length (-1 unknown).
     */
    fun decide(existing: Long, code: Int, contentRange: String?, contentLength: Long): Decision {
        val length = contentLength.takeIf { it >= 0 }
        return when (code) {
            206 -> {
                val range = parseContentRange(contentRange)
                when {
                    range == null -> if (existing == 0L) Decision.Restart(length) else Decision.Discard
                    range.first == existing -> Decision.Append(existing, range.third ?: length?.let { existing + it })
                    range.first == 0L -> Decision.Restart(range.third ?: length)
                    else -> Decision.Discard
                }
            }
            in 200..299 -> Decision.Restart(length)
            416 -> {
                val total = parseContentRange(contentRange)?.third
                if (existing > 0 && total == existing) Decision.Complete else Decision.Discard
            }
            else -> Decision.Fail(code)
        }
    }

    /** Whether an HTTP failure may pass by itself (worth an automatic retry). */
    fun retryable(code: Int): Boolean = code == 408 || code == 429 || code >= 500
}

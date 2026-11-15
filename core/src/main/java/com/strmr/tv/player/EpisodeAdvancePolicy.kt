package com.strmr.tv.player

import kotlin.math.ceil

/** One episode's automatic transition. A caller supplies a monotonic clock. */
internal class EpisodeAdvancePolicy {
    var autoplay = false
    var skipCredits = false
    var mediaEnded = false
        private set
    var paused = false
        private set
    var dismissed = false
        private set
    private var cancelled = false
    private var advanced = false
    private var remainingMs: Long? = null
    private var previousTick: Long? = null
    private var wasAtCredits = false
    private var blocked = false
    val seconds: Int? get() = remainingMs?.let { ceil(it / 1000.0).toInt() }

    fun end(positionMs: Long, durationMs: Long, failed: Boolean): Boolean {
        if (cancelled || advanced || !genuineEnd(positionMs, durationMs, failed)) return false
        mediaEnded = true
        return !dismissed
    }

    /** Returns true only once when the countdown has completed. */
    fun tick(nowMs: Long, atCredits: Boolean, playing: Boolean): Boolean {
        if (cancelled || advanced) return false
        if (wasAtCredits && !atCredits && !mediaEnded && remainingMs != null) blocked = true
        wasAtCredits = atCredits
        val eligible = autoplay && !blocked && !dismissed && (mediaEnded || (atCredits && skipCredits))
        if (!eligible) { remainingMs = null; previousTick = null; return false }
        val limit = if (mediaEnded) 5_000L else 7_000L
        val left = remainingMs
        if (left == null) { remainingMs = limit; previousTick = nowMs; return false }
        val elapsed = (nowMs - (previousTick ?: nowMs)).coerceAtLeast(0)
        previousTick = nowMs
        remainingMs = (minOf(left, limit) - if (playing && !paused) elapsed else 0).coerceAtLeast(0)
        return remainingMs == 0L && claimAdvance()
    }

    fun observePosition(positionMs: Long, durationMs: Long) {
        if (mediaEnded && durationMs > 0 && !genuineEnd(positionMs, durationMs, false)) {
            mediaEnded = false
            blocked = true
            remainingMs = null
            previousTick = null
        }
    }

    fun togglePause() { paused = !paused; previousTick = null }
    fun dismiss() { dismissed = true; blocked = true; remainingMs = null }
    fun cancel() { cancelled = true; remainingMs = null }
    fun claimAdvance(): Boolean {
        if (cancelled || advanced) return false
        advanced = true
        remainingMs = null
        return true
    }

    companion object {
        fun genuineEnd(positionMs: Long, durationMs: Long, failed: Boolean): Boolean =
            !failed && durationMs > 0 && positionMs >= (durationMs - 2_000).coerceAtLeast(0)
    }
}

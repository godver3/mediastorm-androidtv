package com.strmr.tv.player

import kotlin.math.abs

/**
 * When a movie or episode should move to another source on its own ([AutoSwitch]): it is meant
 * to be playing but the playhead has not moved for [STUCK_MS] ([SETTLE_MS] while it starts or
 * after a seek), or it has stalled [STALL_COUNT] times within [STALL_WINDOW_MS], however short
 * each stall. Sampled once a second; the same rules as the tvOS app's StallWatch.
 */
class StallWatch {
    sealed interface Trigger {
        val reason: String

        data class Stuck(val seconds: Int) : Trigger {
            override val reason get() = "stuck ${seconds}s"
        }

        data class RepeatedStalls(val count: Int) : Trigger {
            override val reason get() = "$count stalls in ${STALL_WINDOW_MS / 1000}s"
        }
    }

    private var lastPositionMs: Long? = null
    private var lastSampleAt: Long? = null
    private var stillSince: Long? = null
    private var stallCounted = false
    private val stalls = ArrayDeque<Long>()
    /** Starting up or landing a seek: until the playhead moves again. */
    private var settling = true

    /** Starts over, as at the start of playback (after a switch that found nothing to switch to). */
    fun reset() {
        lastPositionMs = null
        lastSampleAt = null
        stillSince = null
        stallCounted = false
        stalls.clear()
        settling = true
    }

    /** [active]: playback is meant to be moving (loading or playing; not paused or ended). */
    fun sample(positionMs: Long, active: Boolean, nowMs: Long): Trigger? {
        val last = lastPositionMs
        val lastAt = lastSampleAt
        lastPositionMs = positionMs
        lastSampleAt = nowMs
        if (!active) {
            stillSince = null
            stallCounted = false
            return null
        }
        if (last != null && lastAt != null) {
            val moved = positionMs - last
            if (abs(moved - (nowMs - lastAt)) > SEEK_JUMP_MS) {
                settling = true
                stillSince = null
                stallCounted = false
                return null
            }
            if (moved > 50) {
                settling = false
                stillSince = null
                stallCounted = false
                return null
            }
        }
        val since = stillSince ?: nowMs
        stillSince = since
        val still = nowMs - since
        if (!settling && !stallCounted && still >= STALL_THRESHOLD_MS) {
            stallCounted = true
            while (stalls.isNotEmpty() && nowMs - stalls.first() >= STALL_WINDOW_MS) stalls.removeFirst()
            stalls.addLast(nowMs)
            if (stalls.size >= STALL_COUNT) return Trigger.RepeatedStalls(stalls.size)
        }
        if (still >= if (settling) SETTLE_MS else STUCK_MS) return Trigger.Stuck((still / 1000).toInt())
        return null
    }

    companion object {
        const val STUCK_MS = 10_000L
        /** Before playback first moves, and after a seek, a slow start is allowed longer. */
        const val SETTLE_MS = 20_000L
        /** A still playhead this long counts as one stall. */
        const val STALL_THRESHOLD_MS = 2_000L
        const val STALL_COUNT = 3
        const val STALL_WINDOW_MS = 120_000L
        /** A jump bigger than this between samples is a seek, not playback. */
        const val SEEK_JUMP_MS = 4_000L
    }
}

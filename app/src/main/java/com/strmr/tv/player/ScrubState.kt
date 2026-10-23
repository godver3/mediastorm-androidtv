package com.strmr.tv.player

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * Left / Right scrubbing, as on the tvOS software route: presses move a target, not the
 * playback, and one seek is made once they pause, so a run of presses is one seek (one
 * re-buffer) and the preview leads it. Holding the key speeds the step up. Select commits at
 * once; Back cancels.
 */
class ScrubState(private val controller: PlayerController, private val scope: CoroutineScope) {
    /** Where the presses have moved the seek to; null when not scrubbing. */
    var target by mutableStateOf<Long?>(null)
        private set

    private var commitJob: Job? = null
    private var lastRepeatAt = 0L

    val active: Boolean get() = target != null

    /** One press (or key repeat, `repeatCount` > 0) in `direction` (-1 or 1). */
    fun step(direction: Int, repeatCount: Int) {
        if (controller.session.isLive) return
        val now = System.currentTimeMillis()
        // Key repeat arrives about every 50 ms; ten steps a second is fast enough to steer.
        if (repeatCount > 0 && now - lastRepeatAt < 100) return
        lastRepeatAt = now
        val step = when {
            repeatCount == 0 -> 10_000L
            repeatCount < 20 -> 10_000L
            repeatCount < 50 -> 30_000L
            repeatCount < 100 -> 60_000L
            else -> 120_000L
        }
        val duration = controller.duration.value
        val from = target ?: controller.player.currentPosition
        val end = if (duration > 0) duration - 1_000 else Long.MAX_VALUE
        target = (from + direction * step).coerceIn(0, end.coerceAtLeast(0))
        scheduleCommit(if (repeatCount > 0) 1_200 else 900)
    }

    /** Seeks to the target now. */
    fun commit() {
        commitJob?.cancel()
        target?.let { controller.seekTo(it) }
        target = null
    }

    fun cancel() {
        commitJob?.cancel()
        target = null
    }

    private fun scheduleCommit(afterMs: Long) {
        commitJob?.cancel()
        commitJob = scope.launch {
            delay(afterMs)
            commit()
        }
    }
}

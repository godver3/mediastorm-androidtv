package com.strmr.tv.player

import android.os.SystemClock
import androidx.media3.common.C
import androidx.media3.common.Player
import com.strmr.tv.app.AppLog

/**
 * Logs each rebuffer once playback has started: when it began, how much was buffered and, on a
 * live stream, how far behind the edge the playhead sat; then how long it lasted. A user report of
 * "buffering every few seconds" otherwise left only segment fetch times to infer it from.
 */
class StallLog(private val player: Player) : Player.Listener {
    private var started = false
    private var seeking = false
    private var stallStartedAt = 0L
    private var stalls = 0
    private var stalledMs = 0L

    override fun onPlaybackStateChanged(state: Int) {
        when (state) {
            Player.STATE_READY -> {
                if (stallStartedAt != 0L) {
                    val ms = SystemClock.elapsedRealtime() - stallStartedAt
                    stalls++
                    stalledMs += ms
                    stallStartedAt = 0L
                    AppLog.i("Player", "stall #$stalls ended after ${ms}ms${liveDetail()}")
                }
                started = true
                seeking = false
            }
            Player.STATE_BUFFERING -> if (started && !seeking && stallStartedAt == 0L) {
                stallStartedAt = SystemClock.elapsedRealtime()
                AppLog.i("Player", "stall #${stalls + 1} at ${player.currentPosition / 1000}s, buffered ${player.totalBufferedDuration}ms${liveDetail()}")
            }
            else -> Unit
        }
    }

    /** A seek's buffering is the viewer's own, not a stall. */
    override fun onPositionDiscontinuity(oldPosition: Player.PositionInfo, newPosition: Player.PositionInfo, reason: Int) {
        if (reason != Player.DISCONTINUITY_REASON_SEEK) return
        seeking = true
        stallStartedAt = 0L
    }

    /** One line for the whole playback, at release. */
    fun summary() {
        if (stalls > 0) AppLog.i("Player", "stalls: $stalls, ${stalledMs}ms in all")
    }

    private fun liveDetail(): String {
        if (!player.isCurrentMediaItemLive) return ""
        val offset = player.currentLiveOffset.takeIf { it != C.TIME_UNSET }?.let { " liveOffset=${it}ms" } ?: ""
        return "$offset speed=${player.playbackParameters.speed}"
    }
}

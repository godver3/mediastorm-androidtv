package com.strmr.tv.player

import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.common.Timeline

/**
 * Live timeshift on the server's HLS window (`timeshift=1`, [PlayerController.timeshift]): ExoPlayer
 * lists the whole window as the live item's seekable range and joins it at its default position,
 * a few target durations behind the newest segment (the server's `EXT-X-START`). "Live" is that
 * default position, not the window's very end, so a viewer who just tuned is at the edge.
 */
object Timeshift {
    /** Within this of the default position counts as live (the playlist refreshes in steps). */
    const val AT_LIVE_TOLERANCE_MS = 10_000L

    /** How far the playhead sits behind the live window's default position, in ms (0 at or past it). */
    fun behindLiveMs(player: Player): Long {
        val timeline = player.currentTimeline
        if (timeline.isEmpty || !player.isCurrentMediaItemLive) return 0
        val window = timeline.getWindow(player.currentMediaItemIndex, Timeline.Window())
        val edge = window.defaultPositionMs.takeIf { it != C.TIME_UNSET } ?: return 0
        return behindLiveMs(edge, player.currentPosition)
    }

    fun behindLiveMs(defaultPositionMs: Long, positionMs: Long): Long = (defaultPositionMs - positionMs).coerceAtLeast(0)

    fun isAtLive(behindMs: Long): Boolean = behindMs <= AT_LIVE_TOLERANCE_MS

    /**
     * The item for a live stream. A timeshift one plays at 1x always: ExoPlayer's live speed
     * control would otherwise creep a resumed pause back toward its target offset.
     */
    fun mediaItem(url: String, timeshift: Boolean): MediaItem {
        val builder = MediaItem.Builder().setUri(url)
        if (timeshift) {
            builder.setLiveConfiguration(
                MediaItem.LiveConfiguration.Builder().setMinPlaybackSpeed(1f).setMaxPlaybackSpeed(1f).build(),
            )
        }
        return builder.build()
    }
}

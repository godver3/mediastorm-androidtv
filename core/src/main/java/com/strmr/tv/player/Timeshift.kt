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
     * Slowest a timeshift channel plays while it builds its lead on the live edge (ExoPlayer's own
     * default floor; the audio is time-stretched, so the pitch holds).
     */
    const val MIN_LIVE_SPEED = 0.97f

    /**
     * The item for a live stream. A timeshift one never plays faster than 1x: ExoPlayer's live
     * speed control would otherwise creep a resumed pause back toward its target offset. It may
     * play slower, down to [MIN_LIVE_SPEED], to fall back to that target. A tune joins a fresh
     * server session whose window is only its first segment, so the player starts at the very edge.
     * Held at exactly 1x it stayed there with nothing buffered ahead: every segment arrived just in
     * time over a slow route (iroh relay), and playback stalled every segment, 3 to 4 s apart.
     */
    fun mediaItem(url: String, timeshift: Boolean): MediaItem {
        val builder = MediaItem.Builder().setUri(url)
        if (timeshift) builder.setLiveConfiguration(liveConfiguration)
        return builder.build()
    }

    /** A timeshift item's live configuration: [MIN_LIVE_SPEED] to 1x, ExoPlayer's target offset. */
    val liveConfiguration: MediaItem.LiveConfiguration
        get() = MediaItem.LiveConfiguration.Builder().setMinPlaybackSpeed(MIN_LIVE_SPEED).setMaxPlaybackSpeed(1f).build()
}

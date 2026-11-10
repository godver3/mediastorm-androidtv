package com.strmr.tv.player

import androidx.annotation.OptIn
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import com.strmr.tv.api.ApiClient
import com.strmr.tv.app.AppLog
import com.strmr.tv.live.LiveStream
import com.strmr.tv.live.keepLiveSessionAlive
import com.strmr.tv.live.stopLiveSession
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/**
 * A recording that is still recording, over the server's growing HLS session (an EVENT
 * playlist that lists every segment from the start and gets ENDLIST about 20 s after the
 * recording's last write). The player reloads the playlist itself, so the timeline grows
 * and seeks are ordinary seeks. This keeps the session alive (every 20 s, with the
 * position, as the live TV session is) and stops it on close. A playlist that ended while
 * the server still reports the recording as growing (a source stall over 20 s) is opened
 * again in a new session at the position.
 */
@OptIn(UnstableApi::class)
class RecordingHls(
    private val player: ExoPlayer,
    private val scope: CoroutineScope,
    private var sessionId: String?,
    private val restart: suspend () -> LiveStream,
) {
    private var restarting = false

    /**
     * Starts at 0, not the live edge, and at normal speed: Media3 would otherwise speed
     * playback up (to 1.03x) to close in on the edge of what looks like a live stream.
     */
    fun mediaItem(url: String): MediaItem = MediaItem.Builder()
        .setUri(url)
        .setLiveConfiguration(MediaItem.LiveConfiguration.Builder().setMinPlaybackSpeed(1f).setMaxPlaybackSpeed(1f).build())
        .build()

    private val keepalive = scope.launch {
        while (isActive) {
            delay(KEEPALIVE_MS)
            ping()
        }
    }

    /** Keepalive with the position; whether the server still reports the recording as growing (null when unknown). */
    private suspend fun ping(): Boolean? {
        val id = sessionId ?: return null
        return ApiClient.keepLiveSessionAlive(
            id, positionSeconds = player.currentPosition / 1000.0, paused = !player.playWhenReady,
            buffering = player.playbackState == Player.STATE_BUFFERING,
        )
    }

    /** The playlist ended: the real end unless the server says the recording is still growing. */
    fun ended(onFinished: () -> Unit) {
        if (restarting) return
        restarting = true
        scope.launch {
            try {
                if (ping() != true) {
                    AppLog.i("Recordings", "growing HLS: ended at ${player.currentPosition / 1000.0}s")
                    onFinished()
                    return@launch
                }
                val at = player.currentPosition
                AppLog.w("Recordings", "growing HLS: playlist ended early at ${at / 1000.0}s, still recording; new session")
                val next = restart()
                sessionId?.let { old -> MainScope().launch { ApiClient.stopLiveSession(old) } }
                sessionId = next.sessionId
                player.setMediaItem(mediaItem(next.url), at)
                player.playWhenReady = true
                player.prepare()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                AppLog.w("Recordings", "growing HLS: new session failed: ${e.message}")
                onFinished()
            } finally {
                restarting = false
            }
        }
    }

    fun release() {
        keepalive.cancel()
        sessionId?.let { id -> MainScope().launch { ApiClient.stopLiveSession(id) } }
    }

    private companion object {
        const val KEEPALIVE_MS = 20_000L
    }
}

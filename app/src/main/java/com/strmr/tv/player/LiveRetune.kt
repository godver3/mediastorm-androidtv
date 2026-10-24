package com.strmr.tv.player

import android.os.SystemClock
import androidx.annotation.OptIn
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import com.strmr.tv.api.ApiClient
import com.strmr.tv.api.ApiError
import com.strmr.tv.app.AppLog
import com.strmr.tv.live.LiveStream
import com.strmr.tv.live.keepLiveSessionAlive
import com.strmr.tv.live.stopLiveSession
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * A live channel's server side while it plays, as the tvOS live player keeps it: its HLS
 * session (when the server opened one rather than a direct stream) kept alive and stopped on
 * close, and the channel tuned again when its source drops (an end of stream, or a source or
 * network error) within [LiveRetunePolicy]'s budget: the old session stopped, a fresh
 * `/api/live/hls/start` ([tune]), and the player kept open with [reconnecting] set until the
 * new stream plays. A spent budget, or a retune the server refuses, ends playback with
 * [giveUp]'s message.
 */
@OptIn(UnstableApi::class)
class LiveRetune(
    private val player: ExoPlayer,
    private val scope: CoroutineScope,
    private var sessionId: String?,
    private val tune: suspend () -> LiveStream,
    private val giveUp: (String) -> Unit,
) {
    /** A retune is under way: the player covers the channel with "Reconnecting…". */
    val reconnecting = MutableStateFlow(false)
    private val policy = LiveRetunePolicy().apply { tuned(now()) }
    private var retuneJob: Job? = null
    private var released = false

    private val keepalive = scope.launch {
        while (isActive) {
            delay(KEEPALIVE_MS)
            sessionId?.let { ApiClient.keepLiveSessionAlive(it) }
        }
    }

    /** The stream is playing. */
    fun playing() {
        policy.playing(now())
        reconnecting.value = false
    }

    /** The stream ended, which a live channel doesn't: its source dropped. */
    fun ended() {
        if (!sourceLost("end of stream")) giveUp(NEVER_PLAYED)
    }

    /** A playback error; true when it was the source's and this took it over. */
    fun failed(e: PlaybackException): Boolean = LiveRetunePolicy.isSourceError(e.errorCode) && sourceLost(e.errorCodeName)

    /** Tunes again or gives up; false when the channel never played, so its failure stands. */
    private fun sourceLost(reason: String): Boolean {
        if (retuneJob?.isActive == true) return true
        when (val decision = policy.sourceLost(now())) {
            is LiveRetunePolicy.Decision.Retune -> retune(decision, reason)
            LiveRetunePolicy.Decision.Exhausted -> {
                AppLog.e("Live", "retune budget spent ($reason)")
                giveUp(EXHAUSTED)
            }
            LiveRetunePolicy.Decision.NeverPlayed -> return false
        }
        return true
    }

    private fun retune(decision: LiveRetunePolicy.Decision.Retune, reason: String) {
        val wait = if (decision.delayMs > 0) " in ${decision.delayMs / 1000}s" else ""
        AppLog.w("Live", "source dropped ($reason); retune ${decision.attempt}$wait")
        reconnecting.value = true
        player.stop()
        endSession()
        retuneJob = scope.launch {
            delay(decision.delayMs)
            policy.tuned(now())
            try {
                val next = open() ?: return@launch
                AppLog.i("Live", "retune ${decision.attempt}: tuned (${if (next.sessionId == null) "direct" else "hls session"})")
                player.setMediaItem(MediaItem.fromUri(next.url))
                player.playWhenReady = true
                player.prepare()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                AppLog.e("Live", "retune ${decision.attempt} failed: ${e.message}")
                // The server's own reason; a network failure's message is a socket's, not the viewer's.
                if (!released) giveUp((e as? ApiError)?.message ?: RETUNE_FAILED)
            }
        }
    }

    /**
     * The new stream, its session taken over at once. One the server opens as the player
     * closes is stopped, not left holding the source.
     */
    private suspend fun open(): LiveStream? = withContext(NonCancellable) {
        val next = tune()
        if (released) {
            next.sessionId?.let { ApiClient.stopLiveSession(it) }
            null
        } else next.also { sessionId = it.sessionId }
    }

    /** Stops the current HLS session (a direct stream has none). */
    private fun endSession() {
        val id = sessionId ?: return
        sessionId = null
        MainScope().launch { ApiClient.stopLiveSession(id) }
    }

    fun release() {
        released = true
        retuneJob?.cancel()
        keepalive.cancel()
        endSession()
    }

    private fun now() = SystemClock.elapsedRealtime()

    private companion object {
        const val KEEPALIVE_MS = 30_000L
        const val EXHAUSTED = "The channel keeps dropping out. Try it again in a moment."
        const val NEVER_PLAYED = "The channel stopped before it started playing. Try it again in a moment."
        const val RETUNE_FAILED = "The channel dropped out and couldn't be tuned again. Try it again in a moment."
    }
}

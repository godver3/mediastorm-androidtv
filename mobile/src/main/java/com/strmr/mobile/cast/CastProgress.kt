package com.strmr.mobile.cast

import com.strmr.tv.api.ApiClient
import com.strmr.tv.api.reportProgress
import com.strmr.tv.app.AppLog
import com.strmr.tv.live.keepLiveSessionAlive
import com.strmr.tv.live.stopLiveSession
import com.strmr.tv.player.PlaybackCoordinator
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/*
 * The running cast: the receiver polled every second, watch progress every ten (the local
 * player's cadence), the server's HLS session kept warm while a paused receiver asks for
 * nothing, and the end of the media told apart from a stop.
 */

/** Polls in a row without an answer before the receiver is presumed gone (TV off, input switched). */
private const val FAILURE_LIMIT = 8

internal fun CastController.startPolling() {
    pollJob?.cancel()
    pollJob = scope.launch {
        var failures = 0
        var ticks = 0
        var sawPlayback = false
        while (isActive) {
            delay(1_000)
            val transport = transport ?: return@launch
            ticks += 1
            val state = transport.status()
            if (state == null) {
                failures += 1
                if (failures >= FAILURE_LIMIT) {
                    finish("receiver stopped answering", stopReceiver = false)
                    return@launch
                }
                continue
            }
            failures = 0
            if (this@startPolling.transport !== transport) return@launch
            apply(state)
            if (state.phase == CastPlaybackState.Phase.PLAYING || state.phase == CastPlaybackState.Phase.PAUSED) sawPlayback = true
            if (state.phase == CastPlaybackState.Phase.FINISHED || (sawPlayback && state.phase == CastPlaybackState.Phase.IDLE)) {
                // A DLNA renderer says only STOPPED: near the end it finished, else the TV stopped it.
                val ended = state.phase == CastPlaybackState.Phase.FINISHED || isNearEnd()
                finish(if (ended) "media finished" else "receiver went idle", stopReceiver = false, ended = ended)
                return@launch
            }
            if (ticks % 10 == 0) reportProgress(ended = false)
            // A live session every ten seconds, the live player's cadence.
            if (ticks % (if (session?.isLive == true) 10 else 20) == 0) keepAlive()
        }
    }
}

private fun CastController.apply(state: CastPlaybackState) {
    val current = session ?: return
    // A receiver that doesn't know the length (a transcode) keeps the one the server gave; a live
    // channel has none (a receiver may report the seekable window as one).
    val duration = if (state.duration > 0 && !current.isLive) state.duration else current.state.duration
    session = current.copy(
        state = current.state.copy(
            phase = state.phase, position = state.position, duration = duration, volume = state.volume ?: current.state.volume,
        ),
    )
}

private fun CastController.isNearEnd(): Boolean {
    val current = session ?: return false
    return CastEnd.isNearEnd(current.isLive, current.state.position, current.state.duration)
}

/** The rule for a receiver going idle: within a minute of the end, or past 92 %, counts as finished. */
internal object CastEnd {
    fun isNearEnd(live: Boolean, position: Double, duration: Double): Boolean {
        if (live || duration <= 0) return false
        return position >= duration - 60 || position / duration >= 0.92
    }
}

/** The `/history/progress` heartbeat the local player sends, for what plays on the TV. */
internal fun CastController.reportProgress(ended: Boolean) {
    val (request, path) = playback ?: return
    val state = session?.state ?: return
    val duration = state.duration
    val position = if (ended) maxOf(state.position, duration) else state.position
    if (duration <= 0 || position <= 0) return
    val body = request.progressReport(position, duration, paused = state.phase == CastPlaybackState.Phase.PAUSED, ended = ended,
                                      sourcePath = path)
    scope.launch {
        withContext(NonCancellable) {
            runCatching { ApiClient.reportProgress(body) }.onFailure { AppLog.w(CastController.TAG, "progress failed: ${it.message}") }
        }
    }
}

/** The server reaps an HLS session that goes quiet for a minute, and a paused receiver fetches nothing. */
private fun CastController.keepAlive() {
    val id = hlsSessionId ?: return
    val paused = session?.state?.phase == CastPlaybackState.Phase.PAUSED
    scope.launch { ApiClient.keepLiveSessionAlive(id, paused = paused) }
}

/** Ends the cast: progress saved, the receiver stopped (when asked), the server session let go. */
internal suspend fun CastController.finish(reason: String, stopReceiver: Boolean, ended: Boolean = false) {
    pollJob?.cancel()
    pollJob = null
    // Nothing running: a failed attempt stays on the remote until it is dismissed.
    val transport = transport ?: return
    AppLog.i(CastController.TAG, "cast to ${transport.device.name} ended: $reason")
    reportProgress(ended)
    this.transport = null
    val sessionId = hlsSessionId
    hlsSessionId = null
    playback = null
    session = null
    remoteVisible = false
    withContext(NonCancellable) {
        if (stopReceiver) transport.stop()
        sessionId?.let { ApiClient.stopLiveSession(it) }
    }
    PlaybackCoordinator.sessionsEnded.value += 1
}

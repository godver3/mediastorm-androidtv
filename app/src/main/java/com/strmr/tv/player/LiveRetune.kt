package com.strmr.tv.player

import android.os.SystemClock
import androidx.annotation.OptIn
import androidx.media3.common.PlaybackException
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import com.strmr.tv.api.ApiClient
import com.strmr.tv.api.ApiError
import com.strmr.tv.app.AppLog
import com.strmr.tv.live.LiveChannel
import com.strmr.tv.live.LiveHlsPreference
import com.strmr.tv.live.LiveStream
import com.strmr.tv.live.keepLiveSessionAlive
import com.strmr.tv.live.liveStremioStreams
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
 * [giveUp]'s message. A direct stream that fails before it plays is opened once more as HLS
 * ([LiveHlsFallback]), and [channel]'s source remembered as needing it once that has played
 * [HLS_PROOF_MS] with no stall; a source whose direct stream has played here never falls back.
 *
 * Stremio failover, as Expo's live recovery: a Stremio addon channel lists several streams of
 * the same broadcast, so when the one playing is lost for good (an error nothing above fixes,
 * a spent budget, a retune the server refuses, or an end before it ever played) the channel is
 * tuned on the next stream it hasn't tried, with "Trying another stream source…" on the cover
 * ([note]). Only once every stream has been tried does playback end. The playable streams come
 * from the HLS session's `stremioStreamIndexes`, or for a direct stream from the addon's list
 * (`GET /api/live/stremio/streams`), fetched on the first failure.
 */
@OptIn(UnstableApi::class)
class LiveRetune(
    private val player: ExoPlayer,
    private val scope: CoroutineScope,
    private var sessionId: String?,
    channel: LiveChannel?,
    /** Opens the channel; `true` asks for an HLS session (`format=hls`), an index that Stremio stream. */
    private val tune: suspend (forceHls: Boolean, stremioStreamIndex: Int?) -> LiveStream,
    private val giveUp: (String) -> Unit,
    first: LiveStream? = null,
    /** Each fresh stream a retune or failover opens (it may keep a timeshift window, or not). */
    private val tuned: (LiveStream) -> Unit = {},
) {
    /** The channel playing; its `stremioStreamIndex` moves on under Stremio failover. */
    var channel: LiveChannel? = channel
        private set
    /** A retune is under way: the player covers the channel with "Reconnecting…". */
    val reconnecting = MutableStateFlow(false)
    /** What the cover says instead of "Reconnecting…" (Stremio failover). */
    val note = MutableStateFlow<String?>(null)
    private var policy = LiveRetunePolicy().apply { tuned(now()) }
    private val stremio = StremioFailover().apply { first?.let { tuned(it.stremioStreamIndex ?: channel?.stremioStreamIndex, it.stremioStreamIndexes) } }
    private var failoverJob: Job? = null
    private var retuneJob: Job? = null
    private var released = false
    private val hls = LiveHlsFallback(direct = sessionId == null)
    private var hlsProof: Job? = null

    init {
        // Whether direct has played for this source before, which rules the fallback out.
        if (hls.forcesHls.not() && sessionId == null && channel != null) scope.launch {
            LiveHlsPreference.scope(channel, wait = true)?.let { hls.directWorked = LiveHlsPreference.directWorked(it) }
        }
    }

    private val keepalive = scope.launch {
        while (isActive) {
            delay(KEEPALIVE_MS)
            sessionId?.let { ApiClient.keepLiveSessionAlive(it) }
        }
    }

    /**
     * The channel is off the air here while the provider's archive plays in its place
     * ([CatchupJoin]): its session stopped (a provider may allow one connection), no retunes.
     */
    var parked = false
        private set

    /** The stream is playing. */
    fun playing() {
        if (parked) return
        policy.playing(now())
        if (hls.playing()) rememberDirectWorked()
        reconnecting.value = false
        note.value = null
        if (hls.mode == LiveHlsFallback.Mode.FALLBACK && hlsProof?.isActive != true) {
            hlsProof = scope.launch {
                delay(HLS_PROOF_MS)
                if (player.isPlaying && hls.confirm()) rememberHls()
            }
        }
    }

    /** Stops the channel's session for the archive; [unpark] tunes it again. */
    fun park() {
        if (released || parked) return
        parked = true
        hlsProof?.cancel()
        retuneJob?.cancel()
        failoverJob?.cancel()
        reconnecting.value = false
        note.value = null
        endSession()
    }

    /** Back to live from the archive: the channel tuned afresh, with a full retune budget. */
    fun unpark() {
        if (released || !parked) return
        parked = false
        policy = LiveRetunePolicy()
        restart("back to live", 0)
        note.value = BACK_TO_LIVE
    }

    /** The player is buffering: a stall while the fallback is on probation. */
    fun buffering() {
        if (hlsProof?.isActive != true) return
        hls.stalled()
        AppLog.i("Live", "hls fallback stalled while on probation; not remembered for the source")
        hlsProof?.cancel()
    }

    /** The stream ended, which a live channel doesn't: its source dropped. */
    fun ended() {
        if (parked) return
        if (!fallBackToHls("end of stream") && !sourceLost("end of stream")) failOverOrGiveUp("end of stream", NEVER_PLAYED)
    }

    /**
     * A playback error; true when this took it over: any error of a direct stream that never
     * played (retried as HLS), else a source error (retuned).
     */
    fun failed(e: PlaybackException): Boolean = !parked && (fallBackToHls(e.errorCodeName) || LiveRetunePolicy.isSourceError(e.errorCode) && sourceLost(e.errorCodeName) ||
            failOver(e.errorCodeName, e.message ?: e.errorCodeName))

    /** A direct stream that never played: tunes once more as HLS. */
    private fun fallBackToHls(reason: String): Boolean {
        if (released || retuneJob?.isActive == true || !hls.failed()) return false
        AppLog.w("Live", "direct stream failed before playing ($reason); retrying as hls")
        restart("hls fallback", HLS_FALLBACK_DELAY_MS)
        return true
    }

    private fun rememberDirectWorked() = scope.launch {
        val source = channel?.let { LiveHlsPreference.scope(it, wait = true) } ?: return@launch
        LiveHlsPreference.rememberDirectWorked(source)
        AppLog.i("Live", "direct played for source ${source.sourceId}; it won't fall back to hls")
    }

    private fun rememberHls() = scope.launch {
        val channel = channel ?: return@launch
        val source = LiveHlsPreference.scope(channel, wait = true)
        if (source == null) {
            AppLog.i("Live", "hls fallback playing, but the source can't be remembered")
            return@launch
        }
        LiveHlsPreference.remember(source)
        AppLog.i("Live", "hls remembered for source ${source.sourceId}")
    }

    /** Tunes again or gives up; false when the channel never played, so its failure stands. */
    private fun sourceLost(reason: String): Boolean {
        if (retuneJob?.isActive == true) return true
        when (val decision = policy.sourceLost(now())) {
            is LiveRetunePolicy.Decision.Retune -> retune(decision, reason)
            LiveRetunePolicy.Decision.Exhausted -> {
                AppLog.e("Live", "retune budget spent ($reason)")
                failOverOrGiveUp(reason, EXHAUSTED)
            }
            LiveRetunePolicy.Decision.NeverPlayed -> return false
        }
        return true
    }

    private fun retune(decision: LiveRetunePolicy.Decision.Retune, reason: String) {
        val wait = if (decision.delayMs > 0) " in ${decision.delayMs / 1000}s" else ""
        AppLog.w("Live", "source dropped ($reason); retune ${decision.attempt}$wait")
        restart("retune ${decision.attempt}", decision.delayMs)
    }

    /** Stops the stream and its session, and tunes the channel again after [delayMs]. */
    private fun restart(label: String, delayMs: Long) {
        reconnecting.value = true
        hlsProof?.cancel()
        player.stop()
        endSession()
        retuneJob = scope.launch {
            delay(delayMs)
            policy.tuned(now())
            try {
                val next = open() ?: return@launch
                hls.tuned(direct = next.sessionId == null)
                if (channel?.hasStremioSources == true) {
                    stremio.tuned(next.stremioStreamIndex ?: channel?.stremioStreamIndex, next.stremioStreamIndexes)
                }
                AppLog.i("Live", "$label: tuned (${if (next.sessionId == null) "direct" else "hls session"}" +
                    "${if (next.timeshift) ", timeshift ${next.timeshiftWindowSeconds / 60} min" else ""})")
                tuned(next)
                player.setMediaItem(Timeshift.mediaItem(next.url, next.timeshift))
                player.playWhenReady = true
                player.prepare()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                AppLog.e("Live", "$label failed: ${e.message}")
                // The server's own reason; a network failure's message is a socket's, not the viewer's.
                if (!released) failOverOrGiveUp("$label failed", (e as? ApiError)?.message ?: RETUNE_FAILED)
            }
        }
    }

    /**
     * The new stream, its session taken over at once. One the server opens as the player
     * closes is stopped, not left holding the source.
     */
    private suspend fun open(): LiveStream? = withContext(NonCancellable) {
        val next = tune(hls.forcesHls, channel?.stremioStreamIndex)
        if (released) {
            next.sessionId?.let { ApiClient.stopLiveSession(it) }
            null
        } else next.also { sessionId = it.sessionId }
    }

    private fun failOverOrGiveUp(reason: String, message: String) {
        if (!failOver(reason, message)) giveUp(message)
    }

    /**
     * Stremio failover: tunes the channel's next untried stream, or gives up with [message]
     * once every one has been tried. False when the channel isn't a Stremio one.
     */
    private fun failOver(reason: String, message: String): Boolean {
        val current = channel?.takeIf { it.hasStremioSources } ?: return false
        if (released) return true
        if (failoverJob?.isActive == true) return true
        failoverJob = scope.launch {
            if (stremio.available == null) {
                try {
                    stremio.learn(ApiClient.liveStremioStreams(current).map { it.index })
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    AppLog.e("Live", "stremio failover: stream list failed (${e.message})")
                }
            }
            if (released) return@launch
            val index = stremio.next()
            if (index == null) {
                AppLog.w("Live", "stremio failover: no untried streams left ($reason)")
                giveUp(message)
                return@launch
            }
            AppLog.w("Live", "stremio failover: stream $index after $reason")
            channel = current.copy(stremioStreamIndex = index)
            // A new stream gets the full retune budget.
            policy = LiveRetunePolicy()
            note.value = TRYING_ANOTHER
            restart("stremio stream $index", HLS_FALLBACK_DELAY_MS)
        }
        return true
    }

    /** Stops the current HLS session (a direct stream has none). */
    private fun endSession() {
        val id = sessionId ?: return
        sessionId = null
        MainScope().launch { ApiClient.stopLiveSession(id) }
    }

    /**
     * Gives up the channel's HLS session to another owner (the multiscreen's first screen):
     * no more keepalives or retunes, and [release] won't stop it. Null when the stream is direct
     * or between sessions mid-retune.
     */
    fun handOver(): String? {
        val id = sessionId?.takeIf { retuneJob?.isActive != true }
        released = true
        retuneJob?.cancel()
        failoverJob?.cancel()
        keepalive.cancel()
        if (id != null) sessionId = null
        return id
    }

    fun release() {
        released = true
        hlsProof?.cancel()
        retuneJob?.cancel()
        failoverJob?.cancel()
        keepalive.cancel()
        endSession()
    }

    private fun now() = SystemClock.elapsedRealtime()

    private companion object {
        const val KEEPALIVE_MS = 30_000L
        /** Time for the direct stream's provider connection to close before the session opens another. */
        const val HLS_FALLBACK_DELAY_MS = 300L
        /** How long the fallback plays with no stall before the source is remembered. */
        const val HLS_PROOF_MS = 30_000L
        const val EXHAUSTED = "The channel keeps dropping out. Try it again in a moment."
        const val NEVER_PLAYED = "The channel stopped before it started playing. Try it again in a moment."
        const val BACK_TO_LIVE = "Back to live…"
        const val TRYING_ANOTHER = "Trying another stream source…"
        const val RETUNE_FAILED = "The channel dropped out and couldn't be tuned again. Try it again in a moment."
    }
}

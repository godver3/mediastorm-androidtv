package com.strmr.mobile.cast

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.strmr.tv.app.AppLog
import com.strmr.tv.player.PlaybackRequest
import kotlinx.coroutines.Job
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

/** The cast on the remote and the now-playing bar: what plays, where, and how it is going. */
data class ActiveCast(
    val device: CastDevice,
    val title: String,
    val subtitle: String?,
    val artworkUrl: String?,
    /** A live channel: no timeline, no seeking, no progress. */
    val isLive: Boolean = false,
    val phase: Phase = Phase.Connecting,
    val state: CastPlaybackState = CastPlaybackState(),
    val canSeek: Boolean = true,
) {
    sealed interface Phase {
        data object Connecting : Phase
        data object Active : Phase
        data class Failed(val message: String) : Phase
    }
}

/**
 * The phone's one cast (the iPhone app's CastController): device discovery for the picker
 * (Chromecasts through the Cast SDK, DLNA renderers over SSDP), starting a cast from a resolved
 * stream or a live channel, the receiver's state polled every second for the remote, and watch
 * progress reported to the server as the local player does, so Continue Watching follows what
 * plays on the TV. Main thread only; its state is Compose state.
 *
 * The rest lives beside it by concern: CastStart (starting), CastMediaBuilder (what each kind
 * of receiver is handed), CastProgress (polling, progress, the end) and CastHandoff (details
 * page and player).
 */
object CastController {
    internal const val TAG = "Cast"
    internal val scope = MainScope()

    val chromecasts: StateFlow<List<CastDevice>> get() = GoogleCastService.devices
    var renderers by mutableStateOf(emptyList<CastDevice>())
        private set
    var isSearching by mutableStateOf(false)
        private set
    var session by mutableStateOf<ActiveCast?>(null)
        internal set
    /** The remote sheet is up (opened by a start, the now-playing bar or the picker). */
    var remoteVisible by mutableStateOf(false)

    internal val dlna = mutableMapOf<String, DlnaRenderer>()
    internal var transport: CastTransport? = null
    /** The title watch progress is reported against, and its stream path; null for a live channel. */
    internal var playback: Pair<PlaybackRequest, String>? = null
    /** The server HLS session the receiver reads: kept alive, stopped with the cast. */
    internal var hlsSessionId: String? = null
    internal var pollJob: Job? = null
    internal var startJob: Job? = null
    private var searchJob: Job? = null

    init {
        GoogleCastService.onSessionEnded = { ended ->
            val current = transport
            if (current is GoogleCastTransport && current.session === ended && session?.phase == ActiveCast.Phase.Active) {
                scope.launch { finish("receiver ended the session", stopReceiver = false) }
            }
        }
    }

    // Discovery

    /** The picker is up: Chromecasts stream in from the SDK, DLNA renderers after one SSDP window. */
    fun startDiscovery() {
        scope.launch { GoogleCastService.startDiscovery() }
        refreshRenderers()
    }

    fun stopDiscovery() = GoogleCastService.stopDiscovery()

    fun refreshRenderers() {
        if (searchJob != null) return
        isSearching = true
        searchJob = scope.launch {
            try {
                val found = DlnaDiscovery.discover()
                found.forEach { dlna[it.device.id] = it }
                // Keep a renderer added by hand that didn't answer this round's multicast.
                val ids = found.map { it.device.id }.toSet()
                renderers = (found.map { it.device } + renderers.filter { it.id !in ids && dlna.containsKey(it.id) })
                    .sortedBy { it.name.lowercase() }
            } finally {
                isSearching = false
                searchJob = null
            }
        }
    }

    /** A renderer typed in by address. */
    suspend fun addRenderer(host: String): CastDevice {
        val renderer = DlnaDiscovery.probe(host)
        dlna[renderer.device.id] = renderer
        renderers = (listOf(renderer.device) + renderers.filter { it.id != renderer.device.id }).sortedBy { it.name.lowercase() }
        return renderer.device
    }

    internal fun dlnaTransport(device: CastDevice): DlnaTransport =
        DlnaTransport(dlna[device.id] ?: throw CastException("${device.name} is no longer on the network."))

    // Controls

    fun togglePlayPause() {
        val transport = transport ?: return
        val current = session ?: return
        val playing = current.state.isPlaying
        session = current.copy(state = current.state.copy(phase = if (playing) CastPlaybackState.Phase.PAUSED else CastPlaybackState.Phase.PLAYING))
        scope.launch {
            runCatching { if (playing) transport.pause() else transport.play() }
                .onFailure { AppLog.w(TAG, "toggle failed: ${it.message}") }
            reportProgress(ended = false)
        }
    }

    fun seek(seconds: Double) {
        val transport = transport ?: return
        val current = session ?: return
        if (!current.canSeek || current.isLive) return
        val duration = current.state.duration
        val target = if (duration > 0) seconds.coerceIn(0.0, (duration - 1).coerceAtLeast(0.0)) else seconds.coerceAtLeast(0.0)
        session = current.copy(state = current.state.copy(position = target))
        scope.launch { runCatching { transport.seek(target) }.onFailure { AppLog.w(TAG, "seek failed: ${it.message}") } }
    }

    fun skip(deltaSeconds: Double) = seek((session?.state?.position ?: 0.0) + deltaSeconds)

    fun setVolume(volume: Float) {
        val transport = transport ?: return
        session?.let { session = it.copy(state = it.state.copy(volume = volume)) }
        scope.launch { runCatching { transport.setVolume(volume) } }
    }

    /** Stop Casting: the receiver stops, progress is saved, the server's session is let go. */
    fun stop() {
        scope.launch { finish("stopped from the phone", stopReceiver = true) }
    }

    /** Clears a failed attempt off the remote. */
    fun dismissFailure() {
        if (session?.phase is ActiveCast.Phase.Failed) session = null
    }
}

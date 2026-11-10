package com.strmr.mobile.cast

import com.strmr.tv.app.AppLog
import kotlin.math.roundToInt
import kotlinx.coroutines.delay

/**
 * A DLNA renderer driven over UPnP AVTransport (SetAVTransportURI, Play, Pause, Seek, Stop,
 * GetTransportInfo / GetPositionInfo polling) and RenderingControl (volume), with the Expo
 * bridge's handling of renderers that wedge or answer slowly.
 */
class DlnaTransport(val renderer: DlnaRenderer) : CastTransport {
    override val device: CastDevice get() = renderer.device
    override var canSeek = true
        private set
    private var polls = 0
    private var lastVolume: Float? = null
    private val av get() = renderer.avTransport
    private val instance = "InstanceID" to "0"

    /**
     * The renderer's Sink protocolInfo list (`ConnectionManager.GetProtocolInfo`); null when it
     * won't say, in which case the original container is offered as is.
     */
    suspend fun sinkProtocols(): List<String>? {
        val manager = renderer.connectionManager ?: return null
        val reply = runCatching { DlnaSoap.call(manager, "GetProtocolInfo", emptyList(), timeoutMs = 8_000) }.getOrNull()
        return DlnaProtocol.sinkList(reply?.childText("Sink"))
    }

    override suspend fun load(media: CastMedia) {
        val protocolInfo = media.protocolInfo ?: "http-get:*:${media.contentType}:*"
        canSeek = media.seekable
        // A renderer mid-playback (or just finished) need not take new media: LG wedges in
        // TRANSITIONING, Sony answers 701 until stopped. Stop and wait for idle first.
        settle()
        val metadata = DlnaProtocol.didlLite(media.title, media.url, protocolInfo)
        val arguments = listOf(instance, "CurrentURI" to media.url, "CurrentURIMetaData" to metadata)
        try {
            DlnaSoap.call(av, "SetAVTransportURI", arguments)
        } catch (e: Exception) {
            AppLog.i(TAG, "SetAVTransportURI failed, clearing the transport and retrying: ${e.message}")
            settle()
            DlnaSoap.call(av, "SetAVTransportURI", arguments)
        }
        try {
            play()
        } catch (e: Exception) {
            // Opening a large file can leave Play unanswered past the timeout: check the state.
            if (!waitForPlaying()) throw e
        }
        if (media.startPosition > 1 && media.seekable) {
            runCatching { seek(media.startPosition) }.onFailure { AppLog.i(TAG, "resume seek failed: ${it.message}") }
        }
    }

    override suspend fun play() {
        DlnaSoap.call(av, "Play", listOf(instance, "Speed" to "1"))
    }

    override suspend fun pause() {
        DlnaSoap.call(av, "Pause", listOf(instance))
    }

    override suspend fun seek(seconds: Double) {
        if (!canSeek) throw CastException("This stream can't seek on this TV.")
        DlnaSoap.call(av, "Seek", listOf(instance, "Unit" to "REL_TIME", "Target" to DlnaSoap.clock(seconds)))
    }

    override suspend fun setVolume(volume: Float) {
        val rc = renderer.renderingControl ?: throw CastException("This TV has no volume control.")
        val level = (volume.coerceIn(0f, 1f) * 100).roundToInt()
        DlnaSoap.call(rc, "SetVolume", listOf(instance, "Channel" to "Master", "DesiredVolume" to level.toString()), timeoutMs = 8_000)
        lastVolume = level / 100f
    }

    override suspend fun status(): CastPlaybackState? {
        val transport = runCatching { DlnaSoap.call(av, "GetTransportInfo", listOf(instance), timeoutMs = 8_000) }.getOrNull()
            ?: return null
        val position = runCatching { DlnaSoap.call(av, "GetPositionInfo", listOf(instance), timeoutMs = 8_000) }.getOrNull()
        polls += 1
        val rc = renderer.renderingControl
        if (polls % 5 == 1 && rc != null) {
            runCatching { DlnaSoap.call(rc, "GetVolume", listOf(instance, "Channel" to "Master"), timeoutMs = 8_000) }.getOrNull()
                ?.childText("CurrentVolume")?.toFloatOrNull()?.let { lastVolume = it / 100f }
        }
        return CastPlaybackState(
            phase = phase(transport.childText("CurrentTransportState")),
            position = DlnaSoap.seconds(position?.childText("RelTime")) ?: 0.0,
            duration = DlnaSoap.seconds(position?.childText("TrackDuration")) ?: 0.0,
            volume = lastVolume,
        )
    }

    override suspend fun stop() {
        runCatching { DlnaSoap.call(av, "Stop", listOf(instance), timeoutMs = 15_000) }
    }

    /** Stops the transport until it reports STOPPED or NO_MEDIA_PRESENT (three tries). */
    private suspend fun settle() {
        repeat(3) {
            runCatching { DlnaSoap.call(av, "Stop", listOf(instance)) }
            val state = runCatching { DlnaSoap.call(av, "GetTransportInfo", listOf(instance), timeoutMs = 8_000) }.getOrNull()
                ?.childText("CurrentTransportState")?.uppercase().orEmpty()
            if ("STOPPED" in state || "NO_MEDIA_PRESENT" in state) return
        }
    }

    /**
     * A stalled start gets another Play every third poll (as a viewer reaching for the TV remote
     * would); a position that moves counts as started even in TRANSITIONING.
     */
    private suspend fun waitForPlaying(): Boolean {
        var lastPosition: Double? = null
        for (attempt in 0 until 30) {
            delay(2_000)
            val state = status() ?: continue
            if (state.phase == CastPlaybackState.Phase.PLAYING) return true
            if (state.phase == CastPlaybackState.Phase.IDLE) return false
            if (lastPosition != null && state.position > lastPosition) return true
            lastPosition = state.position
            if ((attempt + 1) % 3 == 0) runCatching { play() }
        }
        return false
    }

    companion object {
        private const val TAG = "DLNA"

        /** AVTransport's `CurrentTransportState`; STOPPED / NO_MEDIA_PRESENT are idle (the controller decides if it finished). */
        fun phase(state: String?): CastPlaybackState.Phase = when (state?.trim()?.uppercase()) {
            "PLAYING" -> CastPlaybackState.Phase.PLAYING
            "PAUSED_PLAYBACK", "PAUSED_RECORDING" -> CastPlaybackState.Phase.PAUSED
            "TRANSITIONING" -> CastPlaybackState.Phase.BUFFERING
            else -> CastPlaybackState.Phase.IDLE
        }
    }
}

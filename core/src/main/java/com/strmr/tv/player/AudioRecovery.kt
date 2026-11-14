package com.strmr.tv.player

import androidx.media3.common.PlaybackException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

/** Technical evidence only: none of these signals proves a globally defective file. */
enum class AudioRecoveryHint(val description: String) {
    DECODER("The selected audio could not be decoded on this device."),
    OUTPUT("This device could not initialize or write audio output."),
    MISSING("The server expected audio, but this device has no audio track while video advances."),
}

fun audioRecoveryEligible(session: PlayerSession, enabled: Boolean, contextCurrent: Boolean): Boolean =
    enabled && contextCurrent && session.reportsProgress && !session.isLive && !session.localFile &&
        !session.prequeueId.isNullOrBlank() && session.request.audioRecoveryCount == 0

/** A real playback observation, never a silence, volume-level or guessed spoken-language detector. */
data class AudioAvailability(
    val nowMs: Long,
    val positionMs: Long,
    val playing: Boolean,
    val videoSelected: Boolean,
    val expectedAudio: Boolean,
    val hasAudioTracks: Boolean,
    val audioEnabled: Boolean,
    val volume: Float,
)

/** One suggestion per playback, after local decoder fallback; seeking/pausing restarts missing-track observation. */
class AudioRecoveryPolicy(private val missingAfterMs: Long = 10_000) {
    private var offered = false
    private var since: Long? = null
    private var startPosition = 0L
    private var lastPosition = 0L

    fun terminalFailure(errorCode: Int, audioRenderer: Boolean, canPrepare: Boolean): AudioRecoveryHint? {
        if (!canPrepare || offered) return null
        val hint = when (errorCode) {
            PlaybackException.ERROR_CODE_AUDIO_TRACK_INIT_FAILED,
            PlaybackException.ERROR_CODE_AUDIO_TRACK_WRITE_FAILED,
            PlaybackException.ERROR_CODE_AUDIO_TRACK_OFFLOAD_WRITE_FAILED -> AudioRecoveryHint.OUTPUT
            PlaybackException.ERROR_CODE_DECODER_INIT_FAILED,
            PlaybackException.ERROR_CODE_DECODER_QUERY_FAILED,
            PlaybackException.ERROR_CODE_DECODING_FAILED,
            PlaybackException.ERROR_CODE_DECODING_FORMAT_UNSUPPORTED,
            PlaybackException.ERROR_CODE_DECODING_FORMAT_EXCEEDS_CAPABILITIES -> AudioRecoveryHint.DECODER.takeIf { audioRenderer }
            else -> null
        }
        if (hint != null) offered = true
        return hint
    }

    fun observe(value: AudioAvailability, canPrepare: Boolean): AudioRecoveryHint? {
        val suspicious = canPrepare && !offered && value.playing && value.videoSelected && value.expectedAudio &&
            !value.hasAudioTracks && value.audioEnabled && value.volume > 0f
        if (!suspicious) { since = null; return null }
        val elapsed = since?.let { value.nowMs - it }
        if (elapsed == null || elapsed < 0 || value.positionMs < lastPosition || value.positionMs - startPosition > elapsed + 2_000) {
            since = value.nowMs
            startPosition = value.positionMs
            lastPosition = value.positionMs
            return null
        }
        lastPosition = value.positionMs
        if (elapsed < missingAfterMs || value.positionMs - startPosition < 1_000) return null
        offered = true
        since = null
        return AudioRecoveryHint.MISSING
    }
}

data class AudioRecoverySuggestion(val hint: AudioRecoveryHint, val flow: ReleaseReports)

/** A transient retry only. The user owns adoption and any later explicit report. */
class AudioRecoveryAssistant(
    private val scope: CoroutineScope,
    private val createFlow: () -> ReleaseReports,
    private val contextCurrent: () -> Boolean,
) {
    private val mutable = MutableStateFlow<AudioRecoverySuggestion?>(null)
    val suggestion: StateFlow<AudioRecoverySuggestion?> = mutable
    val active get() = mutable.value != null
    val flow get() = mutable.value?.flow
    private var loading: Job? = null

    fun suggest(hint: AudioRecoveryHint): Boolean {
        if (active || !contextCurrent()) return false
        val flow = createFlow()
        val suggestion = AudioRecoverySuggestion(hint, flow)
        mutable.value = suggestion
        loading = scope.launch {
            try {
                flow.load()
                if (mutable.value === suggestion && contextCurrent() && flow.state.value.options != null) flow.submit(false, true)
                else if (!contextCurrent()) dismiss()
            } catch (e: CancellationException) { throw e }
        }
        return true
    }

    fun dismiss() {
        val previous = mutable.value
        mutable.value = null
        loading?.cancel()
        loading = null
        previous?.flow?.close()
    }
}

fun audioRecoveryMessage(hint: AudioRecoveryHint, state: ReleaseReportState): String {
    val progress = when {
        state.ready != null -> "An alternative is ready. Review it before switching."
        state.busy || (state.options == null && state.message == null) -> "Preparing an alternative. No report or permanent exclusion is saved."
        else -> "Automatic preparation could not finish. Open source controls or dismiss this suggestion."
    }
    return "${hint.description} $progress"
}

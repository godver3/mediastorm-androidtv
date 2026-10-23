package com.strmr.tv.player

import androidx.annotation.OptIn
import androidx.media3.common.text.Cue
import androidx.media3.common.util.UnstableApi
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/** A downloaded or translated subtitle, offered in the Subtitles menu beside the stream's own. */
data class ExternalTrack(
    /** "Online: French", "Translated: French". */
    val title: String,
    /** The release or provider, shown under the title. */
    val detail: String?,
    val cues: List<VttCue>,
)

/**
 * The one external subtitle track a playback holds (as the Expo player's `external` option):
 * while it is selected, its cues for the current position are handed to [SubtitleOverlay] in
 * place of ExoPlayer's, so they draw in the device's style.
 */
@OptIn(UnstableApi::class)
class ExternalSubtitles(
    private val scope: CoroutineScope,
    private val position: () -> Long,
    private val publish: (List<Cue>) -> Unit,
) {
    val track = MutableStateFlow<ExternalTrack?>(null)
    val selected = MutableStateFlow(false)
    private var ticker: Job? = null
    private var shown: List<VttCue> = emptyList()

    /** Loads `next` as the external track and shows it. */
    fun show(next: ExternalTrack) {
        track.value = next
        select()
    }

    /** New cues for the loaded track (a translation that grew), keeping its selection. */
    fun updateCues(cues: List<VttCue>) {
        track.value = track.value?.copy(cues = cues) ?: return
    }

    fun select() {
        if (track.value == null) return
        selected.value = true
        shown = emptyList()
        if (ticker?.isActive == true) return
        ticker = scope.launch {
            while (isActive) {
                val now = WebVtt.active(track.value?.cues.orEmpty(), position())
                if (now != shown) {
                    shown = now
                    publish(now.map { Cue.Builder().setText(it.text).build() })
                }
                delay(100)
            }
        }
    }

    /** Back to the stream's own subtitles (or none); the track stays in the menu. */
    fun deselect() {
        if (!selected.value) return
        selected.value = false
        ticker?.cancel()
        ticker = null
        shown = emptyList()
        publish(emptyList())
    }

    fun cancel() {
        ticker?.cancel()
    }
}

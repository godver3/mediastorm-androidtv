package com.strmr.tv.ui.details

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.layout
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.dp
import androidx.tv.material3.Icon
import androidx.tv.material3.IconButton
import com.strmr.tv.api.ApiClient
import com.strmr.tv.api.PlaybackSettings
import com.strmr.tv.api.PrequeueStatus
import com.strmr.tv.api.playbackSettings
import com.strmr.tv.player.EffectiveTracks
import com.strmr.tv.player.ManualSourceTracks
import com.strmr.tv.player.ManualTrackChoice
import com.strmr.tv.player.ManualTracks
import com.strmr.tv.player.PrequeuePick
import com.strmr.tv.player.WarmPrequeue
import com.strmr.tv.player.releaseTitle
import com.strmr.tv.player.trackLists
import com.strmr.tv.ui.AppIcons

/**
 * Details › Audio & Subtitles: picks for the stream the page's prequeue resolved, kept for the
 * visit by its stream path and cleared after playback.
 */
class PrequeueTracks(val model: DetailsModel) {
    private var choice by mutableStateOf(ManualTrackChoice())
    private var choicePath by mutableStateOf<String?>(null)
    var open by mutableStateOf(false)
    var playback by mutableStateOf<PlaybackSettings?>(null)

    /** The page's prequeue while it resolves, or once it lists tracks for the episode Play targets; null hides the button. */
    fun visible(warm: WarmPrequeue?): WarmPrequeue? {
        if (model.primaryVersions.isNotEmpty()) return null // Play uses a library copy
        if (warm == null || warm.titleId != model.target.titleId || warm.profile != ApiClient.profileId) return null
        return when (warm.phase) {
            WarmPrequeue.Phase.RESOLVING -> warm
            WarmPrequeue.Phase.UNAVAILABLE -> null
            WarmPrequeue.Phase.READY -> {
                val status = warm.tracks ?: return null
                if (!model.target.isMovie) {
                    val ep = model.primaryEpisode ?: return null
                    val target = status.targetEpisode
                    if (target?.seasonNumber != ep.seasonNumber || target.episodeNumber != ep.episodeNumber) return null
                }
                warm
            }
        }
    }

    fun choiceFor(status: PrequeueStatus): ManualTrackChoice = if (choicePath == status.streamPath) choice else ManualTrackChoice()

    fun choose(status: PrequeueStatus, new: ManualTrackChoice) {
        choicePath = status.streamPath
        choice = new
    }

    fun clear() {
        choicePath = null
        choice = ManualTrackChoice()
    }

    /** The picks to hand to Play, bound to the prequeued stream. */
    fun pick(warm: WarmPrequeue?): PrequeuePick? {
        val status = visible(warm)?.tracks ?: return null
        val path = status.streamPath ?: return null
        val c = choiceFor(status).takeIf { !it.isEmpty } ?: return null
        val lists = status.trackLists
        return PrequeuePick(path, status.targetEpisode, ManualSourceTracks(c, lists.audio, lists.subtitles))
    }

    /** The profile's languages after this device's overrides, for the panel's allowed-language filter. */
    fun defaults(status: PrequeueStatus): TrackDefaults {
        val e = EffectiveTracks.current(status.trackSelection, playback)
        return TrackDefaults(e.audioLanguage, e.subtitleLanguage, e.subtitleMode, e.allowedLanguages)
    }

    /**
     * What Play picks: the server's selection, unless this device's overrides change it (the
     * player then picks by language, as the panel's own defaults do).
     */
    fun preselected(status: PrequeueStatus): PreselectedTracks? {
        val selection = status.trackSelection ?: return null
        val e = EffectiveTracks.current(selection, playback)
        if (e.clientAudio || e.clientSubtitles) return null
        val subtitle = if (selection.subtitleMode == "off") null else (selection.subtitle ?: selection.textSubtitle)?.streamIndex
        return PreselectedTracks(selection.audio?.streamIndex, subtitle)
    }

    /** "English · EAC3 · Subtitles Off", for the picks or else what Play would pick. */
    fun caption(status: PrequeueStatus): String {
        val lists = status.trackLists
        val chosen = choiceFor(status)
        val pre = preselected(status)
        val d = defaults(status)
        val audioIndex = chosen.audio ?: pre?.audio ?: ManualTracks.defaultAudio(lists.audio, d.audioLanguage)
        val subtitleIndex = when (chosen.subtitle) {
            null -> if (pre != null) pre.subtitle else ManualTracks.defaultSubtitle(lists.subtitles, d.subtitleLanguage, d.subtitleMode)
            ManualTrackChoice.OFF -> null
            else -> chosen.subtitle
        }
        val parts = mutableListOf<String>()
        (lists.audio.firstOrNull { it.index == audioIndex } ?: lists.audio.firstOrNull())?.let { a ->
            parts += ManualTracks.languageName(a.language)
            a.codec.ifBlank { null }?.let { parts += it.uppercase() }
        }
        parts += lists.subtitles.firstOrNull { it.index == subtitleIndex }?.let { "${ManualTracks.languageName(it.language)} Subtitles" }
            ?: "Subtitles Off"
        return parts.joinToString(" · ")
    }
}

/**
 * Icon-only like the other secondary actions: a spinner while the prequeue resolves, then the
 * tracks icon; focused, a caption under it names the tracks Play uses.
 */
@Composable
fun PrequeueTracksButton(state: PrequeueTracks, warm: WarmPrequeue?) {
    val shown = state.visible(warm) ?: return
    val status = shown.tracks
    var focused by remember { mutableStateOf(false) }
    LaunchedEffect(status != null) {
        if (status != null && state.playback == null) state.playback = runCatching { ApiClient.playbackSettings() }.getOrNull()
    }
    Box(contentAlignment = Alignment.BottomCenter) {
        IconButton(onClick = { if (status != null) state.open = true }, modifier = Modifier.onFocusChanged { focused = it.isFocused }) {
            if (status == null) {
                CircularProgressIndicator(Modifier.size(18.dp), color = Color.White, strokeWidth = 2.dp)
            } else {
                Icon(AppIcons.Waveform, contentDescription = "Audio & Subtitles")
            }
        }
        if (focused) FocusCaption(if (status == null) "Finding a source…" else state.caption(status))
    }
}

/** The track panel for the prequeued stream; Play there starts what the primary button plays, with the picks. */
@Composable
fun PrequeueTracksPanel(state: PrequeueTracks, warm: WarmPrequeue?, onPlay: () -> Unit) {
    if (!state.open) return
    val status = state.visible(warm)?.tracks ?: return
    SourceTracksDialog(
        release = status.releaseTitle ?: state.model.target.name,
        state = ProbeState(status.trackLists),
        defaults = state.defaults(status),
        choice = state.choiceFor(status),
        preselected = state.preselected(status),
        onChoice = { state.choose(status, it) },
        onRetry = {},
        onPlay = {
            state.open = false
            onPlay()
        },
        onDismiss = { state.open = false },
    )
}

/** Laid out at no size, centred under its parent's bottom edge, so the caption doesn't move the row. */
internal fun Modifier.floatingBelow(): Modifier = layout { measurable, _ ->
    val placeable = measurable.measure(Constraints())
    layout(0, 0) { placeable.place(-placeable.width / 2, 6.dp.roundToPx()) }
}

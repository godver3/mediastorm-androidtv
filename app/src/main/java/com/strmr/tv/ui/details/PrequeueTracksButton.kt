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

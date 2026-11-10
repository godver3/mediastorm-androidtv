package com.strmr.mobile.ui.details

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.selection.selectable
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.strmr.mobile.ui.components.SheetHeader
import com.strmr.tv.api.TrackProbe
import com.strmr.tv.player.ManualTrackChoice
import com.strmr.tv.player.ManualTracks
import com.strmr.tv.ui.details.PreselectedTracks
import com.strmr.tv.ui.details.ProbeState
import com.strmr.tv.ui.details.TrackDefaults

/**
 * Audio & Subtitles for one stream (the TV track panel as a sheet): its audio and subtitle
 * tracks within the profile's allowed languages, the defaults preselected, a tap choosing one,
 * then Play. While the probe runs it shows a spinner; when it fails, the reason with Retry, and
 * Play still plays with the defaults.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TracksSheet(
    release: String,
    state: ProbeState,
    defaults: TrackDefaults?,
    choice: ManualTrackChoice,
    preselected: PreselectedTracks? = null,
    playLabel: String = "Play",
    onChoice: (ManualTrackChoice) -> Unit,
    onRetry: (() -> Unit)?,
    onPlay: () -> Unit,
    onDismiss: () -> Unit,
) {
    val probe = state.probe
    val tracks = probe as? TrackProbe.Tracks
    val allowed = defaults?.allowed.orEmpty()
    val audio = tracks?.audio?.let { list -> ManualTracks.allowed(list, allowed) { it.language } }.orEmpty()
    val subtitles = tracks?.subtitles?.let { list -> ManualTracks.allowed(list, allowed) { it.language } }.orEmpty()
    val defaultAudio = if (preselected != null) preselected.audio else ManualTracks.defaultAudio(audio, defaults?.audioLanguage, defaults?.losslessAudio == true)
    val defaultSubtitle = if (preselected != null) preselected.subtitle
    else ManualTracks.defaultSubtitle(subtitles, defaults?.subtitleLanguage, defaults?.subtitleMode,
                                      (audio.firstOrNull { it.index == defaultAudio } ?: audio.firstOrNull())?.language)
    val selectedAudio = choice.audio ?: defaultAudio
    val selectedSubtitle = when (choice.subtitle) {
        null -> defaultSubtitle
        ManualTrackChoice.OFF -> null
        else -> choice.subtitle
    }
    val loaded = tracks != null && (audio.isNotEmpty() || subtitles.isNotEmpty())

    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = loaded)) {
        LazyColumn(Modifier.fillMaxWidth().navigationBarsPadding()) {
            item { SheetHeader("Audio & Subtitles", release, null) }
            when {
                probe == null -> item {
                    Row(Modifier.padding(24.dp), horizontalArrangement = Arrangement.spacedBy(14.dp), verticalAlignment = Alignment.CenterVertically) {
                        CircularProgressIndicator()
                        Text("Loading tracks…")
                    }
                }
                probe is TrackProbe.Failed -> item {
                    Message("Couldn't load tracks: ${probe.reason}\nPlay uses the default tracks.")
                }
                !loaded -> item { Message("No track information available. Play uses the default tracks.") }
                else -> {
                    if (audio.isNotEmpty()) {
                        section("Audio (${audio.size})")
                        items(audio, key = { "a${it.index}" }) { t ->
                            TrackRow(ManualTracks.audioLabel(t), ManualTracks.audioDetail(t), t.index == selectedAudio) {
                                onChoice(choice.copy(audio = t.index))
                            }
                        }
                    }
                    section("Subtitles (${subtitles.size})")
                    item(key = "off") { TrackRow("Off", null, selectedSubtitle == null) { onChoice(choice.copy(subtitle = ManualTrackChoice.OFF)) } }
                    items(subtitles, key = { "s${it.index}" }) { t ->
                        TrackRow(ManualTracks.subtitleLabel(t), ManualTracks.subtitleDetail(t), t.index == selectedSubtitle) {
                            onChoice(choice.copy(subtitle = t.index))
                        }
                    }
                }
            }
            item {
                Row(Modifier.padding(horizontal = 24.dp, vertical = 16.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    if (probe is TrackProbe.Failed && onRetry != null) OutlinedButton(onClick = onRetry) { Text("Retry") }
                    Button(onClick = onPlay, modifier = Modifier.weight(1f)) { Text(if (loaded) playLabel else "$playLabel with Defaults") }
                }
            }
        }
    }
}

@Composable
private fun Message(text: String) =
    Text(text, Modifier.padding(24.dp), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)

private fun LazyListScope.section(title: String) = item(key = "section-$title") {
    Text(
        title, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(start = 24.dp, end = 24.dp, top = 16.dp, bottom = 4.dp),
    )
}

@Composable
private fun TrackRow(title: String, detail: String?, selected: Boolean, onClick: () -> Unit) {
    ListItem(
        headlineContent = { Text(title, maxLines = 1, overflow = TextOverflow.Ellipsis) },
        supportingContent = detail?.takeIf { it.isNotEmpty() }?.let { { Text(it, maxLines = 1, overflow = TextOverflow.Ellipsis) } },
        leadingContent = { RadioButton(selected = selected, onClick = null) },
        colors = ListItemDefaults.colors(containerColor = Color.Transparent),
        modifier = Modifier.selectable(selected = selected, role = Role.RadioButton, onClick = onClick),
    )
}

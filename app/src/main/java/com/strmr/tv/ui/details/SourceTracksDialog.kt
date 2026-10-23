package com.strmr.tv.ui.details

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.tv.material3.Button
import androidx.tv.material3.Icon
import androidx.tv.material3.ListItem
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.OutlinedButton
import androidx.tv.material3.Text
import com.strmr.tv.api.TrackProbe
import com.strmr.tv.player.ManualTrackChoice
import com.strmr.tv.player.ManualTracks
import com.strmr.tv.ui.Accent
import com.strmr.tv.ui.ignoreHeldSelect

/** A result's track probe for the panel: null while it runs. */
data class ProbeState(val probe: TrackProbe?)

/** What the panel preselects before the user changes anything: the profile's languages and mode. */
data class TrackDefaults(val audioLanguage: String?, val subtitleLanguage: String?, val subtitleMode: String?, val allowed: List<String>)

/** Tracks the server already chose (container stream numbers; a null subtitle is off), preselected instead. */
data class PreselectedTracks(val audio: Int?, val subtitle: Int?)

/**
 * The Expo TV long-press track panel for one manual-selection result: its probed audio and
 * subtitle tracks (limited to the profile's allowed languages), the default ones preselected,
 * Select on a track choosing it, then Play. While the probe runs it shows a spinner; when it
 * fails, "Couldn't load tracks" with Retry, and Play still plays with the defaults.
 */
@Composable
fun SourceTracksDialog(
    release: String,
    state: ProbeState,
    defaults: TrackDefaults?,
    choice: ManualTrackChoice,
    preselected: PreselectedTracks? = null,
    onChoice: (ManualTrackChoice) -> Unit,
    onRetry: () -> Unit,
    onPlay: () -> Unit,
    onDismiss: () -> Unit,
) {
    val tracks = state.probe as? TrackProbe.Tracks
    val audio = tracks?.audio?.let { list -> ManualTracks.allowed(list, defaults?.allowed.orEmpty()) { it.language } }.orEmpty()
    val subtitles = tracks?.subtitles?.let { list -> ManualTracks.allowed(list, defaults?.allowed.orEmpty()) { it.language } }.orEmpty()
    val defaultAudio = if (preselected != null) preselected.audio else ManualTracks.defaultAudio(audio, defaults?.audioLanguage)
    val defaultSubtitle = if (preselected != null) preselected.subtitle
        else ManualTracks.defaultSubtitle(subtitles, defaults?.subtitleLanguage, defaults?.subtitleMode)
    val selectedAudio = choice.audio ?: defaultAudio
    val selectedSubtitle = when (choice.subtitle) {
        null -> defaultSubtitle
        ManualTrackChoice.OFF -> null
        else -> choice.subtitle
    }
    val first = remember { FocusRequester() }
    val action = remember { FocusRequester() }
    // Down from the last track lands on Play, not the button nearest below it.
    val toPlay = Modifier.focusProperties { down = action }
    val loaded = tracks != null && (audio.isNotEmpty() || subtitles.isNotEmpty())
    LaunchedEffect(state, loaded) { runCatching { if (loaded) first.requestFocus() else action.requestFocus() } }

    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Box(Modifier.fillMaxSize().ignoreHeldSelect().background(Color.Black.copy(alpha = 0.6f)), contentAlignment = Alignment.Center) {
            Column(
                Modifier.width(640.dp).fillMaxHeight(0.86f).background(Color(0xFF1C1C1E), RoundedCornerShape(20.dp)).padding(28.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Text("Track Selection", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
                Text(release, color = Color.White.copy(alpha = 0.6f), maxLines = 2, overflow = TextOverflow.Ellipsis)
                Box(Modifier.weight(1f).fillMaxWidth()) {
                    when {
                        state.probe == null -> Row(Modifier.padding(top = 16.dp), horizontalArrangement = Arrangement.spacedBy(14.dp),
                                                   verticalAlignment = Alignment.CenterVertically) {
                            CircularProgressIndicator(Modifier.size(20.dp), color = Accent, strokeWidth = 2.dp)
                            Text("Loading tracks…", color = Color.White.copy(alpha = 0.7f))
                        }
                        state.probe is TrackProbe.Failed -> Column(Modifier.padding(top = 16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            Text("Couldn't load tracks", style = MaterialTheme.typography.titleMedium)
                            Text(state.probe.reason, color = Color.White.copy(alpha = 0.6f), maxLines = 3, overflow = TextOverflow.Ellipsis)
                            Text("Play uses the default tracks.", color = Color.White.copy(alpha = 0.6f))
                        }
                        !loaded -> Text("No track information available. Play uses the default tracks.",
                                        Modifier.padding(top = 16.dp), color = Color.White.copy(alpha = 0.7f))
                        else -> LazyColumn(contentPadding = PaddingValues(vertical = 4.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            if (audio.isNotEmpty()) {
                                item { SectionTitle("Audio (${audio.size})") }
                                items(audio, key = { "a${it.index}" }) { t ->
                                    TrackRow(ManualTracks.audioLabel(t), ManualTracks.audioDetail(t), t.index == selectedAudio,
                                             if (t == audio.first()) Modifier.focusRequester(first) else Modifier) {
                                        onChoice(choice.copy(audio = t.index))
                                    }
                                }
                            }
                            item { SectionTitle("Subtitles (${subtitles.size})") }
                            item(key = "off") {
                                val off = if (audio.isEmpty()) Modifier.focusRequester(first) else Modifier
                                TrackRow("Off", null, selectedSubtitle == null, if (subtitles.isEmpty()) off.then(toPlay) else off) {
                                    onChoice(choice.copy(subtitle = ManualTrackChoice.OFF))
                                }
                            }
                            items(subtitles, key = { "s${it.index}" }) { t ->
                                TrackRow(ManualTracks.subtitleLabel(t), ManualTracks.subtitleDetail(t), t.index == selectedSubtitle,
                                         if (t == subtitles.last()) toPlay else Modifier) {
                                    onChoice(choice.copy(subtitle = t.index))
                                }
                            }
                        }
                    }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    if (state.probe is TrackProbe.Failed) {
                        Button(onClick = onRetry, modifier = Modifier.focusRequester(action)) { Text("Retry") }
                        OutlinedButton(onClick = onPlay) { Text("Play with Defaults") }
                    } else {
                        Button(onClick = onPlay, modifier = Modifier.focusRequester(action)) { Text(if (loaded) "Play" else "Play with Defaults") }
                    }
                    OutlinedButton(onClick = onDismiss) { Text("Cancel") }
                }
            }
        }
    }
}

@Composable
private fun SectionTitle(text: String) =
    Text(text, Modifier.padding(top = 10.dp, bottom = 2.dp), color = Color.White.copy(alpha = 0.6f),
         style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.SemiBold)

@Composable
private fun TrackRow(title: String, detail: String?, selected: Boolean, modifier: Modifier, onClick: () -> Unit) {
    ListItem(
        selected = selected,
        onClick = onClick,
        headlineContent = { Text(title, maxLines = 1, overflow = TextOverflow.Ellipsis) },
        supportingContent = detail?.takeIf { it.isNotEmpty() }?.let { { Text(it, maxLines = 1, overflow = TextOverflow.Ellipsis) } },
        trailingContent = if (selected) { { Icon(Icons.Filled.Check, contentDescription = "Selected") } } else null,
        modifier = modifier,
    )
}

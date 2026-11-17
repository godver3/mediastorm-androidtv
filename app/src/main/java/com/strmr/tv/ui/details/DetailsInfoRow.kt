package com.strmr.tv.ui.details

import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.tv.material3.Text
import com.strmr.tv.api.ApiClient
import com.strmr.tv.api.playbackSettings
import com.strmr.tv.player.WarmPrequeue

/**
 * Under the actions, when the profile turns any segment on: the prequeued release (middle-
 * truncated; "Finding a source…" while it resolves) · runtime · the tracks Play uses
 * ([DetailsInfoLine]). Styled as the metadata line.
 */
@Composable
fun DetailsInfoRow(model: DetailsModel, tracks: PrequeueTracks, warm: WarmPrequeue?, modifier: Modifier = Modifier) {
    val settings = DetailsInfo.settings
    val ready = settings.showTracks && tracks.visible(warm)?.tracks != null
    // The caption reflects this device's track overrides, as the Audio & Subtitles button's does.
    LaunchedEffect(ready) {
        if (ready && tracks.playback == null) tracks.playback = runCatching { ApiClient.playbackSettings() }.getOrNull()
    }
    val line = DetailsInfoLine.of(settings, model, tracks, warm) ?: return
    val separator = if (line.rest != null) Separator else ""
    Row(modifier.widthIn(max = 760.dp), verticalAlignment = Alignment.CenterVertically) {
        if (line.findingSource) {
            CircularProgressIndicator(Modifier.padding(end = 6.dp).size(12.dp), color = InfoColor, strokeWidth = 1.5.dp)
            InfoText("Finding a source…$separator")
        }
        line.filename?.let { name ->
            // Middle-cut to the room the rest leaves, so the release's tail (group, codec) stays
            // readable. Cut by measuring rather than TextOverflow.MiddleEllipsis, whose text keeps
            // the full width and leaves a gap before the separator.
            BoxWithConstraints(Modifier.weight(1f, fill = false)) {
                val measurer = rememberTextMeasurer()
                val style = infoStyle()
                val room = constraints.maxWidth
                val fitted = remember(name, separator, room) {
                    middleCut(name, separator) { measurer.measure(it, style, maxLines = 1).size.width <= room }
                }
                InfoText(fitted)
            }
        }
        line.rest?.let { InfoText(it) }
    }
}

/** [name] + [suffix], cut from the middle of [name] with "…" until [fits] (at least a few characters each side). */
internal fun middleCut(name: String, suffix: String, fits: (String) -> Boolean): String {
    if (fits(name + suffix)) return name + suffix
    var lo = 2
    var hi = name.length - 1
    var best = name.take(1) + "…" + name.takeLast(1) + suffix
    while (lo <= hi) {
        val keep = (lo + hi) / 2
        val head = (keep + 1) / 2
        val candidate = name.take(head) + "…" + name.takeLast(keep - head) + suffix
        if (fits(candidate)) { best = candidate; lo = keep + 1 } else hi = keep - 1
    }
    return best
}

private const val Separator = " · "
private val InfoColor = Color.White.copy(alpha = 0.65f)

private fun infoStyle() = TextStyle(fontSize = 13.sp, fontWeight = FontWeight.Medium, color = InfoColor)

@Composable
private fun InfoText(text: String, modifier: Modifier = Modifier) {
    Text(text, style = infoStyle(), maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = modifier)
}

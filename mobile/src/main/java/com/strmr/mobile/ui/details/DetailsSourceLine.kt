package com.strmr.mobile.ui.details

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Storage
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.strmr.tv.api.ApiClient
import com.strmr.tv.api.playbackSettings
import com.strmr.tv.player.WarmPrequeue
import com.strmr.tv.player.releaseTitle
import com.strmr.tv.ui.AppIcons

/**
 * Under the actions: what Play will use (the iPhone page's line). A library copy ("Plex ·
 * 4K · HEVC"), else the prequeued stream's release with its Audio & Subtitles (tap to change
 * them), or "Finding a source…" while the prequeue resolves.
 */
@Composable
fun DetailsSourceLine(state: DetailsPageState, warm: WarmPrequeue?) {
    val versions = state.model.libraryVersions(state.activeEpisode)
    val best = versions.firstOrNull()
    if (best != null) {
        val text = (if (versions.size > 1) "${versions.size} versions · " else "") + "${best.source.label} · ${best.quality}"
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Filled.Storage, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(16.dp))
            Text(text, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        return
    }
    val tracks = state.prequeueTracks
    val shown = tracks.visible(warm) ?: return
    val status = shown.tracks
    LaunchedEffect(status != null) {
        if (status != null && tracks.playback == null) tracks.playback = runCatching { ApiClient.playbackSettings() }.getOrNull()
    }
    if (status == null) {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
            CircularProgressIndicator(Modifier.size(14.dp), strokeWidth = 2.dp)
            Text("Finding a source…", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        return
    }
    Column(
        Modifier.fillMaxWidth().clickable(onClickLabel = "Audio & Subtitles") { tracks.open = true }.padding(vertical = 4.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        status.releaseTitle?.let {
            Text(it, style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Medium, maxLines = 1, overflow = TextOverflow.MiddleEllipsis)
        }
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(AppIcons.Waveform, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(16.dp))
            Text(tracks.caption(status), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                 maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f, fill = false))
            Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant,
                 modifier = Modifier.size(16.dp))
        }
    }
}

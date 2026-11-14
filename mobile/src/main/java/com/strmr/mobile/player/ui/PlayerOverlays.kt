package com.strmr.mobile.player.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.strmr.tv.api.Artwork
import com.strmr.tv.player.PlaybackRequest
import com.strmr.tv.player.PlayerController
import com.strmr.tv.player.SkipAction

/** Skip Intro / Skip Recap / Skip Credits, at the bottom right, shown with or without the controls. */
@Composable
fun SkipButton(action: SkipAction.Skip, modifier: Modifier = Modifier, onClick: () -> Unit) {
    FilledTonalButton(onClick = onClick, modifier = modifier) { Text(action.label, fontWeight = FontWeight.SemiBold) }
}

/**
 * Up Next at the credits: the next episode's still, code and name, Play (with the auto-play
 * countdown when the profile has it) and a close button that dismisses it for this segment.
 */
@Composable
fun UpNextCard(action: SkipAction.NextEpisode, onPlay: () -> Unit, onDismiss: () -> Unit, modifier: Modifier = Modifier) {
    val next = action.next
    val art = next.episode?.imageUrl ?: next.backdrops.firstOrNull() ?: next.title.backdropUrl
    ElevatedCard(modifier.width(300.dp)) {
        Row(Modifier.padding(10.dp), horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
            AsyncImage(
                model = Artwork.url(art, "w500"), contentDescription = null, contentScale = ContentScale.Crop,
                modifier = Modifier.width(112.dp).aspectRatio(16f / 9f).clip(RoundedCornerShape(8.dp)).background(Color.Black),
            )
            Column(Modifier.weight(1f)) {
                Text("Up Next", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
                Text(next.episode?.let { listOfNotNull(it.code, it.name).joinToString(" · ") } ?: next.title.name,
                     style = MaterialTheme.typography.bodyMedium, maxLines = 2, overflow = TextOverflow.Ellipsis)
            }
            IconButton(onClick = onDismiss, modifier = Modifier.size(32.dp)) { Icon(Icons.Filled.Close, "Dismiss") }
        }
        Button(onClick = onPlay, modifier = Modifier.fillMaxWidth().padding(start = 10.dp, end = 10.dp, bottom = 10.dp)) {
            Icon(Icons.Filled.SkipNext, contentDescription = null)
            Text(action.countdown?.let { "  Play in $it" } ?: "  Play Next Episode")
        }
    }
}

/**
 * Over a live channel while it is tuned again (the core's live retune, Stremio failover
 * included): its logo and name and "Reconnecting…" on black, as the TV shows it.
 */
@Composable
fun ReconnectingCover(request: PlaybackRequest, note: String?, modifier: Modifier = Modifier) {
    Column(
        modifier.fillMaxSize().background(Color.Black),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(16.dp, Alignment.CenterVertically),
    ) {
        var logoFailed by remember { mutableStateOf(false) }
        Artwork.logo(request.logoUrl ?: request.title.logoUrl)?.takeIf { !logoFailed }?.let {
            AsyncImage(model = it, contentDescription = null, contentScale = ContentScale.Fit,
                       onError = { logoFailed = true }, modifier = Modifier.size(200.dp, 90.dp))
        }
        Text(request.title.name, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, color = Color.White,
             maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(horizontal = 32.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
            CircularProgressIndicator(Modifier.size(20.dp), color = Color.White, strokeWidth = 2.dp)
            Text(note ?: "Reconnecting…", color = Color.White.copy(alpha = 0.75f))
        }
    }
}

@Composable
fun PlaybackErrorDialog(message: String, onClose: () -> Unit, onReport: (() -> Unit)? = null) {
    AlertDialog(
        onDismissRequest = onClose,
        title = { Text("Playback Error") },
        text = { Text(message) },
        confirmButton = { TextButton(onClick = onClose) { Text("Close") } },
        dismissButton = { if (onReport != null) TextButton(onClick = onReport) { Text("Report / change source") } },
    )
}

/** A short message in a pill (a notice from the controller, the online-subtitle status). */
@Composable
fun StatusPill(text: String, modifier: Modifier = Modifier) {
    Text(
        text, color = Color.White, style = MaterialTheme.typography.labelLarge,
        modifier = modifier.background(Color.Black.copy(alpha = 0.7f), RoundedCornerShape(50)).padding(horizontal = 16.dp, vertical = 8.dp),
    )
}

/** The online subtitle search's status ("Searching for subtitles…"), fading in and out. */
@Composable
fun SubtitleStatus(controller: PlayerController, modifier: Modifier = Modifier) {
    val message by controller.online.status.collectAsState()
    var last by remember { mutableStateOf("") }
    message?.let { last = it }
    AnimatedVisibility(message != null, modifier, enter = fadeIn(), exit = fadeOut()) { StatusPill(last) }
}

/** Brightness or volume while a side swipe changes it. */
@Composable
fun LevelIndicator(icon: ImageVector, level: Float, modifier: Modifier = Modifier) {
    Row(
        modifier.background(Color.Black.copy(alpha = 0.7f), RoundedCornerShape(50)).padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Icon(icon, contentDescription = null, tint = Color.White)
        LinearProgressIndicator(progress = { level }, modifier = Modifier.width(120.dp), drawStopIndicator = {})
    }
}

/** The double-tap seek label, over the side that was tapped. */
@Composable
fun SeekRippleLabel(text: String, modifier: Modifier = Modifier) {
    Box(modifier.size(120.dp).background(Color.White.copy(alpha = 0.15f), RoundedCornerShape(50)), contentAlignment = Alignment.Center) {
        Text(text, color = Color.White, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
    }
}

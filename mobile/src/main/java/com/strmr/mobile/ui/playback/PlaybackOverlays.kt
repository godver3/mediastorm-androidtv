package com.strmr.mobile.ui.playback

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.strmr.tv.api.Artwork
import com.strmr.tv.player.PlaybackCoordinator
import com.strmr.tv.player.PlaybackRequest

/** The stream-start cover while a play request resolves, and its error. */
@Composable
fun PlaybackOverlays() {
    val resolving by PlaybackCoordinator.resolving.collectAsState()
    val error by PlaybackCoordinator.errorMessage.collectAsState()
    resolving?.let { StreamStartCover(it) }
    error?.let {
        AlertDialog(
            onDismissRequest = { PlaybackCoordinator.errorMessage.value = null },
            title = { Text("Playback Error") },
            text = { Text(it) },
            confirmButton = { TextButton(onClick = { PlaybackCoordinator.errorMessage.value = null }) { Text("OK") } },
        )
    }
}

/**
 * The stream-start screen while the server resolves a source (the iPhone's loading view): the
 * poster upright, the backdrop when the screen is wide, the logo (or title), the episode, the
 * prequeue's stage and the release being tried, and Cancel (Back cancels too).
 */
@Composable
private fun StreamStartCover(request: PlaybackRequest) {
    val stage by PlaybackCoordinator.stage.collectAsState()
    val detail by PlaybackCoordinator.detail.collectAsState()
    BackHandler { PlaybackCoordinator.cancelResolve() }
    BoxWithConstraints(
        Modifier.fillMaxSize().background(Color.Black)
            // Takes the touches so the page beneath can't be used while it resolves.
            .clickable(remember { MutableInteractionSource() }, indication = null) {},
    ) {
        val wide = maxWidth > maxHeight
        val backdrop = request.backdrops.firstOrNull() ?: request.title.backdropUrl
        val poster = request.title.displayPosterUrl?.takeIf { it.isNotEmpty() }
        val art = if (wide) backdrop ?: poster else poster ?: backdrop
        AsyncImage(
            model = Artwork.url(art, if (wide) "w1280" else "w780"),
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = Modifier.fillMaxSize(),
        )
        Box(
            Modifier.fillMaxSize().background(
                Brush.verticalGradient(0f to Color.Black.copy(alpha = 0.25f), 0.45f to Color.Black.copy(alpha = 0.45f), 1f to Color.Black.copy(alpha = 0.95f)),
            ),
        )
        Column(
            Modifier.align(if (wide) Alignment.BottomStart else Alignment.BottomCenter).safeDrawingPadding()
                .padding(horizontal = 24.dp, vertical = 32.dp).widthIn(max = 560.dp),
            horizontalAlignment = if (wide) Alignment.Start else Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Title(request, wide)
            request.episode?.let {
                Text(listOfNotNull(it.code, it.name).joinToString(" · "), color = Color.White.copy(alpha = 0.85f),
                     style = MaterialTheme.typography.titleSmall, textAlign = if (wide) TextAlign.Start else TextAlign.Center)
            }
            Row(
                Modifier.padding(top = 6.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                CircularProgressIndicator(Modifier.size(18.dp), color = Color.White, strokeWidth = 2.dp)
                Text(stage, color = Color.White, fontWeight = FontWeight.Medium)
            }
            detail?.let {
                Text(it, color = Color.White.copy(alpha = 0.6f), maxLines = 2, overflow = TextOverflow.Ellipsis,
                     style = MaterialTheme.typography.bodySmall, textAlign = if (wide) TextAlign.Start else TextAlign.Center)
            }
            OutlinedButton(onClick = { PlaybackCoordinator.cancelResolve() }, modifier = Modifier.padding(top = 8.dp)) {
                Text("Cancel", color = Color.White)
            }
        }
    }
}

/** The title's logo, or its name when it has none (or the logo doesn't load). */
@Composable
private fun Title(request: PlaybackRequest, wide: Boolean) {
    var logoFailed by remember { mutableStateOf(false) }
    val logo = Artwork.logo(request.logoUrl ?: request.title.logoUrl)?.takeIf { !logoFailed }
    if (logo != null) {
        AsyncImage(
            model = logo, contentDescription = request.title.name, contentScale = ContentScale.Fit,
            alignment = if (wide) Alignment.BottomStart else Alignment.BottomCenter,
            onError = { logoFailed = true },
            modifier = Modifier.height(72.dp).widthIn(max = 280.dp),
        )
    } else {
        Text(request.title.name, color = Color.White, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold,
             textAlign = if (wide) TextAlign.Start else TextAlign.Center)
    }
}

package com.strmr.tv.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import coil.compose.AsyncImage
import com.strmr.tv.api.Artwork
import com.strmr.tv.player.PlaybackCoordinator
import com.strmr.tv.player.PlaybackRequest

/**
 * The stream-start screen while the server resolves a source: the title's backdrop, its logo
 * and the prequeue's stage (with the release being tried). Back cancels.
 */
@Composable
fun PlaybackLoading(request: PlaybackRequest) {
    val stage by PlaybackCoordinator.stage.collectAsState()
    val detail by PlaybackCoordinator.detail.collectAsState()
    Dialog(
        onDismissRequest = { PlaybackCoordinator.cancelResolve() },
        properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false),
    ) {
        Box(Modifier.fillMaxSize().background(Color.Black)) {
            AsyncImage(
                model = Artwork.url(request.backdrops.firstOrNull() ?: request.title.backdropUrl),
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize(),
            )
            Box(Modifier.fillMaxSize().background(Brush.verticalGradient(0f to Color.Black.copy(alpha = 0.3f), 1f to Color.Black.copy(alpha = 0.9f))))
            Column(
                Modifier.align(Alignment.BottomStart).padding(48.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                val logo = Artwork.logo(request.logoUrl ?: request.title.logoUrl)
                if (logo != null) {
                    AsyncImage(model = logo, contentDescription = request.title.name, contentScale = ContentScale.Fit,
                               alignment = Alignment.BottomStart, modifier = Modifier.height(80.dp).widthIn(max = 300.dp))
                } else {
                    Text(request.title.name, style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
                }
                request.episode?.let { Text(listOfNotNull(it.code, it.name).joinToString(" · "), color = Color.White.copy(alpha = 0.8f)) }
                Box(Modifier.height(6.dp))
                androidx.compose.foundation.layout.Row(
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    CircularProgressIndicator(Modifier.size(18.dp), color = Color.White, strokeWidth = 2.dp)
                    Text(stage, fontWeight = FontWeight.Medium)
                }
                detail?.let {
                    Text(it, color = Color.White.copy(alpha = 0.6f), maxLines = 1, overflow = TextOverflow.Ellipsis,
                         style = MaterialTheme.typography.bodySmall, modifier = Modifier.widthIn(max = 600.dp))
                }
            }
        }
    }
}

/** Resolving and playback errors, over whatever is showing. */
@Composable
fun PlaybackOverlays() {
    val resolving by PlaybackCoordinator.resolving.collectAsState()
    val error by PlaybackCoordinator.errorMessage.collectAsState()
    resolving?.let { PlaybackLoading(it) }
    error?.let { MessageDialog("Playback Error", it) { PlaybackCoordinator.errorMessage.value = null } }
}

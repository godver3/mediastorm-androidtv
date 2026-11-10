package com.strmr.mobile.ui.cast

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Cast
import androidx.compose.material.icons.filled.Movie
import androidx.compose.material.icons.filled.Tv
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import coil.compose.SubcomposeAsyncImage
import com.strmr.mobile.cast.ActiveCast
import com.strmr.mobile.cast.CastController
import com.strmr.mobile.cast.CastPlaybackState

/**
 * Casting to …: what plays on the TV, with its artwork, a scrubber, −10 s / play-pause / +10 s,
 * the TV's volume and Stop Casting (the iPhone app's CastRemoteView). Connecting shows a spinner,
 * a failure its message until Done.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CastRemoteSheet(onDismiss: () -> Unit) {
    val sheet = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val cast = CastController.session
    ModalBottomSheet(onDismissRequest = { CastController.dismissFailure(); onDismiss() }, sheetState = sheet) {
        Column(
            Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(horizontal = 24.dp).padding(bottom = 32.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(20.dp),
        ) {
            if (cast == null) {
                Icon(Icons.Filled.Cast, null, Modifier.size(48.dp).padding(top = 8.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                Text("Nothing Is Casting", style = MaterialTheme.typography.titleLarge)
                Text("Start a cast from a title to control it here.", color = MaterialTheme.colorScheme.onSurfaceVariant)
            } else {
                Content(cast)
            }
            TextButton(onClick = { CastController.dismissFailure(); onDismiss() }) { Text("Done") }
        }
    }
}

@Composable
private fun Content(cast: ActiveCast) {
    Artwork(cast)
    Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(cast.title, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center)
        cast.subtitle?.let {
            Text(it, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Center)
        }
        Row(Modifier.padding(top = 6.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            Icon(Icons.Filled.Cast, null, Modifier.size(16.dp), tint = MaterialTheme.colorScheme.primary)
            Text(statusLine(cast), style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
        }
    }
    when (val phase = cast.phase) {
        ActiveCast.Phase.Connecting -> CircularProgressIndicator(Modifier.padding(top = 8.dp).size(40.dp))
        is ActiveCast.Phase.Failed -> Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Icon(Icons.Filled.Warning, null, tint = MaterialTheme.colorScheme.error)
                Text("Couldn't Cast", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.error)
            }
            Text(phase.message, color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Center)
        }
        ActiveCast.Phase.Active -> CastRemoteControls(cast)
    }
}

private fun statusLine(cast: ActiveCast): String = when (cast.phase) {
    ActiveCast.Phase.Connecting -> "Connecting to ${cast.device.name}…"
    is ActiveCast.Phase.Failed -> cast.device.name
    ActiveCast.Phase.Active ->
        if (cast.state.phase == CastPlaybackState.Phase.BUFFERING || cast.state.phase == CastPlaybackState.Phase.LOADING) {
            "Buffering on ${cast.device.name}"
        } else {
            "Casting to ${cast.device.name}"
        }
}

/** A poster (tall), or a channel's logo fitted on a 16:9 tile. */
@Composable
private fun Artwork(cast: ActiveCast) {
    val shape = RoundedCornerShape(14.dp)
    val tile = MaterialTheme.colorScheme.surfaceContainerHighest
    if (cast.isLive) {
        Box(Modifier.widthIn(max = 360.dp).fillMaxWidth().aspectRatio(16 / 9f).clip(shape).background(tile).padding(28.dp),
            contentAlignment = Alignment.Center) {
            SubcomposeAsyncImage(
                model = cast.artworkUrl, contentDescription = null, contentScale = ContentScale.Fit,
                error = { Icon(Icons.Filled.Tv, null, Modifier.size(48.dp)) },
            )
        }
    } else {
        SubcomposeAsyncImage(
            model = cast.artworkUrl, contentDescription = null, contentScale = ContentScale.Fit,
            modifier = Modifier.padding(top = 8.dp).heightIn(max = 300.dp).clip(shape),
            loading = { Placeholder(tile) },
            error = { Placeholder(tile) },
        )
    }
}

@Composable
private fun Placeholder(color: androidx.compose.ui.graphics.Color) {
    Box(Modifier.height(240.dp).aspectRatio(2 / 3f).background(color), contentAlignment = Alignment.Center) {
        Icon(Icons.Filled.Movie, null, Modifier.size(48.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

package com.strmr.mobile.ui.cast

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.VolumeDown
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material.icons.filled.Forward10
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Replay10
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.strmr.mobile.cast.ActiveCast
import com.strmr.mobile.cast.CastController
import com.strmr.tv.api.formatTimestamp
import kotlinx.coroutines.delay

/** The running cast's controls: the timeline and transport (LIVE and play / pause for a channel), volume, Stop Casting. */
@Composable
fun CastRemoteControls(cast: ActiveCast) {
    if (cast.isLive) {
        Surface(color = Color(0xFFD32F2F), contentColor = Color.White, shape = CircleShape) {
            Text("LIVE", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Black,
                 modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp))
        }
        PlayPause(cast)
    } else {
        Timeline(cast)
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(32.dp)) {
            IconButton(onClick = { CastController.skip(-10.0) }, enabled = cast.canSeek, modifier = Modifier.size(56.dp)) {
                Icon(Icons.Filled.Replay10, "Back 10 seconds", Modifier.size(32.dp))
            }
            PlayPause(cast)
            IconButton(onClick = { CastController.skip(10.0) }, enabled = cast.canSeek, modifier = Modifier.size(56.dp)) {
                Icon(Icons.Filled.Forward10, "Forward 10 seconds", Modifier.size(32.dp))
            }
        }
    }
    Volume(cast)
    OutlinedButton(
        onClick = { CastController.stop() },
        colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.error),
        modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
    ) {
        Icon(Icons.Filled.Stop, null, Modifier.padding(end = 8.dp))
        Text("Stop Casting")
    }
}

@Composable
private fun PlayPause(cast: ActiveCast) {
    val playing = cast.state.isPlaying
    FilledIconButton(onClick = { CastController.togglePlayPause() }, modifier = Modifier.size(72.dp)) {
        Icon(if (playing) Icons.Filled.Pause else Icons.Filled.PlayArrow, if (playing) "Pause" else "Play", Modifier.size(40.dp))
    }
}

/** The scrubber: it follows the receiver except while a finger is on it, and a moment after (the seek lands). */
@Composable
private fun Timeline(cast: ActiveCast) {
    val duration = cast.state.duration.coerceAtLeast(0.0)
    var scrubbing by remember { mutableStateOf<Float?>(null) }
    var settling by remember { mutableStateOf(0) }
    LaunchedEffect(settling) {
        if (settling > 0) {
            delay(1_500)
            scrubbing = null
        }
    }
    val position = scrubbing?.toDouble() ?: if (duration > 0) cast.state.position.coerceAtMost(duration) else cast.state.position
    Column(Modifier.fillMaxWidth()) {
        Slider(
            value = position.toFloat(),
            onValueChange = { scrubbing = it },
            onValueChangeFinished = {
                scrubbing?.let { CastController.seek(it.toDouble()) }
                settling += 1
            },
            valueRange = 0f..duration.toFloat().coerceAtLeast(1f),
            enabled = cast.canSeek && duration > 0,
        )
        Row(Modifier.fillMaxWidth()) {
            val style = MaterialTheme.typography.labelMedium
            val color = MaterialTheme.colorScheme.onSurfaceVariant
            Text(formatTimestamp(position), style = style, color = color, modifier = Modifier.weight(1f))
            Text(if (duration > 0) "-" + formatTimestamp((duration - position).coerceAtLeast(0.0)) else "--:--", style = style, color = color)
        }
        if (!cast.canSeek) {
            Text("Seeking isn't available for this stream.", style = MaterialTheme.typography.labelSmall,
                 color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(top = 4.dp))
        }
    }
}

/** The TV's own volume, when it reports one; sent when the finger lifts. */
@Composable
private fun Volume(cast: ActiveCast) {
    val current = cast.state.volume ?: return
    var dragging by remember { mutableStateOf<Float?>(null) }
    var settling by remember { mutableStateOf(0) }
    LaunchedEffect(settling) {
        if (settling > 0) {
            delay(2_000)
            dragging = null
        }
    }
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Icon(Icons.AutoMirrored.Filled.VolumeDown, null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
        Slider(
            value = dragging ?: current,
            onValueChange = { dragging = it },
            onValueChangeFinished = {
                dragging?.let { CastController.setVolume(it) }
                settling += 1
            },
            modifier = Modifier.weight(1f).padding(horizontal = 8.dp),
        )
        Icon(Icons.AutoMirrored.Filled.VolumeUp, "TV volume", tint = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

package com.strmr.mobile.ui.cast

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CastConnected
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.strmr.mobile.cast.ActiveCast
import com.strmr.mobile.cast.CastController
import com.strmr.mobile.cast.CastDevice

/** The picker asked for from the app's pages (the details page's Cast); the player opens its own. */
object CastUi {
    /** What a pick does; non-null while the picker is up. */
    var pickerAction by mutableStateOf<((CastDevice) -> Unit)?>(null)
        private set

    fun openPicker(onPick: (CastDevice) -> Unit) {
        pickerAction = onPick
    }

    fun closePicker() {
        pickerAction = null
    }
}

/** The picker and the remote over the signed-in app (in MainScaffold). */
@Composable
fun CastHost() {
    CastUi.pickerAction?.let { action ->
        CastDevicePicker(
            onPick = { device ->
                CastUi.closePicker()
                action(device)
            },
            onShowRemote = {
                CastUi.closePicker()
                CastController.remoteVisible = true
            },
            onDismiss = { CastUi.closePicker() },
        )
    }
    if (CastController.remoteVisible) CastRemoteSheet(onDismiss = { CastController.remoteVisible = false })
}

/**
 * "Casting to …" above the navigation bar while a cast runs: tap for the remote, play / pause in
 * place (the iPhone app's CastNowPlayingBar).
 */
@Composable
fun CastNowPlayingBar() {
    val cast = CastController.session ?: return
    Surface(color = MaterialTheme.colorScheme.surfaceContainerHigh) {
        Column {
            HorizontalDivider()
            Row(
                Modifier.fillMaxWidth().clickable { CastController.remoteVisible = true }.padding(start = 16.dp, end = 8.dp, top = 6.dp, bottom = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Icon(Icons.Filled.CastConnected, null, Modifier.size(22.dp), tint = MaterialTheme.colorScheme.primary)
                Column(Modifier.weight(1f)) {
                    Text(cast.title, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold,
                         maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        if (cast.isLive) {
                            Text("LIVE", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Black, color = Color(0xFFE53935))
                        }
                        Text(barStatus(cast), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                             maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                }
                if (cast.phase == ActiveCast.Phase.Active) {
                    val playing = cast.state.isPlaying
                    IconButton(onClick = { CastController.togglePlayPause() }) {
                        Icon(if (playing) Icons.Filled.Pause else Icons.Filled.PlayArrow, if (playing) "Pause" else "Play")
                    }
                }
            }
        }
    }
}

private fun barStatus(cast: ActiveCast) = when (cast.phase) {
    ActiveCast.Phase.Connecting -> "Connecting to ${cast.device.name}…"
    is ActiveCast.Phase.Failed -> "Couldn't cast to ${cast.device.name}"
    ActiveCast.Phase.Active -> "Casting to ${cast.device.name}"
}

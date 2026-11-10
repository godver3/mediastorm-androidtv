package com.strmr.mobile.ui.playback

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import com.strmr.tv.player.PlaybackCoordinator

/** The stream-start cover while a play request resolves, and its error. */
@Composable
fun PlaybackOverlays() {
    val resolving by PlaybackCoordinator.resolving.collectAsState()
    val error by PlaybackCoordinator.errorMessage.collectAsState()
    if (resolving != null) {
        Box(Modifier.fillMaxSize().background(Color.Black), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
    }
    error?.let {
        AlertDialog(
            onDismissRequest = { PlaybackCoordinator.errorMessage.value = null },
            title = { Text("Playback Error") },
            text = { Text(it) },
            confirmButton = { TextButton(onClick = { PlaybackCoordinator.errorMessage.value = null }) { Text("OK") } },
        )
    }
}

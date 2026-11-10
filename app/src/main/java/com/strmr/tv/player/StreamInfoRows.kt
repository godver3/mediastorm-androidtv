package com.strmr.tv.player

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text

/** Live: the stream's formats arrive after the panel opens, and audio changes with the track. */
@Composable
fun StreamInfoRows(controller: PlayerController, modifier: Modifier = Modifier) {
    val video by controller.videoFormat.collectAsState()
    val audio by controller.audioFormat.collectAsState()
    val rows = streamInfoRows(controller.session, video, audio)
    if (rows.isEmpty()) return
    Column(modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Box(Modifier.fillMaxWidth().padding(bottom = 6.dp).height(1.dp).background(Color.White.copy(alpha = 0.15f)))
        rows.forEach { (label, value) ->
            Row {
                Text(label, style = MaterialTheme.typography.bodyMedium, color = Color.White.copy(alpha = 0.55f),
                     modifier = Modifier.width(140.dp))
                Text(value, style = MaterialTheme.typography.bodyMedium, color = Color.White.copy(alpha = 0.85f),
                     maxLines = if (label == "File") 2 else 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
            }
        }
    }
}

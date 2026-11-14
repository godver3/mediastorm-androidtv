package com.strmr.tv.player

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.tv.material3.Button
import androidx.tv.material3.Text

/** Does not request focus or pause playback; the remote explicitly opens the prepared-source menu. */
@Composable
fun AudioRecoveryCard(message: String, onReview: () -> Unit, onDismiss: () -> Unit, modifier: Modifier = Modifier) {
    Column(modifier.widthIn(max = 680.dp).background(Color.Black.copy(alpha = 0.85f), RoundedCornerShape(16.dp)).padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text(message)
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Button(onClick = onReview) { Text("Review alternative") }
            Button(onClick = onDismiss) { Text("Dismiss suggestion") }
        }
    }
}

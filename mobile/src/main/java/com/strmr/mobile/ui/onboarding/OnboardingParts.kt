package com.strmr.mobile.ui.onboarding

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp

@Composable
fun BusyButton(
    text: String,
    busy: Boolean,
    enabled: Boolean,
    modifier: Modifier = Modifier,
    tonal: Boolean = false,
    onClick: () -> Unit,
) {
    val content: @Composable () -> Unit = {
        Box(contentAlignment = Alignment.Center) {
            // Keeps the button's width while the spinner shows.
            Text(text, color = if (busy) LocalContentColor.current.copy(alpha = 0f) else LocalContentColor.current)
            if (busy) CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp, color = LocalContentColor.current)
        }
    }
    val m = modifier.fillMaxWidth().height(48.dp)
    if (tonal) FilledTonalButton(onClick, m, enabled = enabled || busy) { content() }
    else Button(onClick, m, enabled = enabled || busy) { content() }
}

@Composable
fun SectionLabel(text: String, modifier: Modifier = Modifier) {
    Text(
        text, style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = modifier.fillMaxWidth(),
    )
}

@Composable
fun ErrorText(text: String) {
    Text(text, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodyMedium, textAlign = TextAlign.Center)
}

fun Modifier.clickableRow(enabled: Boolean = true, onClick: () -> Unit) = clickable(enabled = enabled, onClick = onClick)

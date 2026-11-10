package com.strmr.mobile.ui.connection

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.CloudDone
import androidx.compose.material.icons.outlined.CloudOff
import androidx.compose.material.icons.outlined.WifiOff
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import com.strmr.mobile.connection.ConnectionManager
import com.strmr.mobile.connection.ConnectionManager.Status
import com.strmr.tv.app.AppSession
import kotlinx.coroutines.delay

/**
 * A strip above the navigation bar while the signed-in app (or offline Downloads) can't reach
 * the server (the iPhone app's `MobileConnectionBanner`): "You're Offline" with a way into
 * Downloads, or "Can't Reach Server" with the retry countdown; "Back Online" for a moment once
 * it answers again. [inDownloads]: shown over offline Downloads (no Downloads button). [modifier]
 * applies inside the strip (system bar padding where the strip sits at the screen's edge).
 */
@Composable
fun ConnectionBanner(inDownloads: Boolean = false, modifier: Modifier = Modifier) {
    val status by ConnectionManager.status.collectAsState()
    val recovered by ConnectionManager.recoveredAt.collectAsState()
    val phase by AppSession.phase.collectAsState()
    val content: Triple<ImageVector, String, String>? = when (status) {
        Status.Offline -> Triple(Icons.Outlined.WifiOff, "You're Offline",
                                 if (inDownloads) "Downloads play without a connection." else "Downloads are still available.")
        is Status.Reconnecting -> Triple(Icons.Outlined.CloudOff, "Can't Reach Server", "")
        Status.Online -> if (recovered != null || (inDownloads && phase == AppSession.Phase.Ready)) {
            Triple(Icons.Outlined.CloudDone, "Back Online", if (inDownloads) "Close Downloads to return to the app." else "Connected to your server.")
        } else null
    }
    AnimatedVisibility(content != null, enter = expandVertically(), exit = shrinkVertically()) {
        val (icon, title, subtitle) = content ?: return@AnimatedVisibility
        Surface(color = MaterialTheme.colorScheme.surfaceContainerHigh, modifier = Modifier.fillMaxWidth()) {
            Row(modifier.padding(horizontal = 16.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                Icon(icon, null, tint = if (status == Status.Online) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error)
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text(title, style = MaterialTheme.typography.titleSmall)
                    if (status is Status.Reconnecting) RetryCountdown(status)
                    else Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                when (val s = status) {
                    Status.Offline -> if (!inDownloads) {
                        FilledTonalButton(onClick = { ConnectionManager.showsOfflineDownloads.value = true }) { Text("Downloads") }
                    }
                    is Status.Reconnecting -> if (s.nextRetryAt != null) {
                        FilledTonalButton(onClick = { ConnectionManager.retryNow() }) { Text("Retry") }
                    } else CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp)
                    Status.Online -> Unit
                }
            }
        }
    }
}

/** "Retrying in 8 s" / "Reconnecting…" / "Waiting for a network connection". */
@Composable
fun RetryCountdown(status: Status) {
    val style = MaterialTheme.typography.bodySmall
    val color = MaterialTheme.colorScheme.onSurfaceVariant
    when (status) {
        is Status.Reconnecting -> {
            val next = status.nextRetryAt
            if (next == null) {
                Text("Reconnecting…", style = style, color = color)
            } else {
                var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
                LaunchedEffect(next) {
                    while (true) {
                        now = System.currentTimeMillis()
                        delay(1_000)
                    }
                }
                val seconds = ((next - now + 999) / 1000).coerceAtLeast(1)
                Text("Retrying in $seconds s", style = style, color = color)
            }
        }
        Status.Offline -> Text("Waiting for a network connection", style = style, color = color)
        Status.Online -> Unit
    }
}

package com.strmr.mobile.ui.settings

import android.content.Intent
import android.net.Uri
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.strmr.tv.adaptive.ThroughputRules
import com.strmr.tv.adaptive.ThroughputSource
import com.strmr.tv.adaptive.ThroughputStore
import com.strmr.tv.api.ApiClient
import com.strmr.tv.api.adaptivePlaybackEnabled
import com.strmr.tv.api.checkHealth
import com.strmr.tv.app.AppSession
import kotlinx.coroutines.delay
import com.strmr.tv.auth.ServerAddress
import com.strmr.tv.ui.settings.SpeedTestState
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import kotlinx.coroutines.launch

/** Whether the server answered the health check. */
private sealed interface Health {
    data object Checking : Health
    data object Connected : Health
    data class Unreachable(val message: String) : Health
}

/**
 * Settings › Server: the address and its reachability, remote access (iroh) and its
 * reconnect, the speed test with the adaptive playback estimate, the server's web UI and
 * Change Server (which signs out, as on the TV).
 */
@Composable
fun ServerSettingsPage() {
    val session = AppSession
    val server by session.serverUrl.collectAsState()
    val remote by session.isRemote.collectAsState()
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    var health by remember { mutableStateOf<Health>(Health.Checking) }
    var reconnecting by remember { mutableStateOf(false) }
    var confirmChange by remember { mutableStateOf(false) }
    fun check() {
        health = Health.Checking
        scope.launch {
            health = try {
                ApiClient.checkHealth(ApiClient.baseUrl)
                Health.Connected
            } catch (e: Exception) {
                Health.Unreachable(e.message ?: "Unreachable")
            }
        }
    }
    LaunchedEffect(Unit) { check() }
    LaunchedEffect(Unit) { SpeedTestState.adaptiveEnabled = runCatching { ApiClient.adaptivePlaybackEnabled() }.getOrNull() }

    SettingsPage("Server") {
        SettingsSection("Connection") {
            ValueRow("Address", if (remote) "Remote access (iroh)" else server?.let { ServerAddress.display(it) } ?: "Not connected")
            val detail = when (val h = health) {
                Health.Checking -> "Checking…"
                Health.Connected -> "Connected"
                is Health.Unreachable -> h.message
            }
            SettingsRow("Status", detail, trailing = {
                when (val h = health) {
                    Health.Checking -> CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp)
                    Health.Connected -> Icon(Icons.Filled.CheckCircle, "Connected", tint = MaterialTheme.colorScheme.primary)
                    is Health.Unreachable -> Icon(Icons.Filled.Warning, h.message, tint = MaterialTheme.colorScheme.error)
                }
            }) { check() }
        }
        if (remote) {
            SettingsSection("Remote Access", "Remote access reaches the server through an encrypted peer-to-peer link (iroh) when it isn't on this network.") {
                ValueRow("Remote Access", "In use")
                SettingsRow("Reconnect", if (reconnecting) "Reconnecting…" else "Open a fresh link to the server", enabled = !reconnecting) {
                    reconnecting = true
                    scope.launch {
                        session.reconnectRemote()
                        // It reconnects in the background; give it a moment before checking.
                        delay(3_000)
                        reconnecting = false
                        check()
                    }
                }
            }
        }
        SettingsSection(
            "Speed",
            "Test Connection Speed downloads test data from the server; with adaptive playback on, searches use the result to pick a release that fits.",
        ) { SpeedRows() }
        SettingsSection {
            val admin = server?.newBuilder()?.addPathSegment("admin")?.build()
            if (!remote && admin != null) {
                SettingsRow("Server Web UI", icon = Icons.AutoMirrored.Filled.OpenInNew) {
                    runCatching { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(admin.toString()))) }
                }
            }
            SettingsRow("Change Server…") { confirmChange = true }
        }
    }
    if (confirmChange) {
        AlertDialog(
            onDismissRequest = { confirmChange = false },
            title = { Text("Change server?") },
            text = { Text("You'll sign in again on the new server.") },
            confirmButton = { TextButton(onClick = { confirmChange = false; session.changeServer() }) { Text("Change Server") } },
            dismissButton = { TextButton(onClick = { confirmChange = false }) { Text("Cancel") } },
        )
    }
}

@Composable
private fun SpeedRows() {
    val status = SpeedTestState.status
    ValueRow(
        "Test Connection Speed",
        if (status is SpeedTestState.Status.Done) "${"%.1f".format(status.mbps)} Mbps" else "",
        when (status) {
            SpeedTestState.Status.Idle -> "Measure this device's speed to the server"
            SpeedTestState.Status.Running -> "Testing…"
            is SpeedTestState.Status.Done -> "Measured ${time(status.at)} in ${"%.1f".format(status.elapsedMs / 1000.0)} s"
            is SpeedTestState.Status.Failed -> status.message
        },
    ) { SpeedTestState.run() }
    val enabled = SpeedTestState.adaptiveEnabled
    @Suppress("UNUSED_VARIABLE") val revision = ThroughputStore.revision // re-read after a save
    val estimate = ThroughputStore.stored()
    val fresh = ThroughputRules.isFresh(estimate, System.currentTimeMillis())
    ValueRow(
        "Adaptive Playback Estimate",
        estimate?.let { "${ThroughputRules.headerValue(it.measuredMbps)} Mbps" }.orEmpty(),
        when {
            enabled == false -> "Adaptive playback is off"
            estimate == null -> "Not measured yet on this connection"
            else -> (if (estimate.source == ThroughputSource.MANUAL) "Speed test" else "Automatic test") +
                " · ${time(Instant.ofEpochSecond(estimate.measuredAt))}" + if (fresh) "" else " · expired, not sent"
        },
    )
}

internal fun time(at: Instant): String =
    at.atZone(ZoneId.systemDefault()).format(DateTimeFormatter.ofLocalizedDateTime(FormatStyle.SHORT))

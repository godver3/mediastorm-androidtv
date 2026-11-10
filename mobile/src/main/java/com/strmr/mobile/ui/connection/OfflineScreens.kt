package com.strmr.mobile.ui.connection

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.CloudOff
import androidx.compose.material.icons.outlined.DownloadForOffline
import androidx.compose.material.icons.outlined.NetworkCheck
import androidx.compose.material.icons.outlined.WifiOff
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.strmr.mobile.connection.ConnectionManager
import com.strmr.mobile.connection.NetworkMonitor
import com.strmr.mobile.ui.downloads.DownloadsScreen
import com.strmr.tv.app.AppSession
import kotlinx.coroutines.delay

/**
 * The startup error on the phone (the iPhone app's `MobileOfflineView`): the device is offline
 * or the server didn't answer. It retries by itself ([ConnectionManager]) and offers the
 * downloads meanwhile, instead of stopping at "Couldn't Connect".
 */
@Composable
fun OfflineScreen(message: String) {
    val offline by NetworkMonitor.isDeviceOffline.collectAsState()
    val status by ConnectionManager.status.collectAsState()
    val remote by AppSession.isRemote.collectAsState()
    val server by AppSession.serverUrl.collectAsState()
    var settings by remember { mutableStateOf(false) }
    if (settings) return ConnectionSettingsPage(onBack = { settings = false })
    Box(Modifier.fillMaxSize().statusBarsPadding()) {
        IconButton(onClick = { settings = true }, modifier = Modifier.align(Alignment.TopEnd).padding(8.dp)) {
            Icon(Icons.Outlined.NetworkCheck, "Connection")
        }
        Column(
            Modifier.align(Alignment.Center).padding(32.dp), horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Icon(if (offline) Icons.Outlined.WifiOff else Icons.Outlined.CloudOff, null, Modifier.size(48.dp),
                 tint = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(
                if (offline) "You're Offline" else "Can't Reach " + if (remote) "Your Server" else server?.host ?: "the Server",
                style = MaterialTheme.typography.headlineSmall, textAlign = TextAlign.Center,
            )
            Text(
                if (offline) "Connect to Wi-Fi or mobile data. Your downloads are still available."
                else "The server didn't answer. MediaStorm keeps trying; your downloads are still available.",
                textAlign = TextAlign.Center, color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            if (!offline && message.isNotEmpty()) {
                Text(message, style = MaterialTheme.typography.bodySmall, textAlign = TextAlign.Center,
                     color = MaterialTheme.colorScheme.outline)
            }
            RetryCountdown(status)
            Button(onClick = { ConnectionManager.showsOfflineDownloads.value = true }, modifier = Modifier.padding(top = 8.dp)) {
                Icon(Icons.Outlined.DownloadForOffline, null)
                Text("Open Downloads", Modifier.padding(start = 8.dp))
            }
            OutlinedButton(onClick = { ConnectionManager.retryNow() },
                           enabled = !(status is ConnectionManager.Status.Reconnecting &&
                               (status as ConnectionManager.Status.Reconnecting).nextRetryAt == null)) { Text("Try Again") }
            TextButton(onClick = { AppSession.changeServer() }) { Text("Change Server") }
        }
    }
}

/**
 * "Connecting…", with a way into Downloads when it takes a while or the device has no network
 * (the iPhone app's `MobileStartingView`).
 */
@Composable
fun StartingScreen() {
    val offline by NetworkMonitor.isDeviceOffline.collectAsState()
    var slow by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        delay(8_000)
        slow = true
    }
    Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.Center, horizontalAlignment = Alignment.CenterHorizontally) {
        CircularProgressIndicator()
        Text("Connecting…", Modifier.padding(top = 16.dp), color = MaterialTheme.colorScheme.onSurfaceVariant)
        if (slow || offline) {
            OutlinedButton(onClick = { ConnectionManager.showsOfflineDownloads.value = true }, modifier = Modifier.padding(top = 24.dp)) {
                Text("Use Downloads Meanwhile")
            }
        }
    }
}

/** Downloads over the startup screens or the signed-in app, without a server; its banner says when the server is back. */
@Composable
fun OfflineDownloads() {
    val shown by ConnectionManager.showsOfflineDownloads.collectAsState()
    if (!shown) return
    Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        androidx.activity.compose.BackHandler { ConnectionManager.showsOfflineDownloads.value = false }
        DownloadsScreen(
            asPage = true, onBack = { ConnectionManager.showsOfflineDownloads.value = false },
            topInset = { ConnectionBanner(inDownloads = true) },
        )
    }
}

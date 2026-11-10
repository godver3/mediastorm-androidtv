package com.strmr.mobile.ui.connection

import android.content.Intent
import android.provider.Settings
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
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
import com.strmr.mobile.connection.ConnectionManager
import com.strmr.mobile.connection.ConnectionSelector
import com.strmr.mobile.connection.NetworkMonitor
import com.strmr.mobile.connection.ReconnectJournal
import com.strmr.mobile.connection.WifiSsid
import com.strmr.mobile.ui.downloads.DownloadPage
import com.strmr.mobile.ui.downloads.SectionHeader
import com.strmr.mobile.ui.shell.LocalNavigator
import com.strmr.tv.app.AppSession
import com.strmr.tv.app.NetworkRoutes
import com.strmr.tv.remote.RemoteAccess
import java.text.DateFormat
import java.util.Date
import kotlinx.coroutines.launch

/**
 * Settings › Connection (the iPhone app's `MobileConnectionSettingsView`): how the phone
 * reaches the server now (home network, remote access or a typed address), the phone's own
 * network, the server's home-network settings that drive switching, Check Connection and
 * Reconnect, and recent connection events. Switching has no client-side mode: it follows the
 * server's "Network URL Switching" settings. [onBack] null: pushed in the signed-in shell.
 */
@Composable
fun ConnectionSettingsPage(onBack: (() -> Unit)? = null) {
    val back = onBack ?: LocalNavigator.current.let { n -> { n.pop(); Unit } }
    val status by ConnectionManager.status.collectAsState()
    val path by NetworkMonitor.path.collectAsState()
    val offline by NetworkMonitor.isDeviceOffline.collectAsState()
    val settings by ConnectionSelector.settings.collectAsState()
    val ssid by ConnectionSelector.lastSsid.collectAsState()
    val home by ConnectionSelector.isHomeNetwork.collectAsState()
    val remote by AppSession.isRemote.collectAsState()
    val homeDirect by NetworkRoutes.isHomeDirect.collectAsState()
    val server by AppSession.serverUrl.collectAsState()
    val version by AppSession.serverVersion.collectAsState()
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    var checking by remember { mutableStateOf(false) }
    var result by remember { mutableStateOf<String?>(null) }
    var journal by remember { mutableStateOf(ReconnectJournal.entries.reversed()) }
    var showEvents by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { ConnectionSelector.refreshSettings() }
    val paired = RemoteAccess.pairing != null

    DownloadPage("Connection", onBack = back) { padding ->
        LazyColumn(Modifier.fillMaxSize(), contentPadding = padding) {
            item { SectionHeader("Server") }
            item {
                Row2("Status", when (status) {
                    ConnectionManager.Status.Online -> "Connected"
                    ConnectionManager.Status.Offline -> "Offline"
                    is ConnectionManager.Status.Reconnecting -> "Reconnecting"
                })
            }
            item {
                Row2("Connection", when {
                    homeDirect -> "Home Network"
                    remote -> "Remote Access"
                    settings?.isConfigured == true && server == settings?.homeUrl -> "Home Address"
                    settings?.isConfigured == true && server == settings?.remoteUrl -> "Away Address"
                    else -> "Direct"
                })
            }
            server?.let { item { Row2("Address", if (remote) "Remote access tunnel" else it.toString().trimEnd('/')) } }
            version?.version?.let { item { Row2("Server Version", it) } }

            item { SectionHeader("This Device") }
            item { Row2("Network", if (offline) "Offline" else path.iface.title + if (path.metered) " · metered" else "") }
            if (path.iface == NetworkMonitor.Interface.WIFI) item { Row2("Wi-Fi Name", ssid ?: "Unavailable") }
            item {
                when {
                    !WifiSsid.isAllowed -> Action("Allow Wi-Fi Name Detection") { WifiSsid.requestAccess() }
                    !WifiSsid.locationOn -> Action("Turn On Location") {
                        context.startActivity(Intent(Settings.ACTION_LOCATION_SOURCE_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                    }
                    else -> Unit
                }
            }
            item {
                Footer("Android shows the Wi-Fi name only with location access, which MediaStorm uses to tell your home network " +
                    "from others. " + if (paired) "Without it, the home address is still tried and checked before use."
                    else "Without it, the address in use is kept while on Wi-Fi.")
            }

            item { SectionHeader("Home Network") }
            val s = settings
            if (s != null && s.homeWifiSsid.isNotEmpty()) {
                item { Row2("Home Wi-Fi", s.homeWifiSsid) }
                item { Row2("Home Address", s.homeUrl?.toString()?.trimEnd('/') ?: "Not Set") }
                item { Row2("Away Address", s.remoteUrl?.toString()?.trimEnd('/') ?: "Not Set") }
                item { Row2("Set For", s.source.title) }
                home?.let { item { Row2("On Home Network", if (it) "Yes" else "No") } }
            } else {
                item { Row2("Not set up", "") }
            }
            item {
                Footer((if (paired) "On the home network the phone talks to your server directly once it confirms the address belongs " +
                    "to the paired server; elsewhere it uses remote access. " else "On the home Wi-Fi the home address is used, " +
                    "elsewhere the away address. ") + "Set in the web UI under Settings → Server → Network URL Switching.")
            }

            if (paired) {
                item { SectionHeader("Remote Access") }
                item { Row2("Remote Access", if (homeDirect) "Standing by (on home network)" else if (status == ConnectionManager.Status.Online) "Connected" else "Reconnecting…") }
                RemoteAccess.pairing?.connectionCode?.let { code -> item { Row2("Paired With", code.take(13) + "…") } }
                item { Footer("Remote access stays in use away from home and never falls back to an unverified local address.") }
            }

            item { SectionHeader("Actions") }
            item {
                ListItem(
                    headlineContent = { Text("Check Connection", color = MaterialTheme.colorScheme.primary) },
                    supportingContent = result?.let { { Text(it) } },
                    trailingContent = { if (checking) CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp) },
                    modifier = Modifier.clickable(enabled = !checking) {
                        scope.launch {
                            checking = true
                            result = null
                            ConnectionSelector.select("retry")
                            val ok = ConnectionManager.checkNow()
                            journal = ReconnectJournal.entries.reversed()
                            result = if (ok) "The server answered." else "The server didn't answer. Retrying automatically."
                            checking = false
                        }
                    },
                )
            }
            item { Action("Reconnect") { ConnectionManager.retryNow() } }
            item { Action("Change Server…") { AppSession.changeServer() } }

            item { SectionHeader("Recent Events") }
            item { Action(if (showEvents) "Hide ${journal.size} events" else "Show ${journal.size} events") { showEvents = !showEvents } }
            if (showEvents) {
                journal.take(40).forEach { e ->
                    item {
                        val time = DateFormat.getDateTimeInstance(DateFormat.SHORT, DateFormat.MEDIUM).format(Date(e.timestamp))
                        val details = e.details?.entries?.sortedBy { it.key }?.joinToString(" ") { "${it.key}=${it.value}" }
                        ListItem(headlineContent = { Text(e.event, style = MaterialTheme.typography.bodyMedium) },
                                 supportingContent = { Text(listOfNotNull(time, details).joinToString(" · ")) })
                    }
                }
            }
        }
    }
}

@Composable
private fun Row2(title: String, value: String) {
    ListItem(headlineContent = { Text(title) }, trailingContent = { Text(value, color = MaterialTheme.colorScheme.onSurfaceVariant) })
}

@Composable
private fun Action(title: String, onClick: () -> Unit) {
    ListItem(headlineContent = { Text(title, color = MaterialTheme.colorScheme.primary) }, modifier = Modifier.clickable(onClick = onClick))
}

@Composable
private fun Footer(text: String) {
    Text(text, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
         modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp))
}


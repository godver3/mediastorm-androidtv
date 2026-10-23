package com.strmr.tv.ui.onboarding

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Home
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.tv.material3.Icon
import androidx.tv.material3.ListItem
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.OutlinedButton
import androidx.tv.material3.Text
import com.strmr.tv.app.AppSession
import com.strmr.tv.app.DebugPrefill
import com.strmr.tv.auth.DiscoveredServer
import com.strmr.tv.auth.ServerAddress
import com.strmr.tv.auth.ServerBrowser
import com.strmr.tv.remote.RemoteAccess
import com.strmr.tv.ui.Accent
import kotlinx.coroutines.launch

/**
 * First run (and Settings → Change Server): pick a server found on the LAN, or type the
 * backend address; either is checked before moving on.
 */
@Composable
fun ServerConnectScreen() {
    val session = AppSession
    val current by session.serverUrl.collectAsState()
    val browser = remember { ServerBrowser() }
    val servers by browser.servers.collectAsState()
    var address by rememberSaveable {
        mutableStateOf(current?.let { ServerAddress.display(it) } ?: DebugPrefill.serverUrl ?: "")
    }
    var error by remember { mutableStateOf<String?>(null) }
    var connecting by remember { mutableStateOf(false) }
    var connectingId by remember { mutableStateOf<String?>(null) }
    var code by remember { mutableStateOf("") }
    var pairing by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    val resolved = ServerAddress.normalize(address)
    // A known address lands on Connect; an empty one on the field (which opens the IME on select).
    val connectFocus = remember { FocusRequester() }
    val fieldFocus = remember { FocusRequester() }
    LaunchedEffect(Unit) { runCatching { (if (resolved != null) connectFocus else fieldFocus).requestFocus() } }

    DisposableEffect(Unit) {
        browser.start()
        onDispose { browser.stop() }
    }
    if (current != null) BackHandler { session.cancelServerChange() }

    fun connect() {
        if (connecting || resolved == null) return
        connecting = true
        error = null
        scope.launch {
            try {
                session.connect(address)
            } catch (e: Exception) {
                error = e.message
            } finally {
                connecting = false
            }
        }
    }

    fun pair() {
        if (pairing || connecting || RemoteAccess.normalize(code) == null) return
        pairing = true
        error = null
        scope.launch {
            try {
                session.pair(code)
            } catch (e: Exception) {
                error = e.message
            } finally {
                pairing = false
            }
        }
    }

    fun connect(server: DiscoveredServer) {
        if (connecting) return
        connecting = true
        connectingId = server.id
        error = null
        scope.launch {
            try {
                session.connect(server)
            } catch (e: Exception) {
                error = e.message
            } finally {
                connecting = false
                connectingId = null
            }
        }
    }

    OnboardingLayout(
        title = "Connect to Your Server",
        subtitle = "Enter the address of your MediaStorm server. Local addresses use port ${ServerAddress.DEFAULT_PORT} unless you add one.",
    ) {
        if (servers.isNotEmpty()) {
            DiscoveredServerList(servers, connectingId, enabled = !connecting, onPick = ::connect)
        }
        FormField(
            value = address,
            onValueChange = { address = it; error = null },
            label = "Server Address",
            placeholder = "192.168.1.10 or media.example.com",
            keyboardType = KeyboardType.Uri,
            imeAction = ImeAction.Go,
            onSubmit = ::connect,
            modifier = Modifier.focusRequester(fieldFocus),
        )
        if (resolved != null) {
            Text("Connects to ${resolved.toString().trimEnd('/')}", style = MaterialTheme.typography.bodySmall, color = Color.White.copy(alpha = 0.6f))
        }
        error?.let { OnboardingError(it) }
        Row(horizontalArrangement = Arrangement.spacedBy(20.dp)) {
            BusyButton("Connect", busy = connecting && connectingId == null, enabled = resolved != null,
                       modifier = Modifier.focusRequester(connectFocus), onClick = ::connect)
            if (current != null) {
                OutlinedButton(onClick = { session.cancelServerChange() }, enabled = !connecting && !pairing) { Text("Cancel") }
            }
        }
        // Away from home: the server's admin UI (Remote Access) issues a connection code.
        Text("Away From Home", style = MaterialTheme.typography.titleSmall, color = Color.White.copy(alpha = 0.6f),
             modifier = Modifier.fillMaxWidth().padding(top = 12.dp))
        FormField(
            value = code,
            onValueChange = { code = it; error = null },
            label = "Connection Code",
            placeholder = "mshost-ABC123-DEF456-GHJ789",
            imeAction = ImeAction.Go,
            onSubmit = ::pair,
        )
        BusyButton("Connect with Code", busy = pairing, enabled = RemoteAccess.normalize(code) != null && !connecting, onClick = ::pair)
    }
}

/** Servers advertising `_mediastorm._tcp` on this network. */
@Composable
private fun DiscoveredServerList(servers: List<DiscoveredServer>, busyId: String?, enabled: Boolean, onPick: (DiscoveredServer) -> Unit) {
    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text("On Your Network", style = MaterialTheme.typography.titleSmall, color = Color.White.copy(alpha = 0.6f))
        servers.forEach { server ->
            ListItem(
                selected = false,
                enabled = enabled,
                onClick = { onPick(server) },
                headlineContent = { Text(server.name) },
                supportingContent = server.version?.let { { Text("MediaStorm $it") } },
                leadingContent = { Icon(Icons.Filled.Home, contentDescription = null) },
                trailingContent = if (busyId == server.id) {
                    { CircularProgressIndicator(Modifier.size(18.dp), color = Accent, strokeWidth = 2.dp) }
                } else null,
            )
        }
    }
}

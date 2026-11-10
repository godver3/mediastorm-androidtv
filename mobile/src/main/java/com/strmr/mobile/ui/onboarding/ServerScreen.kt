package com.strmr.mobile.ui.onboarding

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Dns
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.shape.RoundedCornerShape
import com.strmr.tv.app.AppSession
import com.strmr.tv.app.DebugPrefill
import com.strmr.tv.auth.DiscoveredServer
import com.strmr.tv.auth.ServerAddress
import com.strmr.tv.auth.ServerBrowser
import com.strmr.tv.remote.RemoteAccess
import kotlinx.coroutines.launch

/**
 * Connect: servers found on this network, an address field, and a connection code for away
 * from home (the server's admin UI → Remote Access issues one).
 */
@Composable
fun ServerScreen() {
    val session = AppSession
    val current by session.serverUrl.collectAsState()
    val browser = remember { ServerBrowser() }
    val servers by browser.servers.collectAsState()
    var address by rememberSaveable { mutableStateOf(current?.let { ServerAddress.display(it) } ?: DebugPrefill.serverUrl ?: "") }
    var code by rememberSaveable { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }
    var busy by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()
    val resolved = ServerAddress.normalize(address)
    val canCancel = current != null || session.canCancelServerChange

    DisposableEffect(Unit) {
        browser.start()
        onDispose { browser.stop() }
    }
    if (canCancel) BackHandler { session.cancelServerChange() }

    fun run(tag: String, action: suspend () -> Unit) {
        if (busy != null) return
        busy = tag
        error = null
        scope.launch {
            try {
                action()
            } catch (e: Exception) {
                error = e.message
            } finally {
                busy = null
            }
        }
    }
    fun connect() { if (resolved != null) run("address") { session.connect(address) } }
    fun connect(server: DiscoveredServer) = run(server.id) { session.connect(server) }
    fun pair() { if (RemoteAccess.normalize(code) != null) run("code") { session.pair(code) } }

    OnboardingScaffold(
        title = "Connect to Your Server",
        subtitle = "Enter the address of your MediaStorm server. Local addresses use port ${ServerAddress.DEFAULT_PORT} unless you add one.",
    ) {
        if (servers.isNotEmpty()) {
            SectionLabel("On Your Network")
            Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp))) {
                servers.forEachIndexed { i, server ->
                    if (i > 0) HorizontalDivider()
                    ListItem(
                        headlineContent = { Text(server.name) },
                        supportingContent = server.version?.let { { Text("MediaStorm $it") } },
                        leadingContent = { Icon(Icons.Outlined.Dns, contentDescription = null) },
                        trailingContent = if (busy == server.id) {
                            { CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp) }
                        } else null,
                        colors = ListItemDefaults.colors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh),
                        modifier = Modifier.clickableRow(enabled = busy == null) { connect(server) },
                    )
                }
            }
        }
        OutlinedTextField(
            value = address,
            onValueChange = { address = it; error = null },
            label = { Text("Server Address") },
            placeholder = { Text("192.168.1.10 or media.example.com") },
            singleLine = true,
            supportingText = resolved?.let { { Text("Connects to ${it.toString().trimEnd('/')}") } },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri, imeAction = ImeAction.Go, autoCorrectEnabled = false),
            keyboardActions = KeyboardActions(onGo = { connect() }),
            modifier = Modifier.fillMaxWidth(),
        )
        BusyButton("Connect", busy = busy == "address", enabled = resolved != null && busy == null, onClick = ::connect)
        error?.let { ErrorText(it) }
        SectionLabel("Away From Home", Modifier.padding(top = 8.dp))
        OutlinedTextField(
            value = code,
            onValueChange = { code = it; error = null },
            label = { Text("Connection Code") },
            placeholder = { Text("mshost-ABC123-DEF456-GHJ789") },
            singleLine = true,
            keyboardOptions = KeyboardOptions(
                capitalization = KeyboardCapitalization.Characters, imeAction = ImeAction.Go, autoCorrectEnabled = false,
            ),
            keyboardActions = KeyboardActions(onGo = { pair() }),
            modifier = Modifier.fillMaxWidth(),
        )
        BusyButton(
            "Connect with Code", busy = busy == "code", tonal = true,
            enabled = RemoteAccess.normalize(code) != null && busy == null, onClick = ::pair,
        )
        if (canCancel) {
            TextButton(onClick = { session.cancelServerChange() }, enabled = busy == null) { Text("Cancel") }
        }
    }
}

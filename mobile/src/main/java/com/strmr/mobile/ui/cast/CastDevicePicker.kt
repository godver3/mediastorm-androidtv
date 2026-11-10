package com.strmr.mobile.ui.cast

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Cast
import androidx.compose.material.icons.filled.CastConnected
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.filled.Tv
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.strmr.mobile.cast.ActiveCast
import com.strmr.mobile.cast.CastController
import com.strmr.mobile.cast.CastDevice
import kotlinx.coroutines.launch

/**
 * Cast To: the Chromecasts and DLNA TVs on the network in one list (the iPhone app's picker),
 * the cast that is running with Stop Casting, and a renderer added by its address. Discovery
 * runs while the sheet is up.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CastDevicePicker(onPick: (CastDevice) -> Unit, onShowRemote: () -> Unit, onDismiss: () -> Unit) {
    val sheet = rememberModalBottomSheetState(skipPartiallyExpanded = false)
    val chromecasts by CastController.chromecasts.collectAsState()
    val active = CastController.session
    DisposableEffect(Unit) {
        CastController.startDiscovery()
        onDispose { CastController.stopDiscovery() }
    }
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheet) {
        Row(Modifier.fillMaxWidth().padding(start = 24.dp, end = 12.dp), verticalAlignment = Alignment.CenterVertically) {
            Text("Cast To", style = MaterialTheme.typography.titleLarge, modifier = Modifier.weight(1f))
            IconButton(onClick = { CastController.refreshRenderers() }, enabled = !CastController.isSearching) {
                Icon(Icons.Filled.Refresh, contentDescription = "Refresh")
            }
        }
        LazyColumn(Modifier.fillMaxWidth()) {
            if (active != null && active.phase == ActiveCast.Phase.Active) {
                header("Now Casting")
                item {
                    ListItem(
                        headlineContent = { Text(active.title, maxLines = 1) },
                        supportingContent = { Text("Playing on ${active.device.name}") },
                        leadingContent = { Icon(Icons.Filled.CastConnected, null, tint = MaterialTheme.colorScheme.primary) },
                        trailingContent = { Icon(Icons.Filled.ChevronRight, null) },
                        modifier = Modifier.clickable(onClick = onShowRemote),
                        colors = ListItemDefaults.colors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
                    )
                }
                item {
                    ListItem(
                        headlineContent = { Text("Stop Casting", color = MaterialTheme.colorScheme.error) },
                        leadingContent = { Icon(Icons.Filled.Stop, null, tint = MaterialTheme.colorScheme.error) },
                        modifier = Modifier.clickable { CastController.stop() },
                        colors = ListItemDefaults.colors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
                    )
                }
            }
            devices(
                "Chromecast", chromecasts, active,
                empty = "No Chromecasts found. They must be on the same Wi-Fi network as this device.", onPick = onPick,
            )
            devices(
                "DLNA TVs", CastController.renderers, active, searching = CastController.isSearching,
                empty = if (CastController.isSearching) null else "No DLNA TVs found. Add one by its address below.", onPick = onPick,
            )
            item { AddByAddress(onPick) }
        }
    }
}

private fun LazyListScope.header(title: String, searching: Boolean = false) = item(key = "h:$title") { SectionHeader(title, searching) }

@Composable
private fun SectionHeader(title: String, searching: Boolean = false) {
    Row(Modifier.padding(start = 24.dp, end = 24.dp, top = 16.dp, bottom = 4.dp), verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(title, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.SemiBold)
        if (searching) CircularProgressIndicator(Modifier.size(14.dp), strokeWidth = 2.dp)
    }
}

private fun LazyListScope.devices(
    title: String, devices: List<CastDevice>, active: ActiveCast?, empty: String?, searching: Boolean = false,
    onPick: (CastDevice) -> Unit,
) {
    header(title, searching)
    items(devices, key = { it.id }) { device ->
        ListItem(
            headlineContent = { Text(device.name) },
            supportingContent = { Text(device.detail) },
            leadingContent = {
                Icon(if (device.kind == CastDevice.Kind.GOOGLE_CAST) Icons.Filled.Cast else Icons.Filled.Tv, null,
                     tint = MaterialTheme.colorScheme.primary)
            },
            trailingContent = { if (active?.device?.id == device.id) Icon(Icons.Filled.Check, "Casting", tint = MaterialTheme.colorScheme.primary) },
            modifier = Modifier.clickable { onPick(device) },
            colors = ListItemDefaults.colors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
        )
    }
    if (devices.isEmpty() && empty != null) {
        item(key = "e:$title") {
            Text(empty, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                 modifier = Modifier.padding(horizontal = 24.dp, vertical = 8.dp))
        }
    }
}

/** For a DLNA TV that isn't listed: its IP address, probed by unicast SSDP. */
@Composable
private fun AddByAddress(onPick: (CastDevice) -> Unit) {
    val scope = rememberCoroutineScope()
    var address by remember { mutableStateOf("") }
    var adding by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    fun add() {
        val host = address.trim()
        if (host.isEmpty() || adding) return
        adding = true
        error = null
        scope.launch {
            try {
                val device = CastController.addRenderer(host)
                address = ""
                onPick(device)
            } catch (e: Exception) {
                error = e.message ?: "Couldn't add that TV."
            } finally {
                adding = false
            }
        }
    }
    HorizontalDivider(Modifier.padding(top = 12.dp))
    SectionHeader("Add by Address")
    Row(Modifier.fillMaxWidth().padding(horizontal = 24.dp), verticalAlignment = Alignment.CenterVertically) {
        OutlinedTextField(
            value = address, onValueChange = { address = it }, singleLine = true, placeholder = { Text("192.168.1.42") },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal, imeAction = ImeAction.Go),
            keyboardActions = KeyboardActions(onGo = { add() }),
            isError = error != null, modifier = Modifier.weight(1f),
        )
        if (adding) {
            CircularProgressIndicator(Modifier.padding(start = 16.dp).size(24.dp))
        } else {
            TextButton(onClick = ::add, enabled = address.isNotBlank(), modifier = Modifier.padding(start = 8.dp)) { Text("Add") }
        }
    }
    Text(
        error ?: "For a DLNA TV that isn't listed, enter its IP address on your network.",
        style = MaterialTheme.typography.bodySmall,
        color = if (error != null) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(start = 24.dp, end = 24.dp, top = 6.dp, bottom = 24.dp),
    )
}

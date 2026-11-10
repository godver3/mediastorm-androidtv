package com.strmr.mobile.ui.live

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.FiberManualRecord
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.PlayCircle
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.SettingsInputAntenna
import androidx.compose.material3.Checkbox
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.strmr.tv.live.LiveSourceChoice
import com.strmr.tv.live.LiveStore
import com.strmr.tv.live.LiveStremioStream

/** The playlist source (All Sources or one provider), shown when there is more than one. */
@Composable
fun LiveSourceMenu(store: LiveStore) {
    if (store.sources.size <= 1) return
    var open by remember { mutableStateOf(false) }
    IconButton(onClick = { open = true }) {
        Icon(Icons.Filled.SettingsInputAntenna, contentDescription = "Source",
             tint = if (store.sourceId != null) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant)
    }
    DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
        val options = listOf<Pair<String?, String>>(null to "All Sources") + store.sources.map { it.id to it.name }
        options.forEach { (id, name) ->
            DropdownMenuItem(
                text = { Text(name) },
                leadingIcon = { if (store.sourceId == id) Icon(Icons.Filled.Check, null) },
                onClick = { open = false; store.sourceId = id },
            )
        }
    }
}

/** Hidden channels, refresh, Recordings; headed by the channel count. */
@Composable
fun LiveMoreMenu(store: LiveStore, onRefresh: () -> Unit, onRecordings: () -> Unit) {
    var open by remember { mutableStateOf(false) }
    IconButton(onClick = { open = true }) { Icon(Icons.Filled.MoreVert, contentDescription = "More Options") }
    DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
        store.total?.let {
            Text("$it channels", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant,
                 modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp))
            HorizontalDivider()
        }
        DropdownMenuItem(
            text = { Text("Show Hidden Channels") },
            trailingIcon = { Checkbox(checked = store.showHidden, onCheckedChange = null) },
            onClick = { store.showHidden = !store.showHidden },
        )
        DropdownMenuItem(
            text = { Text(if (store.epgEnabled == true) "Refresh Guide & Channels" else "Refresh Channels") },
            leadingIcon = { Icon(Icons.Filled.Refresh, null) },
            onClick = { open = false; onRefresh() },
        )
        DropdownMenuItem(
            text = { Text("Recordings") },
            leadingIcon = { Icon(Icons.Filled.FiberManualRecord, null) },
            onClick = { open = false; onRecordings() },
        )
    }
}

/** Choose Source for a Stremio channel: the addon's streams in its order; picking one tunes it. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LiveSourceSheet(choice: LiveSourceChoice, onDismiss: () -> Unit, onPick: (LiveStremioStream) -> Unit) {
    ModalBottomSheet(onDismissRequest = onDismiss) {
        LazyColumn(Modifier.fillMaxWidth().padding(bottom = 16.dp)) {
            item {
                Row(Modifier.padding(horizontal = 16.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    ChannelLogo(choice.channel, 72.dp, 40.dp, namesChannel = false)
                    Column {
                        Text(choice.channel.name, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                        Text("Choose stream source", color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
            items(choice.streams, key = { it.index }) { stream ->
                ListItem(
                    headlineContent = { Text(stream.label, maxLines = 3) },
                    leadingContent = { Icon(Icons.Filled.PlayCircle, null) },
                    colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                    modifier = Modifier.clickable { onPick(stream) },
                )
            }
        }
    }
}

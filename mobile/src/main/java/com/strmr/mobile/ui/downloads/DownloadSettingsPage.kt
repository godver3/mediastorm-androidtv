package com.strmr.mobile.ui.downloads

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.strmr.mobile.downloads.DownloadSettings
import com.strmr.mobile.downloads.Downloads
import com.strmr.mobile.ui.shell.LocalNavigator

/**
 * Settings › Downloads (also the Downloads list's gear; the iPhone app's
 * `MobileDownloadSettingsView`): Wi-Fi only, simultaneous downloads, delete watched, the
 * storage they use and Delete All Downloads. [onBack] null: pushed in the signed-in shell, so
 * Back pops the navigator.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DownloadSettingsPage(onBack: (() -> Unit)? = null) {
    val back = onBack ?: LocalNavigator.current.let { n -> { n.pop(); Unit } }
    val items by Downloads.items.collectAsState()
    var confirmAll by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { Downloads.refreshUsage() }
    DownloadPage("Downloads", onBack = back) { padding ->
        androidx.compose.foundation.layout.Column(Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState())) {
            ToggleRow("Wi-Fi Only", "Downloads wait for Wi-Fi instead of using mobile data. Running downloads pause when Wi-Fi " +
                "drops and continue when it is back.", DownloadSettings.wifiOnly) { DownloadSettings.wifiOnly = it }
            ListItem(
                headlineContent = { Text("Simultaneous Downloads") },
                supportingContent = {
                    androidx.compose.foundation.layout.Column {
                        Text("More at once can be slower for each; the rest wait in the queue.")
                        SingleChoiceSegmentedButtonRow(Modifier.padding(top = 8.dp)) {
                            (1..3).forEach { n ->
                                SegmentedButton(
                                    selected = DownloadSettings.maxConcurrent == n,
                                    onClick = { DownloadSettings.maxConcurrent = n },
                                    shape = SegmentedButtonDefaults.itemShape(n - 1, 3),
                                ) { Text("$n") }
                            }
                        }
                    }
                },
            )
            ToggleRow("Delete Watched Downloads", "Finished downloads you have watched are removed the next time Downloads " +
                "opens while the server is reachable.", DownloadSettings.deleteWatched) { DownloadSettings.deleteWatched = it }
            SectionHeader("Storage")
            DownloadsStorage()
            ListItem(headlineContent = { Text("Downloads") }, trailingContent = { Text("${items.size}") })
            ListItem(
                headlineContent = {
                    Text("Delete All Downloads", color = if (items.isEmpty()) MaterialTheme.colorScheme.onSurfaceVariant
                    else MaterialTheme.colorScheme.error)
                },
                modifier = Modifier.clickable(enabled = items.isNotEmpty()) { confirmAll = true },
            )
        }
    }
    if (confirmAll) {
        AlertDialog(
            onDismissRequest = { confirmAll = false },
            title = { Text("Delete all downloads?") },
            text = { Text("Every downloaded file is removed from this device, and running downloads are cancelled.") },
            confirmButton = {
                TextButton(onClick = {
                    confirmAll = false
                    Downloads.delete(items.map { it.id })
                }) { Text("Delete ${items.size}") }
            },
            dismissButton = { TextButton(onClick = { confirmAll = false }) { Text("Cancel") } },
        )
    }
}

@Composable
private fun ToggleRow(title: String, footer: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    ListItem(
        headlineContent = { Text(title) },
        supportingContent = { Text(footer) },
        trailingContent = { Switch(checked = checked, onCheckedChange = onChange) },
        modifier = Modifier.clickable { onChange(!checked) },
    )
}


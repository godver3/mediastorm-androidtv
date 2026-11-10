package com.strmr.mobile.ui.recordings

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.strmr.tv.live.SeriesRule

/**
 * One rule's settings, saved as they change: enabled, every airing or new episodes only (the
 * TV's switch-series choice), its own padding, and delete (confirmed by the page).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RuleEditorSheet(rule: SeriesRule, model: RecordingRulesModel, onDelete: () -> Unit, onDismiss: () -> Unit) {
    val clear = ListItemDefaults.colors(containerColor = Color.Transparent)
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(Modifier.fillMaxWidth().padding(bottom = 24.dp)) {
            Text(rule.pattern, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold,
                 modifier = Modifier.padding(horizontal = 16.dp))
            Text(
                listOf(if (rule.isRegex) "Regex" else "Title",
                       if (rule.allChannels) "All channels" else rule.channelName ?: "Selected channel").joinToString(" · "),
                color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
            )
            ListItem(
                headlineContent = { Text("Enabled") },
                trailingContent = { Switch(rule.enabled, { model.setEnabled(rule, it) }, enabled = !model.busy) },
                colors = clear,
            )
            ListItem(
                headlineContent = { Text("New Episodes Only") },
                supportingContent = { Text(if (rule.newOnly) "Skips airings the guide marks as reruns." else "Records every airing.") },
                trailingContent = { Switch(rule.newOnly, { model.setNewOnly(rule, it) }, enabled = !model.busy) },
                colors = clear,
            )
            HorizontalDivider(Modifier.padding(vertical = 8.dp))
            Text("Buffers", style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.primary,
                 modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp))
            MinutesStepper("Before", rule.paddingBeforeSeconds) { model.setPadding(rule, before = it) }
            MinutesStepper("After", rule.paddingAfterSeconds) { model.setPadding(rule, after = it) }
            OutlinedButton(
                onClick = onDelete,
                colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.error),
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp),
            ) {
                Icon(Icons.Filled.Delete, contentDescription = null)
                Text(if (rule.isRegex) "Delete Rule" else "Cancel Series Recording", modifier = Modifier.padding(start = 8.dp))
            }
        }
    }
}

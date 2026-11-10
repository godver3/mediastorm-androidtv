package com.strmr.mobile.ui.recordings

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.strmr.mobile.ui.shell.PageScaffold
import com.strmr.tv.live.RecordingSettings
import com.strmr.tv.live.SeriesRule

/**
 * Rules & Buffers: the default padding around new recordings, a new regex rule, and the
 * recurring recordings, each paused / resumed with a switch; tapping one opens its editor
 * (new episodes only, its own padding, delete).
 */
@Composable
fun RecordingRulesScreen() {
    val scope = rememberCoroutineScope()
    val model = remember { RecordingRulesModel(scope) }
    var pattern by rememberSaveable { mutableStateOf("") }
    var editing by remember { mutableStateOf<String?>(null) }
    var deleting by remember { mutableStateOf<SeriesRule?>(null) }
    LaunchedEffect(Unit) { model.load() }

    PageScaffold("Rules & Buffers", showBack = true) { padding ->
        LazyColumn(Modifier.fillMaxSize().padding(padding), contentPadding = PaddingValues(bottom = 24.dp)) {
            item { Header("Default Buffers") }
            item {
                val s = model.settings
                if (s == null) {
                    if (model.loading) CircularProgressIndicator(Modifier.padding(16.dp))
                } else {
                    MinutesStepper("Before", s.paddingBeforeSeconds) { model.saveSettings(RecordingSettings(it, s.paddingAfterSeconds)) }
                    MinutesStepper("After", s.paddingAfterSeconds) { model.saveSettings(RecordingSettings(s.paddingBeforeSeconds, it)) }
                }
                Footer("Added before and after new guide and manual recordings.")
            }
            item { Header("Add a Regex Rule") }
            item {
                Column(Modifier.padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(pattern, { pattern = it }, placeholder = { Text("For example: (News|Weather).*") },
                                      singleLine = true, modifier = Modifier.fillMaxWidth())
                    Button(onClick = { model.createRegex(pattern) { pattern = "" } }, enabled = pattern.isNotBlank() && !model.busy) {
                        Text("Create Regex Rule")
                    }
                }
                Footer("Matches guide titles in the next 7 days on every channel, ignoring case. Use (?-i) to match exact capitalization.")
            }
            item { Header("Recurring Recordings") }
            if (model.rules.isEmpty() && !model.loading) {
                item { Footer("No rules yet. Long press a programme on Live TV and choose Record Series to record it whenever it airs.") }
            }
            items(model.rules, key = { it.id }) { rule ->
                ListItem(
                    headlineContent = { Text(rule.pattern, maxLines = 2) },
                    overlineContent = { Text(if (rule.isRegex) "REGEX" else "TITLE") },
                    supportingContent = { Text(RecordingRulesModel.summary(rule)) },
                    trailingContent = { Switch(rule.enabled, { model.setEnabled(rule, it) }, enabled = !model.busy) },
                    modifier = Modifier.clickable { editing = rule.id },
                )
            }
            model.error?.let { item { Text(it, color = MaterialTheme.colorScheme.error, modifier = Modifier.padding(16.dp)) } }
        }
    }

    editing?.let { id ->
        val rule = model.rules.firstOrNull { it.id == id }
        if (rule == null) editing = null
        else RuleEditorSheet(rule, model, onDelete = { editing = null; deleting = rule }, onDismiss = { editing = null })
    }
    deleting?.let { rule ->
        AlertDialog(
            onDismissRequest = { deleting = null },
            title = { Text("Delete recurring recording?") },
            text = { Text("Remove the rule for “${rule.pattern}”? Upcoming recordings from this rule will be cancelled.") },
            confirmButton = {
                TextButton(onClick = { deleting = null; model.delete(rule) }) { Text("Delete Rule", color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = { TextButton(onClick = { deleting = null }) { Text("Keep Rule") } },
        )
    }
}

@Composable
private fun Header(text: String) {
    Text(text, style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.primary,
         modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 20.dp, bottom = 4.dp))
}

@Composable
private fun Footer(text: String) {
    Text(text, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
         modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp))
}

/** Whole minutes from 0 to 60, as the server takes them; reports seconds. */
@Composable
fun MinutesStepper(title: String, seconds: Int, onChange: (Int) -> Unit) {
    val minutes = seconds / 60
    Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(title, modifier = Modifier.weight(1f))
        IconButton(onClick = { onChange((minutes - 1).coerceAtLeast(0) * 60) }, enabled = minutes > 0) { Icon(Icons.Filled.Remove, "Less") }
        Text("$minutes min", style = MaterialTheme.typography.bodyLarge)
        IconButton(onClick = { onChange((minutes + 1).coerceAtMost(60) * 60) }, enabled = minutes < 60) { Icon(Icons.Filled.Add, "More") }
    }
}

package com.strmr.mobile.ui.recordings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.strmr.mobile.ui.shell.LocalNavigator
import com.strmr.mobile.ui.shell.PageScaffold
import com.strmr.tv.live.Recording
import com.strmr.tv.live.RecordingRules
import com.strmr.tv.player.PlaybackCoordinator
import com.strmr.tv.ui.recordings.RecordingsState
import kotlinx.coroutines.delay

/**
 * One recording's page: status, title, channel, times, duration and size, description, error,
 * then its actions (Play / Play In Progress, Stop / Cancel, Cancel Series, Delete), each but
 * Play confirmed with the TV wording. Refreshes every 10 s while it is scheduled or recording.
 */
@Composable
fun RecordingDetailScreen(id: String) {
    val scope = rememberCoroutineScope()
    val state = remember { RecordingsState(scope) }
    val navigator = LocalNavigator.current
    val confirmations = remember { RecordingConfirmations(state).also { it.onDeleted = { navigator.pop() } } }
    val sessionsEnded by PlaybackCoordinator.sessionsEnded.collectAsState()
    val r = state.recordings.firstOrNull { it.id == id }

    LaunchedEffect(sessionsEnded) {
        while (true) {
            state.load(quiet = true)
            delay(10_000)
        }
    }

    PageScaffold(r?.let { RecordingRules.title(it) } ?: "Recording", showBack = true) { padding ->
        Box(Modifier.fillMaxSize().padding(padding)) {
            when {
                r != null -> Detail(r, state, confirmations)
                state.loading -> CircularProgressIndicator(Modifier.align(Alignment.Center))
                else -> Text(state.error ?: "This recording is no longer on the server.", color = MaterialTheme.colorScheme.onSurfaceVariant,
                             modifier = Modifier.align(Alignment.Center).padding(32.dp))
            }
        }
    }
    RecordingConfirmationDialog(confirmations)
}

@Composable
private fun Detail(r: Recording, state: RecordingsState, confirmations: RecordingConfirmations) {
    val busy = state.busyId == r.id
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            Icon(statusIcon(r.status), contentDescription = null, tint = statusColor(r.status), modifier = Modifier.size(18.dp))
            Text(RecordingRules.statusLabel(r.status).uppercase(), color = statusColor(r.status), style = MaterialTheme.typography.labelLarge,
                 fontWeight = FontWeight.Bold)
        }
        Text(RecordingRules.title(r), style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.SemiBold)
        Text(r.channelName, color = MaterialTheme.colorScheme.onSurfaceVariant)
        HorizontalDivider(Modifier.padding(vertical = 8.dp))
        Fact("Starts", RecordingRules.dateTime(r.startAt))
        Fact("Ends", RecordingRules.dateTime(r.endAt))
        RecordingRules.duration(r.startAt, r.endAt).takeIf { it.isNotEmpty() }?.let { Fact("Duration", it) }
        RecordingRules.size(r.outputSizeBytes)?.let { Fact("Size", it) }
        if (!r.ruleId.isNullOrEmpty()) Fact("Scheduled by", "A recurring rule")
        HorizontalDivider(Modifier.padding(vertical = 8.dp))
        Text(r.description?.ifBlank { null } ?: "No program description available.", style = MaterialTheme.typography.bodyMedium)
        RecordingRules.visibleError(r.error)?.let { Text(it, color = MaterialTheme.colorScheme.error) }
        Spacer(Modifier.size(12.dp))
        RecordingActions.of(r, state.ruleFor(r)).forEach { a ->
            val content: @Composable () -> Unit = {
                Icon(actionIcon(a.kind), contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.size(8.dp))
                Text(a.label)
            }
            if (a.kind == RecordingActionKind.PLAY) {
                Button(onClick = { confirmations.run(r, a.kind) }, enabled = !busy, modifier = Modifier.fillMaxWidth()) { content() }
            } else {
                OutlinedButton(
                    onClick = { confirmations.run(r, a.kind) }, enabled = !busy, modifier = Modifier.fillMaxWidth(),
                    colors = if (a.kind == RecordingActionKind.DELETE) ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.error)
                    else ButtonDefaults.outlinedButtonColors(),
                ) { content() }
            }
        }
        if (!RecordingRules.canDelete(r)) {
            Text("This recording belongs to a recurring rule. Pause or delete the rule under Rules & Buffers.",
                 style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun Fact(label: String, value: String) {
    Row(Modifier.fillMaxWidth()) {
        Text(label, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.weight(1f))
        Text(value)
    }
}

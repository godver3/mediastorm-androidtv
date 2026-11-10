package com.strmr.mobile.ui.recordings

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Block
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.FiberManualRecord
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.PlayCircle
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.StopCircle
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.strmr.mobile.ui.live.RecordingRed
import com.strmr.tv.live.Recording
import com.strmr.tv.live.RecordingRules

private val CompletedGreen = Color(0xFF32D74B)
private val FailedOrange = Color(0xFFFF9F0A)

/** A status's colour and symbol, as the iPhone list. */
@Composable
fun statusColor(status: String): Color = when (status) {
    "pending" -> MaterialTheme.colorScheme.primary
    "starting", "running" -> RecordingRed
    "completed" -> CompletedGreen
    "cancelled" -> MaterialTheme.colorScheme.onSurfaceVariant
    else -> FailedOrange
}

fun statusIcon(status: String): ImageVector = when (status) {
    "pending" -> Icons.Filled.Schedule
    "starting", "running" -> Icons.Filled.FiberManualRecord
    "completed" -> Icons.Filled.PlayCircle
    "cancelled" -> Icons.Filled.StopCircle
    else -> Icons.Filled.ErrorOutline
}

/** "Oct 22, 7:05 PM · 1h" */
fun recordingWhen(r: Recording): String =
    listOf(RecordingRules.dateTime(r.startAt), RecordingRules.duration(r.startAt, r.endAt)).filter { it.isNotEmpty() }.joinToString(" · ")

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun RecordingRow(r: Recording, busy: Boolean, onOpen: () -> Unit, onPlay: () -> Unit, onMenu: () -> Unit) {
    val haptics = LocalHapticFeedback.current
    val color = statusColor(r.status)
    ListItem(
        leadingContent = { Icon(statusIcon(r.status), contentDescription = null, tint = color) },
        headlineContent = { Text(RecordingRules.title(r), maxLines = 2, overflow = TextOverflow.Ellipsis) },
        supportingContent = {
            Column {
                Text(r.channelName, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(recordingWhen(r), style = MaterialTheme.typography.bodySmall, maxLines = 1)
                RecordingRules.visibleError(r.error)?.let {
                    Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall, maxLines = 2)
                }
            }
        },
        trailingContent = {
            Column(horizontalAlignment = Alignment.End, verticalArrangement = Arrangement.spacedBy(4.dp)) {
                when {
                    busy -> CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp)
                    RecordingRules.canPlay(r) -> IconButton(onClick = onPlay) {
                        Icon(Icons.Filled.PlayArrow, contentDescription = if (RecordingRules.isRecording(r)) "Play In Progress" else "Play")
                    }
                    else -> Text(RecordingRules.statusLabel(r.status), style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.SemiBold,
                                 color = color, modifier = Modifier.background(color.copy(alpha = 0.18f), RoundedCornerShape(50))
                                     .padding(horizontal = 8.dp, vertical = 3.dp))
                }
                RecordingRules.size(r.outputSizeBytes)?.let {
                    Text(it, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        },
        modifier = Modifier.combinedClickable(onClick = onOpen, onLongClick = {
            haptics.performHapticFeedback(HapticFeedbackType.LongPress)
            onMenu()
        }),
    )
}

fun actionIcon(kind: RecordingActionKind): ImageVector = when (kind) {
    RecordingActionKind.PLAY -> Icons.Filled.PlayArrow
    RecordingActionKind.CANCEL -> Icons.Filled.StopCircle
    RecordingActionKind.CANCEL_SERIES -> Icons.Filled.Block
    RecordingActionKind.DELETE -> Icons.Filled.Delete
}

/** A row's long-press actions as a bottom sheet. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RecordingMenuSheet(r: Recording, actions: List<RecordingAction>, onDismiss: () -> Unit, onAction: (RecordingActionKind) -> Unit) {
    ModalBottomSheet(onDismissRequest = onDismiss) {
        LazyColumn(Modifier.fillMaxWidth().padding(bottom = 16.dp)) {
            item {
                Column(Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
                    Text(RecordingRules.title(r), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                    Text("${r.channelName} · ${RecordingRules.statusLabel(r.status)}", color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            items(actions, key = { it.kind }) { a ->
                ListItem(
                    headlineContent = { Text(a.label) },
                    leadingContent = { Icon(actionIcon(a.kind), contentDescription = null) },
                    colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                    modifier = Modifier.clickable { onAction(a.kind) },
                )
            }
        }
    }
}

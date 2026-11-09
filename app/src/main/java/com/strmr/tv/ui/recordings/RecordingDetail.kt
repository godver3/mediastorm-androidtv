package com.strmr.tv.ui.recordings

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.focusRestorer
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.tv.material3.Icon
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.OutlinedButton
import androidx.tv.material3.Text
import com.strmr.tv.live.Recording
import com.strmr.tv.live.RecordingRules
import com.strmr.tv.live.SeriesRule
import com.strmr.tv.ui.ChoiceDialog
import com.strmr.tv.ui.DialogChoice

private val Muted = Color.White.copy(alpha = 0.65f)

private fun statusColor(status: String) = when (status) {
    "completed" -> Color(0xFF32D74B)
    "failed" -> RecordingRed
    "cancelled" -> Color.White.copy(alpha = 0.5f)
    "running" -> RecordingRed
    else -> Color(0xFFFFD60A)
}

/** The selected recording: status, title, channel, times, duration and size, description, error, then its actions. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun RecordingDetail(
    r: Recording,
    rule: SeriesRule?,
    busy: Boolean,
    actionsFocus: FocusRequester,
    onPlay: (Recording) -> Unit,
    onCancel: (Recording) -> Unit,
    onCancelSeries: (Recording) -> Unit,
    onDelete: (Recording) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier.background(Color.White.copy(alpha = 0.06f), RoundedCornerShape(16.dp)).padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        val label = RecordingRules.statusLabel(r.status).uppercase()
        Text(if (RecordingRules.isRecording(r)) "● $label" else label, color = statusColor(r.status),
             style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold)
        Text(RecordingRules.title(r), style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.SemiBold,
             maxLines = 2, overflow = TextOverflow.Ellipsis)
        Text(r.channelName, color = Muted, maxLines = 1, overflow = TextOverflow.Ellipsis)
        Text("${RecordingRules.dateTime(r.startAt)} — ${RecordingRules.dateTime(r.endAt)}", color = Muted)
        val facts = listOfNotNull(RecordingRules.duration(r.startAt, r.endAt).ifEmpty { null }, RecordingRules.size(r.outputSizeBytes))
        if (facts.isNotEmpty()) Text(facts.joinToString(" · "), color = Muted)
        Text(r.description?.ifBlank { null } ?: "No program description available.", color = Color.White.copy(alpha = 0.8f),
             maxLines = 4, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(top = 6.dp))
        RecordingRules.visibleError(r.error)?.let { Text(it, color = RecordingRed, maxLines = 3, overflow = TextOverflow.Ellipsis) }
        Spacer(Modifier.height(10.dp))
        // Left from here goes back to the list; entering the row lands on the button last focused.
        FlowRow(
            Modifier.focusRequester(actionsFocus).focusRestorer(),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            actions(r, rule).forEach { a ->
                OutlinedButton(onClick = { if (!busy) a.run(r, onPlay, onCancel, onCancelSeries, onDelete) }, enabled = !busy) {
                    Icon(a.icon, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.size(8.dp))
                    Text(a.label)
                }
            }
        }
    }
}

private enum class Kind { PLAY, CANCEL, CANCEL_SERIES, DELETE }

private data class Action(val kind: Kind, val label: String, val icon: ImageVector) {
    fun run(
        r: Recording, onPlay: (Recording) -> Unit, onCancel: (Recording) -> Unit, onCancelSeries: (Recording) -> Unit,
        onDelete: (Recording) -> Unit,
    ) = when (kind) {
        Kind.PLAY -> onPlay(r)
        Kind.CANCEL -> onCancel(r)
        Kind.CANCEL_SERIES -> onCancelSeries(r)
        Kind.DELETE -> onDelete(r)
    }
}

/**
 * Expo's TV actions and labels: Play (in progress), Stop / Cancel, Cancel Series (an airing a
 * rule scheduled), Delete, each only where it applies.
 */
private fun actions(r: Recording, rule: SeriesRule?): List<Action> = buildList {
    val recording = RecordingRules.isRecording(r)
    if (RecordingRules.canPlay(r)) add(Action(Kind.PLAY, if (recording) "Play In Progress" else "Play Recording", Icons.Filled.PlayArrow))
    if (RecordingRules.canCancel(r)) add(Action(Kind.CANCEL, if (recording) "Stop Recording" else "Cancel Scheduled Recording", Icons.Filled.Close))
    if (rule != null) add(Action(Kind.CANCEL_SERIES, "Cancel Series Recording", Icons.Filled.DateRange))
    if (RecordingRules.canDelete(r)) add(Action(Kind.DELETE, "Delete Recording", Icons.Filled.Delete))
}

/** Long press on a row: the same actions in a dialog. */
@Composable
fun RecordingMenu(
    r: Recording, rule: SeriesRule?, onPlay: () -> Unit, onCancel: () -> Unit, onCancelSeries: () -> Unit, onDelete: () -> Unit,
    onDismiss: () -> Unit,
) {
    ChoiceDialog(
        title = RecordingRules.title(r),
        message = "${r.channelName} · ${RecordingRules.statusLabel(r.status)}",
        choices = actions(r, rule).map { a ->
            DialogChoice(a.label) { a.run(r, { onPlay() }, { onCancel() }, { onCancelSeries() }, { onDelete() }) }
        } +
            DialogChoice("Cancel") {},
        onDismiss = onDismiss,
        steady = true,
    )
}

/** Cancelling a series: the safe choice first, as for a single recording. */
@Composable
fun SeriesCancelConfirmation(rule: SeriesRule, onConfirm: () -> Unit, onDismiss: () -> Unit) {
    ChoiceDialog(
        title = "Cancel series recording?",
        message = "Stop recording ${rule.summary}? Its scheduled airings will not start; one recording now keeps going.",
        choices = listOf(DialogChoice("Keep Series") {}, DialogChoice("Cancel Series", onConfirm)),
        onDismiss = onDismiss,
        steady = true,
    )
}

/** Expo's `RecordingConfirmationModal`: the safe choice ("Keep Recording") first and focused. */
@Composable
fun RecordingConfirmation(r: Recording, delete: Boolean, onConfirm: () -> Unit, onDismiss: () -> Unit) {
    val c = RecordingRules.confirmation(r, delete)
    ChoiceDialog(
        title = c.title,
        message = c.message,
        choices = listOf(DialogChoice("Keep Recording") {}, DialogChoice(c.confirm, onConfirm)),
        onDismiss = onDismiss,
        steady = true,
    )
}

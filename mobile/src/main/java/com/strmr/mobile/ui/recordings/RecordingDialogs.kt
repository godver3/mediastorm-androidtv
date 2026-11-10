package com.strmr.mobile.ui.recordings

import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.strmr.tv.live.Recording
import com.strmr.tv.live.RecordingRules
import com.strmr.tv.player.PlaybackCoordinator
import com.strmr.tv.ui.recordings.RecordingsState

/**
 * Runs a recording action from the list or the detail page: Play at once, the rest after a
 * confirmation with the TV wording (the safe choice, "Keep Recording" / "Keep Series", as
 * dismiss). Main thread only.
 */
class RecordingConfirmations(private val state: RecordingsState) {
    var pending by mutableStateOf<Pair<Recording, RecordingActionKind>?>(null)
        private set
    /** Called after a confirmed delete (the detail page closes). */
    var onDeleted: () -> Unit = {}

    fun run(r: Recording, kind: RecordingActionKind) {
        if (kind == RecordingActionKind.PLAY) PlaybackCoordinator.playRecording(r) else pending = r to kind
    }

    fun ruleFor(r: Recording) = state.ruleFor(r)

    fun dismiss() {
        pending = null
    }

    fun confirm() {
        val (r, kind) = pending ?: return
        pending = null
        when (kind) {
            RecordingActionKind.CANCEL -> state.cancel(r)
            RecordingActionKind.CANCEL_SERIES -> state.cancelSeries(r)
            RecordingActionKind.DELETE -> {
                state.delete(r)
                onDeleted()
            }
            RecordingActionKind.PLAY -> Unit
        }
    }
}

@Composable
fun RecordingConfirmationDialog(c: RecordingConfirmations) {
    val (r, kind) = c.pending ?: return
    val title: String
    val message: String
    val confirm: String
    val keep: String
    if (kind == RecordingActionKind.CANCEL_SERIES) {
        val rule = c.ruleFor(r)
        title = "Cancel series recording?"
        message = "Stop recording ${rule?.summary ?: RecordingRules.title(r)}? Its scheduled airings will not start; one recording now keeps going."
        confirm = "Cancel Series"
        keep = "Keep Series"
    } else {
        val t = RecordingRules.confirmation(r, delete = kind == RecordingActionKind.DELETE)
        title = t.title
        message = t.message
        confirm = t.confirm
        keep = "Keep Recording"
    }
    AlertDialog(
        onDismissRequest = c::dismiss,
        title = { Text(title) },
        text = { Text(message) },
        confirmButton = {
            TextButton(onClick = c::confirm) { Text(confirm, color = MaterialTheme.colorScheme.error) }
        },
        dismissButton = { TextButton(onClick = c::dismiss) { Text(keep) } },
    )
}

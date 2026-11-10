package com.strmr.mobile.ui.downloads

import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import com.strmr.mobile.downloads.DownloadItem
import com.strmr.mobile.downloads.DownloadPlayback
import com.strmr.mobile.downloads.Downloads
import com.strmr.tv.api.StartPoint
import com.strmr.tv.ui.AppToast
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

/**
 * The Downloads prompts (the iPhone app's `MobileDownloadsPrompts`): Delete (one download, a
 * show's or a selection) and, before a finished download plays, Resume / Start Over when a
 * resume point is known (the server's, else the one kept on the device).
 */
class DownloadPrompts(private val scope: CoroutineScope) {
    data class Deletion(val ids: List<String>, val title: String)
    data class Resume(val item: DownloadItem, val start: StartPoint)

    var deletion by mutableStateOf<Deletion?>(null)
    var resume by mutableStateOf<Resume?>(null)
    /** The download whose resume point is being looked up (its row shows a spinner). */
    var checkingResume by mutableStateOf<String?>(null)

    fun play(item: DownloadItem) {
        if (checkingResume != null) return
        checkingResume = item.id
        scope.launch {
            val start = DownloadPlayback.resumePoint(item)
            checkingResume = null
            if (start != null) resume = Resume(item, start) else start(item, null)
        }
    }

    fun start(item: DownloadItem, at: StartPoint?) {
        DownloadPlayback.play(item, at)?.let(AppToast::show)
    }

    fun confirmDelete(item: DownloadItem) {
        deletion = Deletion(listOf(item.id), if (item.isActive) "Cancel this download?" else "Delete “${item.displayTitle}”?")
    }

    fun confirmDelete(ids: List<String>) {
        if (ids.isEmpty()) return
        deletion = Deletion(ids, if (ids.size == 1) "Delete 1 download?" else "Delete ${ids.size} downloads?")
    }
}

@Composable
fun rememberDownloadPrompts(): DownloadPrompts {
    val scope = rememberCoroutineScope()
    return remember { DownloadPrompts(scope) }
}

/** The prompts' dialogs; [onDeleted] runs after a confirmed delete (leave selection mode). */
@Composable
fun DownloadPromptDialogs(prompts: DownloadPrompts, onDeleted: () -> Unit = {}) {
    prompts.deletion?.let { d ->
        AlertDialog(
            onDismissRequest = { prompts.deletion = null },
            title = { Text(d.title) },
            text = { Text(if (d.ids.size == 1) "The file is removed from this device." else "The files are removed from this device.") },
            confirmButton = {
                TextButton(onClick = {
                    prompts.deletion = null
                    Downloads.delete(d.ids)
                    onDeleted()
                }) { Text("Delete") }
            },
            dismissButton = { TextButton(onClick = { prompts.deletion = null }) { Text("Cancel") } },
        )
    }
    prompts.resume?.let { r ->
        AlertDialog(
            onDismissRequest = { prompts.resume = null },
            title = { Text(r.item.displayTitle) },
            text = { Text("Resume from ${r.start.label} or start over?") },
            confirmButton = {
                TextButton(onClick = {
                    prompts.resume = null
                    prompts.start(r.item, r.start)
                }) { Text("Resume") }
            },
            dismissButton = {
                TextButton(onClick = {
                    prompts.resume = null
                    prompts.start(r.item, null)
                }) { Text("Start Over") }
            },
        )
    }
}

/** A row's tap: play a finished download, pause a running one, resume or retry the rest. */
fun DownloadPrompts.primary(item: DownloadItem) {
    when (item.status) {
        DownloadItem.Status.COMPLETED -> play(item)
        DownloadItem.Status.DOWNLOADING, DownloadItem.Status.PENDING -> Downloads.pause(item.id)
        DownloadItem.Status.PAUSED, DownloadItem.Status.FAILED -> Downloads.resume(item.id)
    }
}

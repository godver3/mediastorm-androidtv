package com.strmr.tv.ui.recordings

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.strmr.tv.api.ApiClient
import com.strmr.tv.app.AppLog
import com.strmr.tv.live.Recording
import com.strmr.tv.live.RecordingFilter
import com.strmr.tv.live.RecordingRules
import com.strmr.tv.live.cancelRecording
import com.strmr.tv.live.deleteRecording
import com.strmr.tv.live.recordings
import com.strmr.tv.ui.AppToast
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

/**
 * The Recordings page's data (Expo `NewRecordingsScreen` on TV): the profile's recordings in
 * the server's order (newest start first), the filter, the selected recording, and cancel /
 * delete with a toast and a reload, as Expo does. A failed refresh keeps the last list.
 */
class RecordingsState(private val scope: CoroutineScope) {
    var recordings by mutableStateOf<List<Recording>>(emptyList())
        private set
    var loading by mutableStateOf(true)
        private set
    var refreshing by mutableStateOf(false)
        private set
    var error by mutableStateOf<String?>(null)
        private set
    var busyId by mutableStateOf<String?>(null)
        private set
    var filter by mutableStateOf(RecordingFilter.ALL)
    var selectedId by mutableStateOf<String?>(null)
    /** Bumped after a delete: the deleted row had focus, so the page puts it on the new selection. */
    var deletions by mutableIntStateOf(0)
        private set

    val visible: List<Recording> get() = recordings.filter { filter.matches(it) }
    val selected: Recording? get() = visible.firstOrNull { it.id == selectedId } ?: visible.firstOrNull()

    fun count(f: RecordingFilter) = recordings.count { f.matches(it) }

    /** `quiet`: a poll or a reload after an action, without the Refresh button's spinner. */
    suspend fun load(quiet: Boolean = false) {
        if (!quiet) refreshing = true
        try {
            recordings = ApiClient.recordings()
            error = null
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            AppLog.w("Recordings", "load failed: ${e.message}")
            error = e.message ?: "Failed to load recordings."
        } finally {
            loading = false
            refreshing = false
        }
    }

    fun refresh() = scope.launch { load() }

    /** Cancels a scheduled recording or stops one in progress. */
    fun cancel(r: Recording) = act(r, "cancel", "Cancelled", "Failed to cancel recording.") { ApiClient.cancelRecording(r.id) }

    /** The deleted row's neighbour (the next, else the previous) becomes the selection, so its actions aren't a different recording's by surprise. */
    fun delete(r: Recording) = act(r, "delete", "Deleted", "Failed to delete recording.") {
        ApiClient.deleteRecording(r.id)
        val list = visible
        val i = list.indexOfFirst { it.id == r.id }
        selectedId = (list.getOrNull(i + 1) ?: list.getOrNull(i - 1))?.id
        recordings = recordings.filter { it.id != r.id }
        deletions++
    }

    private fun act(r: Recording, verb: String, done: String, failed: String, call: suspend () -> Unit) {
        if (busyId != null) return
        busyId = r.id
        scope.launch {
            try {
                call()
                AppLog.i("Recordings", "$verb ${r.id} (${r.status})")
                AppToast.show("$done ${RecordingRules.title(r)}.")
                load(quiet = true)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                AppLog.w("Recordings", "$verb ${r.id} failed: ${e.message}")
                AppToast.show(e.message ?: failed)
            } finally {
                busyId = null
            }
        }
    }
}

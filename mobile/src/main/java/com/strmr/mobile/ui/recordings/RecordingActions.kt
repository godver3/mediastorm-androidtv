package com.strmr.mobile.ui.recordings

import com.strmr.tv.live.Recording
import com.strmr.tv.live.RecordingFilter
import com.strmr.tv.live.RecordingRules
import com.strmr.tv.live.SeriesRule

enum class RecordingActionKind { PLAY, CANCEL, CANCEL_SERIES, DELETE }

data class RecordingAction(val kind: RecordingActionKind, val label: String)

/** What the Recordings pages offer for a recording and how the list is grouped. Pure. */
object RecordingActions {
    /**
     * The TV app's actions and labels: Play (in progress), Stop / Cancel, Cancel Series (an
     * airing a rule scheduled), Delete, each only where it applies.
     */
    fun of(r: Recording, rule: SeriesRule?): List<RecordingAction> = buildList {
        val recording = RecordingRules.isRecording(r)
        if (RecordingRules.canPlay(r)) add(RecordingAction(RecordingActionKind.PLAY, if (recording) "Play In Progress" else "Play Recording"))
        if (RecordingRules.canCancel(r)) {
            add(RecordingAction(RecordingActionKind.CANCEL, if (recording) "Stop Recording" else "Cancel Scheduled Recording"))
        }
        if (rule != null) add(RecordingAction(RecordingActionKind.CANCEL_SERIES, "Cancel Series Recording"))
        if (RecordingRules.canDelete(r)) add(RecordingAction(RecordingActionKind.DELETE, "Delete Recording"))
    }

    /**
     * Under All, one section per status in the order that matters (recording now, upcoming,
     * completed, history; upcoming soonest first, the rest newest first), as the iPhone list;
     * otherwise the one filter, in the server's order.
     */
    fun sections(recordings: List<Recording>, filter: RecordingFilter): List<Pair<RecordingFilter, List<Recording>>> {
        if (filter != RecordingFilter.ALL) return listOf(filter to recordings.filter { filter.matches(it) })
        return listOf(RecordingFilter.RECORDING, RecordingFilter.UPCOMING, RecordingFilter.COMPLETED, RecordingFilter.HISTORY)
            .mapNotNull { f ->
                val start = { r: Recording -> RecordingRules.instant(r.startAt)?.toEpochMilli() ?: 0L }
                val items = recordings.filter { f.matches(it) }
                    .let { if (f == RecordingFilter.UPCOMING) it.sortedBy(start) else it.sortedByDescending(start) }
                if (items.isEmpty()) null else f to items
            }
    }
}

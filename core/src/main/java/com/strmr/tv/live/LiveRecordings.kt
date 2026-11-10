package com.strmr.tv.live

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.strmr.tv.api.ApiClient
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * Recording from the guide (Expo's TV programme menu and channel action menu): record a
 * programme once or every airing on the channel, record a two-hour block, and stop or cancel
 * what is already set. [active] holds the scheduled / recording ones the guide marks, refreshed
 * every 10 s while Live is on screen ([poll]) and straight after each action. Main thread only.
 */
class LiveRecordings(private val scope: CoroutineScope, private val notify: (String) -> Unit) {
    var active by mutableStateOf<List<Recording>>(emptyList())
        private set
    /** The profile's series / regex rules, so a programme a rule covers offers to cancel or switch it. */
    var rules by mutableStateOf<List<SeriesRule>>(emptyList())
        private set
    private var job: Job? = null

    /** Refreshes [active] until the caller's coroutine is cancelled. */
    suspend fun poll() {
        while (true) {
            refresh()
            delay(10_000)
        }
    }

    /** Keeps what was last known through a transient failure. */
    suspend fun refresh() {
        runCatching { ApiClient.activeRecordings() }.getOrNull()?.let { if (it != active) active = it }
        runCatching { ApiClient.recordingRules() }.getOrNull()?.let { if (it != rules) rules = it }
    }

    /** The rule that records [program] on the channel, if any. */
    fun seriesRule(program: EpgProgram, channel: LiveChannel): SeriesRule? = SeriesRule.find(rules, program.title, channel)

    /** Deletes the rule; the server cancels the airings it scheduled (one recording now keeps going). */
    fun cancelSeries(rule: SeriesRule) =
        perform("Series recording cancelled for ${rule.summary}.") { ApiClient.deleteRecordingRule(rule.id) }

    /** Switches an existing rule between every airing and new episodes only. */
    fun setSeries(rule: SeriesRule, newOnly: Boolean) =
        perform(if (newOnly) "Now recording only new episodes of ${rule.summary}." else "Now recording every airing of ${rule.summary}.") {
            ApiClient.setRecordingRuleNewOnly(rule.id, newOnly)
        }

    /**
     * The active recording overlapping [program] on the channel, else (without one) the one
     * recording the channel now; a running one wins (Expo `findLiveRecording`).
     */
    fun on(channel: LiveChannel, program: EpgProgram? = null, now: Long = System.currentTimeMillis()): Recording? =
        matching(active.filter { it.channelId == channel.id }, program, now)

    fun record(program: EpgProgram, channel: LiveChannel) =
        perform("Recording scheduled for ${program.title}.") { ApiClient.recordProgram(program, channel) }

    /** [newOnly] skips airings the guide flags as reruns; re-recording with the other choice switches the rule. */
    fun recordSeries(program: EpgProgram, channel: LiveChannel, newOnly: Boolean = false) =
        perform(if (newOnly) "Recording new episodes of \"${program.title}\" on ${channel.name}."
                else "Series recording set for \"${program.title}\" on ${channel.name}.") {
            ApiClient.recordSeries(program.title, channel, newOnly)
        }

    fun recordTwoHours(channel: LiveChannel) =
        perform("2 hour recording scheduled for ${channel.name}.") { ApiClient.recordTimeBlock(channel, 2 * 3_600_000L) }

    /** Stops a recording in progress (what was captured is kept) or cancels a scheduled one. */
    fun cancel(recording: Recording) =
        perform(if (recording.status == "pending") "Scheduled recording cancelled." else "Recording stopped.") {
            ApiClient.cancelRecording(recording.id)
        }

    private fun perform(success: String, action: suspend () -> Unit) {
        if (job?.isActive == true) return
        job = scope.launch {
            try {
                action()
                notify(success)
                refresh()
            } catch (e: kotlinx.coroutines.CancellationException) {
                throw e
            } catch (e: Exception) {
                notify(e.message ?: "The recording could not be changed.")
            }
        }
    }

    companion object {
        /** Of a channel's [recordings], the active one overlapping [program], else airing at [now]. */
        fun matching(recordings: List<Recording>, program: EpgProgram?, now: Long = System.currentTimeMillis()): Recording? {
            val hasProgram = program != null && program.stopMs > program.startMs
            val matches = recordings.filter { r ->
                if (!RecordingRules.canCancel(r)) return@filter false
                val from = RecordingRules.instant(r.startAt)?.toEpochMilli() ?: return@filter false
                val to = RecordingRules.instant(r.endAt)?.toEpochMilli() ?: return@filter false
                if (hasProgram) from < program!!.stopMs && to > program.startMs else from <= now && to > now
            }
            return matches.firstOrNull { it.status == "running" } ?: matches.firstOrNull()
        }
    }
}

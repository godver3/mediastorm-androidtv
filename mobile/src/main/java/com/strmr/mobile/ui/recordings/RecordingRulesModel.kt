package com.strmr.mobile.ui.recordings

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.strmr.tv.api.ApiClient
import com.strmr.tv.live.RecordingSettings
import com.strmr.tv.live.SeriesRule
import com.strmr.tv.live.createRegexRecordingRule
import com.strmr.tv.live.deleteRecordingRule
import com.strmr.tv.live.recordingRules
import com.strmr.tv.live.recordingSettings
import com.strmr.tv.live.saveRecordingSettings
import com.strmr.tv.live.setRecordingRuleNewOnly
import com.strmr.tv.live.updateRecordingRule
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * Rules & Buffers (the iPhone's `MobileRecordingRulesStore`): the profile's default padding,
 * its recurring recordings, a new regex rule, and each rule paused / resumed, switched between
 * every airing and new episodes only, re-padded or deleted. Each change reloads the rules.
 * Main thread only.
 */
class RecordingRulesModel(private val scope: CoroutineScope) {
    var rules by mutableStateOf<List<SeriesRule>>(emptyList())
        private set
    var settings by mutableStateOf<RecordingSettings?>(null)
        private set
    var loading by mutableStateOf(true)
        private set
    var busy by mutableStateOf(false)
        private set
    var error by mutableStateOf<String?>(null)
    private var settingsSave: Job? = null

    suspend fun load() {
        loading = true
        try {
            rules = ApiClient.recordingRules()
            error = null
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            error = e.message ?: "Failed to load recording rules."
        }
        runCatching { ApiClient.recordingSettings() }.getOrNull()?.let { settings = it }
        loading = false
    }

    /** Shown at once, saved once the buttons settle; a failed save puts the old values back. */
    fun saveSettings(new: RecordingSettings) {
        val previous = settings
        settings = new
        settingsSave?.cancel()
        settingsSave = scope.launch {
            delay(600)
            try {
                ApiClient.saveRecordingSettings(new)
                error = null
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                settings = previous
                error = e.message
            }
        }
    }

    fun createRegex(pattern: String, onDone: () -> Unit) {
        if (pattern.isBlank()) return
        run({ ApiClient.createRegexRecordingRule(pattern.trim()) }, onDone)
    }

    fun setEnabled(rule: SeriesRule, enabled: Boolean) = run({ ApiClient.updateRecordingRule(rule.id, enabled = enabled) })

    fun setNewOnly(rule: SeriesRule, newOnly: Boolean) = run({ ApiClient.setRecordingRuleNewOnly(rule.id, newOnly) })

    fun setPadding(rule: SeriesRule, before: Int? = null, after: Int? = null) =
        run({ ApiClient.updateRecordingRule(rule.id, paddingBefore = before, paddingAfter = after) })

    fun delete(rule: SeriesRule) = run({ ApiClient.deleteRecordingRule(rule.id) })

    private fun run(action: suspend () -> Unit, onDone: () -> Unit = {}) {
        if (busy) return
        busy = true
        scope.launch {
            try {
                action()
                error = null
                runCatching { ApiClient.recordingRules() }.getOrNull()?.let { rules = it }
                onDone()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                error = e.message ?: "The rule could not be changed."
            } finally {
                busy = false
            }
        }
    }

    companion object {
        /** "All channels · New only · Paused". */
        fun summary(rule: SeriesRule): String = listOfNotNull(
            if (rule.allChannels) "All channels" else rule.channelName ?: "Selected channel",
            if (rule.newOnly) "New only" else null,
            if (rule.enabled) "Enabled" else "Paused",
        ).joinToString(" · ")
    }
}

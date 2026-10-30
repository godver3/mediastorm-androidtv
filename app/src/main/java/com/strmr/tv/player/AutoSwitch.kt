package com.strmr.tv.player

import androidx.media3.common.Player
import com.strmr.tv.api.ApiClient
import com.strmr.tv.api.SourceResult
import com.strmr.tv.api.resolveSource
import com.strmr.tv.api.searchSources
import com.strmr.tv.app.AppLog
import com.strmr.tv.ui.settings.AutoSwitchSettings
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/**
 * Moves a movie or episode to the next ranked source by itself when the server reports this one
 * broken or too slow (`migrationRequested` on a progress reply), or [StallWatch] sees playback
 * stuck or stalling again and again. The next source is resolved while this one is still up, then
 * played from the same position with the same languages, as a Switch Source pick. Sources already
 * tried go with the request, so a switch never goes back to one; after [LIMIT] switches it stops
 * trying. Settings › Playback › Auto Switch Sources turns it off. The tvOS app's rules.
 */
class AutoSwitch(private val controller: PlayerController) {
    private val watch = StallWatch()
    private var job: Job? = null
    private var switching = false
    /** Sources that failed to resolve on an earlier try from this player. */
    private val failed = mutableSetOf<String>()
    private var sources: List<SourceResult>? = null
    private val session get() = controller.session
    private val request get() = controller.request

    private val applies: Boolean
        get() = session.reportsProgress && !session.isLive && session.streamPath != null && request.libraryVersion == null

    /** From the start of loading, so a source that never starts is caught too. */
    fun start() {
        if (!applies) return
        job = controller.scope.launch {
            while (isActive) {
                delay(1_000)
                sample()
            }
        }
    }

    fun cancel() {
        job?.cancel()
    }

    private fun sample() {
        val player = controller.player
        val position = player.currentPosition
        val duration = controller.duration.value
        val nearEnd = duration > 0 && position > duration - END_MARGIN_MS
        val active = player.playWhenReady && !nearEnd &&
            player.playbackSuppressionReason == Player.PLAYBACK_SUPPRESSION_REASON_NONE &&
            (player.playbackState == Player.STATE_BUFFERING || player.playbackState == Player.STATE_READY)
        watch.sample(position, active, System.currentTimeMillis())?.let { trigger(it.reason) }
    }

    /** Resolves the next untried source and switches to it; with none left, stays put. */
    fun trigger(reason: String) {
        if (!AutoSwitchSettings.enabled || !applies || switching || controller.switchTo.value != null ||
            controller.advanceTo.value != null || controller.ended.value) return
        if (request.autoSwitchCount >= LIMIT) {
            AppLog.i(TAG, "$reason; already switched ${request.autoSwitchCount} times, staying")
            watch.reset()
            return
        }
        switching = true
        val tried = (request.autoSwitchTried + failed + listOfNotNull(key(session.releaseName))).toMutableSet()
        AppLog.i(TAG, "$reason at ${controller.player.currentPosition / 1000}s on \"${session.releaseName}\"")
        controller.online.setStatus("Switching to another source…")
        controller.scope.launch {
            val results = sources ?: runCatching { ApiClient.searchSources(SourceSwitchRules.query(request), request.title) }
                .onFailure { AppLog.w(TAG, "source search failed: ${it.message}") }.getOrNull()?.also { sources = it }.orEmpty()
            val candidates = results.filter { r ->
                !SourceSwitchRules.isCurrent(r.title, r.attributes["raw_title"], session.releaseName) &&
                    keys(r).none { it in tried } && r.health?.state !in setOf("unhealthy", "not_cached")
            }
            for (candidate in candidates.take(CANDIDATES)) {
                if (controller.switchTo.value != null || controller.ended.value) return@launch
                tried += keys(candidate)
                try {
                    val resolved = ApiClient.resolveSource(candidate, request.title.titleId, controller.player.currentPosition / 1000.0)
                    if (controller.switchTo.value != null || controller.ended.value) return@launch
                    switchTo(candidate, resolved, tried)
                    return@launch
                } catch (e: Exception) {
                    AppLog.i(TAG, "\"${candidate.title}\" didn't resolve: ${e.message}")
                }
            }
            AppLog.i(TAG, "no other source to switch to (${results.size} sources)")
            controller.online.setStatus("No other source to switch to", clearAfterMs = 4_000)
            failed += tried
            watch.reset()
            switching = false
        }
    }

    private fun switchTo(source: SourceResult, resolved: Pair<String, com.strmr.tv.api.TrackSelection?>, tried: Set<String>) {
        val player = controller.player
        val carried = CarriedTracks.capture(player.currentTracks, player.trackSelectionParameters, controller.external.selected.value)
        AppLog.i(TAG, "-> \"${source.title}\" (${source.serviceType}) at ${player.currentPosition / 1000}s carried=$carried")
        val next = SourceSwitchRules.resumeAt(request, player.currentPosition)
            .copy(autoSwitchTried = tried, autoSwitchCount = request.autoSwitchCount + 1)
        controller.online.setStatus(null)
        controller.switchTo.value = SourceSwitchRequest(next, source, carried, resolved)
    }

    private fun keys(r: SourceResult): Set<String> =
        setOfNotNull(key(r.id), key(SourceSwitchRules.releaseName(r.title, r.attributes["raw_title"])))

    private fun key(value: String?): String? = value?.trim()?.lowercase()?.ifEmpty { null }

    companion object {
        private const val TAG = "AutoSwitch"
        const val LIMIT = 3
        /** Sources resolved per try before giving up on this one. */
        const val CANDIDATES = 5
        /** Stalls this close to the end aren't worth a switch; the credits are rolling. */
        const val END_MARGIN_MS = 60_000L
    }
}

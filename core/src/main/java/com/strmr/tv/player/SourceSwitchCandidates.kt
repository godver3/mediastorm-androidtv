package com.strmr.tv.player

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.strmr.tv.api.ApiClient
import com.strmr.tv.api.SourceHealth
import com.strmr.tv.api.SourceResult
import com.strmr.tv.api.searchSources
import com.strmr.tv.api.sourceHealth
import com.strmr.tv.app.AppLog

/**
 * Switch Source's list: the title's ranked sources, as the source picker searches them
 * (`/api/indexers/search`, with debrid cache / usenet health polled until known), the playing
 * one checked. Choosing another closes the player and resumes it there at the current
 * position; nothing switches by itself. Shared by the TV popover and the phone's sheet.
 */
class SourceSwitchCandidates(private val controller: PlayerController) {
    var results by mutableStateOf<List<SourceResult>?>(null)
        private set
    var error by mutableStateOf<String?>(null)
        private set
    private val health = mutableStateMapOf<Int, SourceHealth>()
    private val playing = controller.session.releaseName

    /** Searches, then polls pending health until complete; run in the menu's effect. */
    suspend fun load() {
        val request = controller.request
        val query = SourceSwitchRules.query(request)
        try {
            val found = ApiClient.searchSources(query, request.title, request.episode?.numbering)
            found.forEach { r -> r.health?.let { health[r.index] = it } }
            results = found
            AppLog.i("SourceSwitch", "candidates query=\"$query\" count=${found.size} " +
                "current=${found.indexOfFirst { isCurrent(it) }} playing=$playing")
        } catch (e: kotlinx.coroutines.CancellationException) {
            throw e
        } catch (e: Exception) {
            error = e.message ?: "Couldn't load sources"
            AppLog.w("SourceSwitch", "candidate search failed: ${e.message}")
            return
        }
        for (token in health.values.filter { it.state == "pending" }.mapNotNull { it.token }.toSet()) {
            var complete = false
            while (!complete) {
                val snapshot = runCatching { ApiClient.sourceHealth(token) }.getOrNull() ?: break
                snapshot.results.forEach { health[it.index] = it.health }
                complete = snapshot.complete
            }
        }
        health.filterValues { it.state == "pending" }.keys.forEach { health[it] = SourceHealth("unknown") }
    }

    /** The menu's rows; picking the playing one just closes the menu. */
    fun choices(onDone: () -> Unit): List<MenuChoice> {
        val list = results
        return buildList {
            // The playing release heads the list when the search doesn't return it (as Expo keeps it in its candidates).
            if (list != null && list.none { isCurrent(it) } && playing != null) {
                add(MenuChoice(playing, "Playing", selected = true, onClick = onDone))
            }
            list?.forEach { r ->
                val current = isCurrent(r)
                val detail = SourceSwitchRules.detail(r.attributes, r.sizeBytes, r.serviceType, r.indexer, health[r.index]?.state)
                add(MenuChoice(SourceSwitchRules.releaseName(r.title, r.attributes["raw_title"]),
                               if (current) "Playing · $detail" else detail, selected = current) {
                    onDone()
                    if (!current) controller.switchSource(r)
                })
            }
        }
    }

    /** What the menu says when it has no rows. */
    val emptyText: String
        get() = when {
            error != null -> error!!
            results == null -> "Searching sources…"
            else -> "No other sources found"
        }

    private fun isCurrent(r: SourceResult) = SourceSwitchRules.isCurrent(r.title, r.attributes["raw_title"], playing)
}

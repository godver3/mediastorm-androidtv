package com.strmr.tv.player

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.strmr.tv.api.ApiClient
import com.strmr.tv.api.SourceHealth
import com.strmr.tv.api.SourceResult
import com.strmr.tv.api.searchSources
import com.strmr.tv.api.sourceHealth
import com.strmr.tv.app.AppLog

/**
 * Switch Source: the title's ranked sources, as the source picker searches
 * them (`/api/indexers/search`, with debrid cache / usenet health), the playing one checked.
 * Choosing another closes the player and resumes it there at the current position; Back closes
 * the list. Nothing switches by itself.
 */
@Composable
fun SourceSwitchMenu(controller: PlayerController, onDone: () -> Unit, modifier: Modifier = Modifier) {
    val request = controller.request
    var results by remember { mutableStateOf<List<SourceResult>?>(null) }
    var error by remember { mutableStateOf<String?>(null) }
    val health = remember { mutableStateMapOf<Int, SourceHealth>() }
    val playing = controller.session.releaseName

    LaunchedEffect(Unit) {
        val query = SourceSwitchRules.query(request)
        try {
            val found = ApiClient.searchSources(query, request.title)
            found.forEach { r -> r.health?.let { health[r.index] = it } }
            results = found
            AppLog.i("SourceSwitch", "candidates query=\"$query\" count=${found.size} " +
                "current=${found.indexOfFirst { isCurrent(it, playing) }} playing=$playing")
        } catch (e: Exception) {
            error = e.message ?: "Couldn't load sources"
            AppLog.w("SourceSwitch", "candidate search failed: ${e.message}")
            return@LaunchedEffect
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

    val list = results
    val choices = buildList {
        // The playing release heads the list when the search doesn't return it (as Expo keeps it in its candidates).
        if (list != null && list.none { isCurrent(it, playing) } && playing != null) {
            add(MenuChoice(playing, "Playing", selected = true, onClick = onDone))
        }
        list?.forEach { r ->
            val current = isCurrent(r, playing)
            val detail = SourceSwitchRules.detail(r.attributes, r.sizeBytes, r.serviceType, r.indexer, health[r.index]?.state)
            add(MenuChoice(SourceSwitchRules.releaseName(r.title, r.attributes["raw_title"]),
                           if (current) "Playing · $detail" else detail, selected = current) {
                if (current) onDone() else {
                    onDone()
                    controller.switchSource(r)
                }
            })
        }
    }
    val empty = when {
        error != null -> error!!
        list == null -> "Searching sources…"
        else -> "No other sources found"
    }
    // Re-keyed once loaded, so the menu focuses the playing source then.
    key(list != null) { ChoiceMenu("Switch Source", choices, empty, modifier, width = 760.dp) }
}

private fun isCurrent(r: SourceResult, playing: String?) =
    SourceSwitchRules.isCurrent(r.title, r.attributes["raw_title"], playing)

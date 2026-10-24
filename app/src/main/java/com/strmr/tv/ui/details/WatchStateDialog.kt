package com.strmr.tv.ui.details

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.sp
import androidx.tv.material3.Icon
import androidx.tv.material3.ListItem
import androidx.tv.material3.Text
import com.strmr.tv.api.ApiClient
import com.strmr.tv.api.Episode
import com.strmr.tv.api.Season
import com.strmr.tv.api.WatchedScope
import com.strmr.tv.api.setWatched
import com.strmr.tv.ui.PanelSection
import com.strmr.tv.ui.SidePanelDialog
import kotlinx.coroutines.launch

/**
 * A series' watch state as a side panel, like the Live tab's category filter (and the tvOS
 * watch-state menu): the episode the page plays next and the whole-show choices (all, or all
 * but the most recent season) close it; each season toggles in place with the panel left up
 * (checked once every aired episode is watched), so several can be marked in one visit.
 * Whole-show and whole-season changes carry their scope so Trakt-style providers record them
 * with their own bulk calls. Only aired episodes are marked watched; unwatched applies to
 * everything. Episodes flip at once; [onChanged] runs after each change.
 */
@Composable
fun WatchStateDialog(model: DetailsModel, onChanged: suspend () -> Unit, onDismiss: () -> Unit) {
    val ordered = remember(model.seasons) { model.seasons.sortedBy { it.number } }
    val aired = remember(model.seasons) { ordered.associate { s -> s.id to s.episodes.filter(DetailsModel::hasAired) } }
    val regular = ordered.map { it.number }.filter { it > 0 }
    val latest = if (regular.size > 1) regular.max() else null
    val rest = ordered.filter { it.number != latest }
    var error by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()
    val first = remember { FocusRequester() }
    LaunchedEffect(Unit) { runCatching { first.requestFocus() } }

    fun apply(watched: Boolean, episodes: List<Episode>, watchedScope: WatchedScope?, close: Boolean) {
        if (episodes.isEmpty()) return
        val previous = episodes.associate { it.id to model.watchedOverrides[it.id] }
        episodes.forEach { model.watchedOverrides[it.id] = watched }
        // Closed only once it went through: the panel's scope ends with it.
        scope.launch {
            try {
                ApiClient.setWatched(watched, episodes, model.target, watchedScope)
                error = null
                onChanged()
                if (close) onDismiss()
            } catch (e: Exception) {
                previous.forEach { (id, value) -> if (value == null) model.watchedOverrides.remove(id) else model.watchedOverrides[id] = value }
                error = e.message
            }
        }
    }

    SidePanelDialog("Watch State", model.target.name, onDismiss) {
        error?.let { item { Text(it, fontSize = 14.sp, color = Color(0xFFFF6B6B)) } }
        model.primaryEpisode?.let { ep ->
            item {
                val watched = model.isWatched(ep)
                PanelSection("${ep.code} · ${ep.name}")
                Row(if (watched) "Mark as unwatched" else "Mark as watched", modifier = Modifier.focusRequester(first)) {
                    apply(!watched, listOf(ep), null, close = true)
                }
            }
        }
        item {
            val allAired = ordered.flatMap { aired[it.id].orEmpty() }
            val all = ordered.flatMap { it.episodes }
            val focus = if (model.primaryEpisode == null) Modifier.focusRequester(first) else Modifier
            PanelSection("All Episodes")
            Row("Mark all ${allAired.size} as watched", enabled = allAired.isNotEmpty(), modifier = focus) {
                apply(true, allAired, WatchedScope.SHOW, close = true)
            }
            if (latest != null) {
                val restAired = rest.flatMap { aired[it.id].orEmpty() }
                Row("All except Season $latest as watched", enabled = restAired.isNotEmpty()) {
                    apply(true, restAired, null, close = true)
                }
            }
            Row("Mark all ${all.size} as unwatched", enabled = all.isNotEmpty()) {
                apply(false, all, WatchedScope.SHOW, close = true)
            }
            if (latest != null) {
                val restAll = rest.flatMap { it.episodes }
                Row("All except Season $latest as unwatched", enabled = restAll.isNotEmpty()) {
                    apply(false, restAll, null, close = true)
                }
            }
        }
        if (ordered.isNotEmpty()) item { PanelSection("Seasons") }
        for (season in ordered) item(key = season.id) {
            SeasonToggle(season, aired[season.id].orEmpty(), model::isWatched) { w, eps -> apply(w, eps, seasonScope(season), close = false) }
        }
    }
}

/** Specials are not a season to Trakt-style providers; they are sent unscoped. */
private fun seasonScope(season: Season) = if (season.number > 0) WatchedScope.SEASON else null

/**
 * Watched (every aired episode) unmarks the whole season; otherwise its aired episodes are
 * marked, a partly watched season included.
 */
@Composable
private fun SeasonToggle(season: Season, aired: List<Episode>, isWatched: (Episode) -> Boolean, apply: (Boolean, List<Episode>) -> Unit) {
    val watchedCount = season.episodes.count(isWatched)
    val complete = aired.isNotEmpty() && aired.all(isWatched)
    val name = if (season.number == 0) "Specials" else "Season ${season.number}"
    ListItem(
        selected = complete,
        enabled = aired.isNotEmpty() || watchedCount > 0,
        onClick = { if (complete) apply(false, season.episodes) else apply(true, aired) },
        headlineContent = { Text(name, maxLines = 1, overflow = TextOverflow.Ellipsis) },
        supportingContent = if (watchedCount in 1 until season.episodes.size) {
            { Text("$watchedCount of ${season.episodes.size} watched") }
        } else null,
        trailingContent = if (complete) { { Icon(Icons.Filled.Check, contentDescription = "Watched") } } else null,
    )
}

@Composable
private fun Row(title: String, enabled: Boolean = true, modifier: Modifier = Modifier, action: () -> Unit) {
    ListItem(
        selected = false, onClick = action, enabled = enabled, modifier = modifier,
        headlineContent = { Text(title, maxLines = 1, overflow = TextOverflow.Ellipsis) },
    )
}

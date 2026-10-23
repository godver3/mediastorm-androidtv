package com.strmr.tv.ui.details

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Done
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.tv.material3.Icon
import androidx.tv.material3.ListItem
import androidx.tv.material3.Text
import com.strmr.tv.api.ApiClient
import com.strmr.tv.api.Episode
import com.strmr.tv.api.Season
import com.strmr.tv.api.WatchedScope
import com.strmr.tv.api.setWatched
import com.strmr.tv.ui.AppIcons
import kotlinx.coroutines.launch

/**
 * Bulk watched / unwatched for a series, laid out like the tvOS sheet (and the Expo app's
 * bulk-watch modal): all episodes (optionally leaving out the latest season), the episode
 * the page plays next, and each season. Whole-show and whole-season changes carry their
 * scope so Trakt-style providers record them with their own bulk calls. Only aired episodes
 * are marked watched; unwatched applies to everything. [onChanged] runs after each change.
 */
@Composable
fun WatchStateDialog(model: DetailsModel, onChanged: suspend () -> Unit, onDismiss: () -> Unit) {
    val ordered = remember(model.seasons) { model.seasons.sortedBy { it.number } }
    val aired = remember(model.seasons) { ordered.associate { s -> s.id to s.episodes.filter(DetailsModel::hasAired) } }
    val latest = ordered.map { it.number }.filter { it > 0 }.maxOrNull()
    var excludeLatest by remember { mutableStateOf(false) }
    val busy = remember { mutableStateOf(setOf<String>()) }
    val done = remember { mutableStateOf(setOf<String>()) }
    var error by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()
    val first = remember { FocusRequester() }
    LaunchedEffect(Unit) { runCatching { first.requestFocus() } }

    val included = ordered.filter { !excludeLatest || it.number != latest }
    val allEpisodes = included.flatMap { it.episodes }
    val allAired = included.flatMap { aired[it.id].orEmpty() }

    fun apply(watched: Boolean, episodes: List<Episode>, watchedScope: WatchedScope?, key: String, close: Boolean) {
        if (key in busy.value) return
        busy.value += key
        scope.launch {
            try {
                ApiClient.setWatched(watched, episodes, model.target, watchedScope)
                error = null
                done.value += key
                onChanged()
                if (close) onDismiss()
            } catch (e: Exception) {
                error = e.message
            } finally {
                busy.value -= key
            }
        }
    }

    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        // Near-opaque, edge to edge: the details page underneath only distracts.
        Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.94f)), contentAlignment = Alignment.TopCenter) {
            LazyColumn(
                Modifier.widthIn(max = 560.dp),
                contentPadding = PaddingValues(vertical = 40.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                item {
                    Text("Mark Episodes as Watched", fontSize = 24.sp, fontWeight = FontWeight.SemiBold)
                    Text(model.target.name, fontSize = 15.sp, color = Color.White.copy(alpha = 0.6f))
                    error?.let { Text(it, fontSize = 14.sp, color = Color(0xFFFF6B6B)) }
                }
                item { Section("All Episodes") }
                if (latest != null && ordered.count { it.number > 0 } > 1) {
                    item {
                        WatchRow("exclude" in busy.value, if (excludeLatest) Icons.Filled.CheckCircle else AppIcons.Circle, "Exclude most recent season",
                            modifier = Modifier.focusRequester(first)) { excludeLatest = !excludeLatest }
                    }
                }
                item {
                    val showScope = if (excludeLatest) null else WatchedScope.SHOW
                    val focus = if (latest != null && ordered.count { it.number > 0 } > 1) Modifier else Modifier.focusRequester(first)
                    WatchRow("all-watched" in busy.value, Icons.Filled.CheckCircle, "Mark all ${allAired.size} episodes as watched", allAired.isNotEmpty(), focus) {
                        apply(true, allAired, showScope, "all-watched", close = true)
                    }
                    WatchRow("all-unwatched" in busy.value, Icons.Filled.Close, "Mark all ${allEpisodes.size} episodes as unwatched", allEpisodes.isNotEmpty()) {
                        apply(false, allEpisodes, showScope, "all-unwatched", close = true)
                    }
                }
                model.primaryEpisode?.let { ep ->
                    item {
                        val watched = model.isWatched(ep)
                        Section("Current Episode · ${ep.code} · ${ep.name}")
                        WatchRow("current-watched" in busy.value, Icons.Filled.Done, if (watched) "Already watched" else "Mark as watched") {
                            apply(true, listOf(ep), null, "current-watched", close = true)
                        }
                        WatchRow("current-unwatched" in busy.value, Icons.Filled.Close, if (watched) "Mark as unwatched" else "Already unwatched") {
                            apply(false, listOf(ep), null, "current-unwatched", close = true)
                        }
                    }
                }
                if (ordered.isNotEmpty()) item { Section("By Season") }
                for (season in ordered) item(key = season.id) {
                    SeasonRows(season, aired[season.id].orEmpty(), busy.value, done.value) { w, eps, s, k -> apply(w, eps, s, k, close = false) }
                }
                item { WatchRow("close" in busy.value, Icons.Filled.Close, "Close", modifier = Modifier.padding(top = 20.dp)) { onDismiss() } }
            }
        }
    }
}

@Composable
private fun SeasonRows(
    season: Season,
    toWatch: List<Episode>,
    busy: Set<String>,
    done: Set<String>,
    apply: (Boolean, List<Episode>, WatchedScope?, String) -> Unit,
) {
    val watchKey = "${season.id}-watched"
    val unwatchKey = "${season.id}-unwatched"
    // Specials are not a season to Trakt-style providers; send them unscoped.
    val scope = if (season.number > 0) WatchedScope.SEASON else null
    fun count(n: Int) = if (n == 1) "1 episode" else "$n episodes"
    Text(if (season.number == 0) "Specials" else season.name.ifEmpty { "Season ${season.number}" },
         fontSize = 17.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(top = 12.dp, bottom = 4.dp))
    WatchRow(watchKey in busy, if (watchKey in done) Icons.Filled.CheckCircle else Icons.Filled.Done,
        if (watchKey in done) "Marked as watched" else "Mark ${count(toWatch.size)} as watched", toWatch.isNotEmpty()) {
        apply(true, toWatch, scope, watchKey)
    }
    WatchRow(unwatchKey in busy, if (unwatchKey in done) Icons.Filled.CheckCircle else Icons.Filled.Close,
        if (unwatchKey in done) "Marked as unwatched" else "Mark ${count(season.episodes.size)} as unwatched", season.episodes.isNotEmpty()) {
        apply(false, season.episodes, scope, unwatchKey)
    }
}

@Composable
private fun WatchRow(busy: Boolean, icon: ImageVector, title: String, enabled: Boolean = true, modifier: Modifier = Modifier, action: () -> Unit) {
    ListItem(
        selected = false, onClick = action, enabled = enabled, modifier = modifier,
        leadingContent = {
            if (busy) CircularProgressIndicator(Modifier.size(18.dp), color = Color.White, strokeWidth = 2.dp)
            else Icon(icon, contentDescription = null, modifier = Modifier.size(20.dp))
        },
        headlineContent = { Text(title) },
    )
}

@Composable
private fun Section(title: String) {
    Text(title, fontSize = 15.sp, fontWeight = FontWeight.SemiBold, color = Color.White.copy(alpha = 0.6f), modifier = Modifier.padding(top = 20.dp, bottom = 4.dp))
}

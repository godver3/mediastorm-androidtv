package com.strmr.mobile.ui.sports

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyGridScope
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.strmr.mobile.ui.shell.LocalNavigator
import com.strmr.mobile.ui.shell.Navigator
import com.strmr.mobile.ui.shell.PageScaffold
import com.strmr.mobile.ui.shell.Route
import com.strmr.mobile.ui.sports.cycling.SportsCyclingCard
import com.strmr.mobile.ui.sports.race.SportsRaceCard
import com.strmr.tv.sports.SportsHubFilters
import com.strmr.tv.ui.sports.SportsFilter
import com.strmr.tv.ui.sports.SportsHub
import com.strmr.tv.ui.sports.SportsHubItem
import com.strmr.tv.ui.sports.SportsHubState
import com.strmr.tv.ui.sports.SportsMyTeams
import com.strmr.tv.ui.sports.SportsSection
import com.strmr.tv.ui.sports.SportsSpoilers
import com.strmr.tv.ui.sports.prefetchSportsLogos
import kotlinx.coroutines.delay

/**
 * The Sports tab on a phone (the iPhone's `MobileSportsView`): All / Live / Upcoming / Results,
 * chips for the day, sport, competition, Following and My Teams, a search over the cards, then
 * a section of cards per state (Live Now, Upcoming, Results, Schedule Updates), followed
 * competitions and pinned sports first, in a grid one card wide on a phone held upright. The
 * top bar holds Hide Scores and Customize. A game opens its page, a race session its weekend's,
 * a cycling stage its race's.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SportsScreen(asPage: Boolean) {
    val state = SportsHub.state
    val navigator = LocalNavigator.current
    var query by rememberSaveable { mutableStateOf("") }

    LaunchedEffect(Unit) { state.loadPreferences() }
    LaunchedEffect(state.day, state.retries) {
        while (true) {
            state.load()
            delay(state.refreshSeconds * 1000)
        }
    }
    LaunchedEffect(state.racingEnabled, state.retries) { if (state.racingEnabled) state.pollRaces() }
    LaunchedEffect(state.cyclingEnabled, state.retries) { if (state.cyclingEnabled) state.pollCycling() }
    LaunchedEffect(state.games) { state.loadRecords() }

    PageScaffold("Sports", showBack = asPage, actions = { SportsHubMenu(state, navigator) }) { padding ->
        PullToRefreshBox(
            isRefreshing = state.loading && state.games.isNotEmpty(), onRefresh = { state.retry() },
            modifier = Modifier.fillMaxSize().padding(padding),
        ) {
            val sections = SportsHubSearch.filter(state.sections, query) { state.leagueName(it) }
            LazyVerticalGrid(
                columns = GridCells.Adaptive(300.dp),
                contentPadding = PaddingValues(start = PageMargin, end = PageMargin, bottom = 32.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier.fillMaxSize(),
            ) {
                full("controls") { SportsHubControls(state, navigator) }
                full("search") {
                    OutlinedTextField(
                        query, { query = it }, singleLine = true, modifier = Modifier.fillMaxWidth(),
                        placeholder = { Text("Teams, competitions and events") },
                        leadingIcon = { Icon(Icons.Filled.Search, null) },
                        trailingIcon = if (query.isNotEmpty()) { { IconButton(onClick = { query = "" }) { Icon(Icons.Filled.Close, "Clear") } } } else null,
                    )
                }
                full("summary") { SportsFilterSummary(state, navigator) }
                content(state, sections, query, navigator)
            }
        }
    }
}

private fun LazyGridScope.full(key: String, content: @Composable () -> Unit) =
    item(key = key, span = { GridItemSpan(maxLineSpan) }) { content() }

private fun LazyGridScope.content(state: SportsHubState, sections: List<SportsSection>, query: String, navigator: Navigator) {
    val pending = state.racesPending || state.cyclingPending
    when {
        (state.loading && state.games.isEmpty() || pending) && sections.isEmpty() -> full("loading") {
            Box(Modifier.fillMaxWidth().padding(top = 60.dp), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
        }
        state.error != null && state.sections.isEmpty() -> full("error") { Notice(state.error.orEmpty(), "Try Again") { state.retry() } }
        sections.isEmpty() -> full("empty") {
            if (query.isNotBlank()) {
                Notice("No matching events. Try another team, competition or event.", null) {}
            } else {
                val filtered = state.sport != SportsHubFilters.ALL || state.league != SportsHubFilters.ALL
                val narrowed = state.followingOnly || state.myTeamsOnly
                Notice(emptyText(state, filtered), if (filtered) "Clear Filters" else if (narrowed) "Show All Events" else null) {
                    if (filtered) state.clearFilters() else {
                        state.followingOnly = false
                        state.myTeamsOnly = false
                    }
                }
            }
        }
        else -> {
            val delayed = listOfNotNull(
                state.error?.let { "Scores may be delayed: $it" },
                "Racing updates delayed or unavailable".takeIf { state.racingDelayed },
                "Cycling updates delayed or unavailable".takeIf { state.cyclingDelayed },
            )
            if (delayed.isNotEmpty()) full("delayed") {
                Text(delayed.joinToString(" · "), color = SportsAmber, style = MaterialTheme.typography.bodySmall)
            }
            sections.forEach { section ->
                full("h:${section.key}") { SectionHeader(section) }
                items(section.items, key = { "${section.key}:${it.key}" }) { item -> HubCard(item, state, navigator) }
            }
        }
    }
}

@Composable
private fun SectionHeader(section: SportsSection) {
    val context = LocalContext.current
    LaunchedEffect(section.items.map { it.key }) {
        prefetchSportsLogos(context, section.items.mapNotNull { (it as? SportsHubItem.Match)?.game })
    }
    Row(Modifier.padding(top = 8.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        if (section.key == "live") Box(Modifier.size(8.dp).clip(CircleShape).background(SportsLive))
        Text(section.title, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
        Text("${section.items.size}", style = MaterialTheme.typography.titleSmall, color = SportsMuted)
    }
}

@Composable
private fun HubCard(item: SportsHubItem, state: SportsHubState, navigator: Navigator) {
    val spoilers = SportsSpoilers.hidden
    when (item) {
        is SportsHubItem.Match -> SportsGameCard(
            item.game, state.leagueName(item.league), state.kind(item.game), spoilers,
            onClick = { navigator.push(Route.SportsGame(item.game.id)) }, stale = item.league in state.staleLeagues,
        )
        is SportsHubItem.Race -> SportsRaceCard(
            item.listing, state.leagueName(item.league), spoilers,
            onClick = { navigator.push(Route.SportsRace(item.listing.weekend.id, item.listing.session.id)) },
        )
        is SportsHubItem.Cycling -> SportsCyclingCard(
            item.listing, spoilers, onClick = { navigator.push(Route.SportsCycling(item.listing.race.id, item.listing.stage?.id)) },
        )
    }
}

@Composable
private fun Notice(text: String, action: String?, onAction: () -> Unit) {
    Column(Modifier.fillMaxWidth().padding(top = 48.dp), horizontalAlignment = Alignment.CenterHorizontally,
           verticalArrangement = Arrangement.spacedBy(16.dp)) {
        Text(text, color = SportsMuted, textAlign = TextAlign.Center)
        if (action != null) Button(onClick = onAction) { Text(action) }
    }
}

private fun emptyText(state: SportsHubState, filtered: Boolean): String {
    if (state.myTeamsOnly && state.teamIds.isEmpty()) return SportsMyTeams.NO_TEAMS
    val day = state.day.label.lowercase()
    if (filtered) return "Nothing in this selection $day."
    if (state.followingOnly) return "Nothing from the competitions you follow $day."
    return when (state.filter) {
        SportsFilter.LIVE -> "Nothing is live right now."
        SportsFilter.UPCOMING -> "Nothing else is scheduled $day."
        SportsFilter.RESULTS -> "No results yet $day."
        SportsFilter.ALL -> "No events $day."
    }
}

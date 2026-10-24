package com.strmr.tv.ui.sports

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.foundation.background
import androidx.compose.foundation.focusGroup
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.tv.material3.Button
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import com.strmr.tv.ui.LocalNavigator
import com.strmr.tv.ui.Navigator
import com.strmr.tv.ui.Page
import com.strmr.tv.ui.PageMargin
import com.strmr.tv.sports.SportsHubFilters
import com.strmr.tv.ui.sports.cycling.SportsCyclingCard
import com.strmr.tv.ui.sports.race.SportsRaceCard
import kotlinx.coroutines.delay
import java.time.Duration
import java.time.Instant

/**
 * The Sports tab, a native take on the Expo TV hub: filters, day, sport, league and options
 * across the top ([SportsHubControls]), then a row of events per state (Live Now, Upcoming,
 * Results, Schedule Updates), followed competitions and pinned sports first in each. A game opens
 * its page ([com.strmr.tv.ui.sports.game.SportsGameScreen]), a race session its weekend's
 * ([com.strmr.tv.ui.sports.race.SportsRaceScreen]) and a cycling stage its race's
 * ([com.strmr.tv.ui.sports.cycling.SportsCyclingScreen]), where they are watched.
 */
@Composable
fun SportsScreen() {
    val state = SportsHub.state
    val navigator = LocalNavigator.current

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

    // Below the floating top bar and clipped there, so the title scrolls away under nothing.
    LazyColumn(
        Modifier.fillMaxSize().padding(top = 76.dp).clipToBounds(),
        contentPadding = PaddingValues(top = 4.dp, bottom = 40.dp),
        verticalArrangement = Arrangement.spacedBy(22.dp),
    ) {
        item {
            Row(Modifier.padding(horizontal = PageMargin), verticalAlignment = Alignment.CenterVertically) {
                Text("Sports", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.SemiBold)
                Spacer(Modifier.weight(1f))
                state.updatedAt?.let { Text(freshness(it), color = SportsMuted, fontSize = 13.sp) }
            }
        }
        item { SportsHubControls(state, navigator) }
        item { SportsFilterSummary(state, navigator) }
        val sections = state.sections
        val pending = state.racesPending || state.cyclingPending
        when {
            (state.loading && state.games.isEmpty() || pending) && sections.isEmpty() -> item {
                Box(Modifier.fillMaxWidth().padding(top = 60.dp), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(Modifier.size(28.dp), color = androidx.compose.ui.graphics.Color.White, strokeWidth = 2.dp)
                }
            }
            // Races can still show when the scoreboard fails; the "delayed" line says so.
            state.error != null && sections.isEmpty() -> item { Notice(state.error.orEmpty(), "Try Again") { state.retry() } }
            sections.isEmpty() -> item {
                val filtered = state.sport != SportsHubFilters.ALL || state.league != SportsHubFilters.ALL
                Notice(emptyText(state, filtered), if (filtered) "Clear Filters" else if (state.followingOnly) "Show All Events" else null) {
                    if (filtered) state.clearFilters() else state.followingOnly = false
                }
            }
            else -> {
                state.error?.let { item { Text("Scores may be delayed: $it", color = SportsAmber, fontSize = 13.sp, modifier = Modifier.padding(horizontal = PageMargin)) } }
                val delayed = listOfNotNull(
                    "Racing updates delayed or unavailable".takeIf { state.racingDelayed },
                    "Cycling updates delayed or unavailable".takeIf { state.cyclingDelayed },
                )
                if (delayed.isNotEmpty()) item {
                    Text(delayed.joinToString(" · "), color = SportsAmber, fontSize = 13.sp, modifier = Modifier.padding(horizontal = PageMargin))
                }
                items(sections, key = { it.key }) { section ->
                    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Row(Modifier.padding(horizontal = PageMargin), verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            if (section.key == "live") Box(Modifier.size(8.dp).clip(CircleShape).background(SportsLive))
                            Text(section.title, fontSize = 18.sp, fontWeight = FontWeight.SemiBold)
                            Text("${section.items.size}", fontSize = 14.sp, color = SportsMuted)
                        }
                        SectionRow(section, state, navigator)
                    }
                }
            }
        }
    }
}

/**
 * A section's cards. A lazy row keeps its first visible card in place when cards arrive before
 * it (race sessions land after the games); until the remote is in the row, it goes back to the
 * start instead, so the newest results and live events show. Coming back to the row lands on the
 * card last focused there when it is still on screen ([RowFocusMemory]).
 */
@Composable
private fun SectionRow(section: SportsSection, state: SportsHubState, navigator: Navigator) {
    val rowState = rememberLazyListState()
    val memory = remember { RowFocusMemory() }
    var focused by remember { mutableStateOf(false) }
    LaunchedEffect(section.items.firstOrNull()?.key) {
        if (!focused && rowState.firstVisibleItemIndex > 0) rowState.scrollToItem(0)
    }
    // Every card in the row as tall as the tallest seen (race and cycling cards list finishers).
    val spoilers = SportsSpoilers.hidden
    var tallest by remember(section.items.map { it.key }, spoilers) { mutableIntStateOf(0) }
    val density = LocalDensity.current
    val even = Modifier.onSizeChanged { if (it.height > tallest) tallest = it.height }
        .heightIn(min = with(density) { tallest.toDp() })
    // The game card takes the height inside (tv Card's own column drops a minimum height).
    CompositionLocalProvider(LocalSportsCardMinHeight provides with(density) { tallest.toDp() }) {
        LazyRow(
            Modifier.focusProperties { onEnter = { memory.restore() } }.focusGroup().onFocusChanged { focused = it.hasFocus },
            state = rowState,
            contentPadding = PaddingValues(horizontal = PageMargin, vertical = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(18.dp),
        ) {
            items(section.items, key = { it.key }) { item ->
                val even = even.then(memory.track(item.key))
                when (item) {
                    is SportsHubItem.Match -> SportsGameCard(
                        item.game, state.leagueName(item.league), state.kind(item.game), spoilers,
                        onClick = { navigator.open(Page.SportsGame(item.game.id)) }, modifier = even,
                        stale = item.league in state.staleLeagues,
                    )
                    is SportsHubItem.Race -> SportsRaceCard(
                        item.listing, state.leagueName(item.league), spoilers,
                        onClick = { navigator.open(Page.SportsRace(item.listing.weekend.id, item.listing.session.id)) }, modifier = even,
                    )
                    is SportsHubItem.Cycling -> SportsCyclingCard(
                        item.listing, spoilers,
                        onClick = { navigator.open(Page.SportsCycling(item.listing.race.id, item.listing.stage?.id)) }, modifier = even,
                    )
                }
            }
        }
    }
}

/**
 * The card a row had focus on, so focus coming back to the row returns to it (what
 * `focusRestorer()` does) without pinning it. A restorer keeps its last card pinned, and with it
 * the card's row in the hub's list, while focus is elsewhere; when a filter or refresh drops that
 * card or row, the pin outlives its key and Compose resolves it to the old index, which another
 * pin can share: the list then places one item twice and the app dies ("Place was called on a node
 * which was placed already"). Unpinned, a card that left the screen is simply gone and focus lands
 * on the nearest card instead.
 */
private class RowFocusMemory {
    private var last: String? = null
    private val requesters = HashMap<String, FocusRequester>()

    fun track(key: String): Modifier =
        Modifier.focusRequester(requesters.getOrPut(key) { FocusRequester() }).onFocusChanged { if (it.isFocused) last = key }

    fun restore() {
        val requester = last?.let(requesters::get) ?: return
        runCatching { requester.requestFocus() }
    }
}

/** The hub's state, kept across tab switches and game pages for the app's life. */
object SportsHub {
    val state = SportsHubState()
}

@Composable
private fun Notice(text: String, action: String?, onAction: () -> Unit) {
    Column(Modifier.fillMaxWidth().padding(top = 60.dp), horizontalAlignment = Alignment.CenterHorizontally,
           verticalArrangement = Arrangement.spacedBy(16.dp)) {
        Text(text, color = SportsMuted)
        if (action != null) Button(onClick = onAction) { Text(action) }
    }
}

private fun emptyText(state: SportsHubState, filtered: Boolean): String {
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

private fun freshness(updated: Instant): String {
    val minutes = Duration.between(updated, Instant.now()).toMinutes()
    return if (minutes < 1) "Updated just now" else "Updated $minutes min ago"
}

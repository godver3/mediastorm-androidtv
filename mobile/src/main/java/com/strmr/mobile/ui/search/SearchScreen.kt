package com.strmr.mobile.ui.search

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.strmr.mobile.ui.components.rememberCardOpener
import com.strmr.mobile.ui.lists.GridSpacing
import com.strmr.mobile.ui.lists.GridSpinner
import com.strmr.mobile.ui.lists.GridStatus
import com.strmr.mobile.ui.lists.PosterColumns
import com.strmr.mobile.ui.lists.fullWidth
import com.strmr.mobile.ui.lists.gridPadding
import com.strmr.mobile.ui.shell.LocalNavigator
import com.strmr.mobile.ui.shell.PageScaffold
import com.strmr.mobile.ui.shell.Route
import com.strmr.tv.api.ApiClient
import com.strmr.tv.api.PersonRef
import com.strmr.tv.app.AppSession
import com.strmr.tv.home.HomeCard
import com.strmr.tv.home.HomeLink
import com.strmr.tv.api.Title
import com.strmr.mobile.ui.components.PosterCard
import com.strmr.mobile.ui.components.TitleMenu
import androidx.compose.ui.unit.Dp
import com.strmr.tv.search.SearchRanking
import com.strmr.tv.search.SearchState

/**
 * Search on a phone, a port of the Expo mobile search on the shared [SearchState] (as the
 * iPhone app): Top 10 Today before anything is typed, then the movie, series and person
 * searches merged by relevance. Scopes narrow to movies or shows (people only show under All);
 * the empty field offers recent searches. Curated kids profiles can't search.
 */
@Composable
fun SearchScreen(asPage: Boolean) {
    val profile by AppSession.profile.collectAsState()
    PageScaffold("Search", showBack = asPage, large = false) { padding ->
        if (profile?.isKidsCurated == true) {
            Column(Modifier.padding(padding)) {
                GridStatus(
                    "Search is unavailable", "Content is limited to the curated lists selected for this profile.", Icons.Filled.Lock,
                )
            }
            return@PageScaffold
        }
        SearchContent(Modifier.padding(padding).fillMaxSize())
    }
}

@Composable
private fun SearchContent(modifier: Modifier) {
    var query by rememberSaveable { mutableStateOf("") }
    var scope by rememberSaveable { mutableStateOf(SearchScope.ALL) }
    val state = remember { SearchState() }
    val recents = remember(ApiClient.profileId) { RecentSearches() }
    val normalized = query.trim()
    val hasQuery = normalized.isNotEmpty()
    val open = rememberCardOpener()
    val navigator = LocalNavigator.current

    LaunchedEffect(normalized, state.retryToken) { state.search(normalized) }
    LaunchedEffect(Unit) { state.loadTopTen() }

    // A list can name a title twice; the grid needs unique IDs.
    val titles = state.titles(normalized).distinctBy { it.id }.filter { scope.includes(it.mediaType) }
    val cards = titles.mapIndexed { i, t -> searchCard(t).let { if (hasQuery) it else it.ranked(i + 1) } }
    val loading = if (hasQuery) state.loading else state.topTenLoading
    val notice = state.notice.takeIf { hasQuery && state.resultQuery == normalized && !state.loading }
    val gridState = rememberLazyGridState()
    // A new query or scope starts the grid at its top.
    LaunchedEffect(normalized, scope) { gridState.scrollToItem(0) }

    Column(modifier) {
        SearchField(
            query, { query = SearchRanking.clamp(it) },
            recents = recents.items,
            onSubmit = { recents.add(it) },
            onClearRecents = recents::clear,
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
        )
        Row(Modifier.padding(horizontal = 16.dp, vertical = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            SearchScope.entries.forEach { s ->
                FilterChip(selected = scope == s, onClick = { scope = s }, label = { Text(s.label) })
            }
        }
        LazyVerticalGrid(
            PosterColumns, state = gridState, contentPadding = gridPadding(top = 4.dp),
            horizontalArrangement = GridSpacing, verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            if (notice != null) fullWidth("notice") {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Icon(Icons.Filled.Warning, null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text(notice, Modifier.weight(1f), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    TextButton(onClick = state::retry) { Text("Retry") }
                }
            }
            if (!hasQuery && cards.isNotEmpty()) fullWidth("top-ten") {
                Text("Top 10 Today", style = MaterialTheme.typography.titleMedium)
            }
            if (!loading) {
                items(cards.size, key = { cards[it].id }, contentType = { if (cards[it].link is HomeLink.Person) "person" else "poster" }) { i ->
                    val card = cards[i]
                    val person = (card.link as? HomeLink.Person)?.person
                    if (person != null) {
                        PersonCell(person) { navigator.push(Route.Person(person)) }
                    } else {
                        PosterCard(
                            card, Dp.Unspecified, onClick = { open(card) }, onLongClick = { TitleMenu.show(card) },
                            modifier = Modifier.fillMaxWidth(),
                        )
                    }
                }
            }
            when {
                loading -> fullWidth("spinner") { GridSpinner() }
                cards.isNotEmpty() -> Unit
                hasQuery && normalized.length >= SearchRanking.MIN_QUERY_LENGTH && state.resultQuery == normalized && notice == null ->
                    fullWidth("none") { GridStatus("No results for “$normalized”", "Check the spelling or try another search.", Icons.Filled.Search) }
                !hasQuery -> fullWidth("start") { GridStatus("Search MediaStorm", "Find movies, shows and people.", Icons.Filled.Search) }
            }
        }
    }
}

/** All, Movies or TV Shows; people only under All (as Expo). */
private enum class SearchScope(val label: String) {
    ALL("All"), MOVIES("Movies"), SHOWS("TV Shows");

    fun includes(mediaType: String?) = when (this) {
        ALL -> true
        MOVIES -> mediaType == "movie"
        SHOWS -> mediaType == "series" || mediaType == "tv"
    }
}

/** A search hit or Top 10 title as a card: a person's opens their page. */
private fun searchCard(title: Title): HomeCard {
    val tmdbId = title.tmdbId
    if (title.mediaType != "person" || tmdbId == null) return HomeCard.from(title)
    return HomeCard(
        id = title.id,
        link = HomeLink.Person(PersonRef(tmdbId.toInt(), title.name, title.poster?.url)),
        title = title.name,
        subtitle = "Person",
        posterUrl = title.poster?.url,
    )
}

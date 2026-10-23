package com.strmr.tv.ui.search

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.focus.focusRestorer
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.tv.material3.DenseListItem
import androidx.tv.material3.ExperimentalTvMaterial3Api
import androidx.tv.material3.Icon
import androidx.tv.material3.OutlinedButton
import androidx.tv.material3.Text
import com.strmr.tv.api.PersonRef
import com.strmr.tv.api.Title
import com.strmr.tv.app.AppSession
import com.strmr.tv.home.CardBadge
import com.strmr.tv.home.HomeCard
import com.strmr.tv.home.HomeLink
import com.strmr.tv.search.SearchRanking
import com.strmr.tv.search.SearchState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Search
import com.strmr.tv.ui.PageMargin
import com.strmr.tv.ui.PosterGrid
import com.strmr.tv.ui.fullWidth
import com.strmr.tv.ui.onboarding.FormField

/**
 * The Search tab, a port of the Expo TV search (`TVSearchScreen`, as the tvOS app has it):
 * the field (the system keyboard, voice included where the IME has it), then a rail on the
 * left (the query and the result names, while there is a query) beside a poster grid (Top 10
 * Today before typing, then movies, series and people merged by relevance). Without the rail
 * (before typing, and always on kids profiles) the grid has 6 columns; curated kids
 * profiles can't search. The page stays composed under a details page,
 * so the query, results and focus are there on Back.
 */
@Composable
fun SearchScreen() {
    val profile by AppSession.profile.collectAsState()
    if (profile?.isKidsCurated == true) {
        CuratedSearchNotice()
        return
    }
    var query by rememberSaveable { mutableStateOf("") }
    val state = remember { SearchState() }
    val normalized = query.trim()
    val hasQuery = normalized.isNotEmpty()
    // Suggestions while there is a query; Expo's `canProfileAccessSearchGroupings`: none on kids profiles.
    val showsRail = hasQuery && profile?.isKidsProfile != true

    LaunchedEffect(normalized, state.retryToken) { state.search(normalized) }
    LaunchedEffect(Unit) { state.loadTopTen() }

    // A list can name a title twice; the grid needs unique IDs.
    val titles = state.titles(normalized).distinctBy { it.id }
    // Top 10 Today carries its ranks, as its Home row does.
    val cards = titles.mapIndexed { i, t -> searchCard(t).let { if (hasQuery) it else it.copy(badge = CardBadge("#${i + 1}")) } }
    val loading = if (hasQuery) state.loading else state.topTenLoading
    val notice = state.notice.takeIf { hasQuery && state.resultQuery == normalized && !state.loading }

    // Below the tab bar, clipped there.
    Column(Modifier.fillMaxSize().padding(top = 76.dp).clipToBounds()) {
        Box(Modifier.padding(horizontal = PageMargin).widthIn(max = 640.dp)) {
            FormField(
                query, { query = SearchRanking.clamp(it) },
                label = "Search", placeholder = "Search movies, shows, genres, people...", imeAction = ImeAction.Search,
            )
        }
        Box(Modifier.padding(start = PageMargin, end = PageMargin, top = 14.dp).fillMaxWidth().height(1.dp).background(Color.White.copy(alpha = 0.12f)))
        Row(Modifier.fillMaxSize()) {
            if (showsRail) {
                SearchRail(
                    entries = SearchRanking.suggestions(normalized, titles).map { RailEntry(it) { query = it } },
                    modifier = Modifier.padding(start = PageMargin - 12.dp, top = 12.dp).width(220.dp).fillMaxHeight(),
                )
            }
            // A new query starts the grid at its top.
            key(if (hasQuery) "query:$normalized" else "top-ten") {
                PosterGrid(
                    cards,
                    modifier = Modifier.weight(1f),
                    columns = GridCells.Fixed(if (showsRail) 4 else 6),
                    contentPadding = PaddingValues(start = if (showsRail) 28.dp else PageMargin, end = PageMargin, top = 20.dp, bottom = 32.dp),
                    header = {
                        if (!hasQuery && cards.isNotEmpty()) {
                            fullWidth("top-ten") { Text("Top 10 Today", fontSize = 20.sp, fontWeight = FontWeight.SemiBold) }
                        }
                        if (notice != null) {
                            fullWidth("notice") {
                                Row(horizontalArrangement = Arrangement.spacedBy(20.dp), verticalAlignment = Alignment.CenterVertically) {
                                    Text(notice, color = Color.White.copy(alpha = 0.7f))
                                    OutlinedButton(onClick = state::retry) { Text("Retry") }
                                }
                            }
                        }
                    },
                    footer = {
                        if (loading) {
                            fullWidth("spinner") {
                                Box(Modifier.fillMaxWidth().padding(top = 60.dp), contentAlignment = Alignment.Center) {
                                    CircularProgressIndicator(Modifier.size(28.dp), color = Color.White, strokeWidth = 2.dp)
                                }
                            }
                        } else if (cards.isEmpty() && (notice == null || !hasQuery)) {
                            fullWidth("empty") {
                                Column(Modifier.fillMaxWidth().padding(top = 60.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(12.dp)) {
                                    Icon(Icons.Default.Search, contentDescription = null, tint = Color.White.copy(alpha = 0.3f), modifier = Modifier.size(36.dp))
                                    Text("Enter another term to see matching titles", color = Color.White.copy(alpha = 0.6f))
                                }
                            }
                        }
                    },
                )
            }
        }
    }
}

/** A search hit or Top 10 title as a grid card: a person's photo opens their page. */
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

private class RailEntry(val label: String, val onClick: () -> Unit)

/** The rail beside the results: the query and the result names. */
@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
private fun SearchRail(entries: List<RailEntry>, modifier: Modifier) {
    LazyColumn(modifier.focusRestorer(), contentPadding = PaddingValues(vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
        items(entries, key = { it.label }) { entry ->
            DenseListItem(
                selected = false,
                onClick = entry.onClick,
                headlineContent = {
                    Text(entry.label, maxLines = 1, overflow = TextOverflow.Ellipsis, fontSize = 14.sp)
                },
            )
        }
    }
}

/** Expo's notice for kids profiles limited to curated lists, which can't search. */
@Composable
private fun CuratedSearchNotice() {
    Box(Modifier.fillMaxSize().padding(top = 76.dp), contentAlignment = Alignment.Center) {
        Row(horizontalArrangement = Arrangement.spacedBy(24.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Default.Lock, contentDescription = null, tint = Color(0xFFFFD60A), modifier = Modifier.size(40.dp))
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text("Search is unavailable for this profile", fontSize = 20.sp, fontWeight = FontWeight.SemiBold)
                Text("Content is limited to the curated lists selected for this profile.", color = Color.White.copy(alpha = 0.6f))
            }
        }
    }
}

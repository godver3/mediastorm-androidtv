package com.strmr.tv.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyGridScope
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.focusRestorer
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.tv.material3.Card
import androidx.tv.material3.CardDefaults
import androidx.tv.material3.Text
import coil.compose.AsyncImage
import com.strmr.tv.api.Artwork
import com.strmr.tv.home.HomeCard
import com.strmr.tv.home.HomeLink
import com.strmr.tv.ui.home.Badge
import com.strmr.tv.ui.home.ProgressLine
import com.strmr.tv.ui.library.rememberLibraryOpener

/**
 * A grid of poster cards that open details (or a person's page), as the tvOS Search, Watchlist and list grids
 * draw them: title and line under each poster, long press for the title menu.
 * `header` items span the grid; `onNearEnd` pages more in.
 */
@Composable
fun PosterGrid(
    cards: List<HomeCard>,
    modifier: Modifier = Modifier,
    first: FocusRequester? = null,
    contentPadding: PaddingValues = PaddingValues(horizontal = PageMargin, vertical = 24.dp),
    onNearEnd: (() -> Unit)? = null,
    /** Long-press choices besides the shared ones (Remove from List in a custom list's grid). */
    extraActions: (HomeCard) -> List<DialogChoice> = { emptyList() },
    /** The cards are Continue Watching's (their menu adds Hide from Continue Watching and Set Aside). */
    continueWatching: Boolean = false,
    /** Fixed column counts (Search: 4 beside its rail, 6 without); adaptive by default. */
    columns: GridCells = GridCells.Adaptive(136.dp),
    header: LazyGridScope.() -> Unit = {},
    footer: LazyGridScope.() -> Unit = {},
) {
    val navigator = LocalNavigator.current
    val openLibrary = rememberLibraryOpener()
    var menuFor by remember { mutableStateOf<HomeCard?>(null) }
    var menuError by remember { mutableStateOf<String?>(null) }
    // The grid's scope, not the menu's: the menu is gone before its action's request finishes.
    val scope = rememberCoroutineScope()
    // A card taken out from its own menu (Remove from Watchlist, Remove from List, Hide) leaves
    // focus nowhere, and the window hands it to the tabs: the card that takes its place (else
    // the one before) gets it. Kept, with its index, until focus moves to another card. An
    // emptied grid is the page's to handle (the Watchlist's filter takes focus).
    var menuCard by remember { mutableStateOf<Pair<String, Int>?>(null) }
    var refocusKey by remember { mutableStateOf<String?>(null) }
    val refocus = remember { FocusRequester() }
    LaunchedEffect(cards) {
        val (key, index) = menuCard ?: return@LaunchedEffect
        if (cards.any { it.id == key } || cards.isEmpty()) return@LaunchedEffect
        menuCard = null
        refocusKey = cards[minOf(index, cards.size - 1)].id
        withFrameNanos { }
        runCatching { refocus.requestFocus() }
    }
    LazyVerticalGrid(
        columns = columns,
        state = rememberLazyGridState(),
        contentPadding = contentPadding,
        horizontalArrangement = Arrangement.spacedBy(18.dp),
        verticalArrangement = Arrangement.spacedBy(20.dp),
        modifier = modifier.focusRestorer(),
    ) {
        header()
        itemsIndexed(cards, key = { _, c -> c.id }) { index, card ->
            if (onNearEnd != null && index >= cards.size - 12) androidx.compose.runtime.LaunchedEffect(cards.size) { onNearEnd() }
            PosterCard(
                card,
                onClick = {
                    when (val link = card.link) {
                        is HomeLink.Details -> navigator.openDetails(link.target)
                        is HomeLink.Person -> navigator.open(Page.Person(link.person))
                        is HomeLink.Library -> openLibrary(link.entry)
                        else -> Unit
                    }
                },
                onLongClick = {
                    if (card.target != null) {
                        menuFor = card
                        menuCard = card.id to index
                    }
                },
                modifier = (if (index == 0 && first != null) Modifier.focusRequester(first) else Modifier)
                    .then(if (card.id == refocusKey) Modifier.focusRequester(refocus) else Modifier)
                    .onFocusChanged { if (it.isFocused && card.id != menuCard?.first) menuCard = null },
            )
        }
        footer()
    }
    menuFor?.let { card -> TitleMenu(card, scope, extraActions(card), continueWatching, onError = { menuError = it }) { menuFor = null } }
    menuError?.let { MessageDialog("Couldn't Update", it) { menuError = null } }
}

/** A grid-wide line (heading, filters, empty state). */
fun LazyGridScope.fullWidth(key: String, content: @Composable () -> Unit) =
    item(key = key, span = { GridItemSpan(maxLineSpan) }) { content() }

/** Clear of the focus scale at the tallest a poster gets: a grid column's, about 152 × 228 dp. */
private val POSTER_CAPTION_GAP = captionGap(228.dp)

/** A 2:3 poster card: Compose for TV's Card, scaling on focus, title and line under it. */
@Composable
fun PosterCard(card: HomeCard, onClick: () -> Unit, onLongClick: () -> Unit, modifier: Modifier = Modifier) {
    Column(verticalArrangement = Arrangement.spacedBy(POSTER_CAPTION_GAP)) {
        Card(
            onClick = onClick,
            onLongClick = onLongClick,
            scale = CardDefaults.scale(focusedScale = CardFocusScale), border = cardFocusBorder(),
            modifier = modifier.fillMaxWidth().aspectRatio(2f / 3f),
        ) {
            Box(Modifier.fillMaxSize().background(Color(0xFF1C1C1E))) {
                AsyncImage(
                    model = Artwork.url(card.posterUrl ?: card.artworkUrl, "w342"), contentDescription = card.title,
                    contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize(),
                )
                card.badge?.let { Badge(it, Modifier.align(Alignment.TopStart).padding(5.dp)) }
                if (card.progress > 0.01f) ProgressLine(card.progress, Modifier.align(Alignment.BottomStart).padding(5.dp))
            }
        }
        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(card.title, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(card.subtitle, fontSize = 11.5.sp, color = Color.White.copy(alpha = 0.6f), maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
    }
}

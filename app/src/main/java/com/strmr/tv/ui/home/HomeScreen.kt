package com.strmr.tv.ui.home

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.AnimationVector1D
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.tween
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.gestures.BringIntoViewSpec
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import kotlinx.coroutines.launch
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.tv.material3.Text
import com.strmr.tv.app.AppSession
import com.strmr.tv.app.AppLog
import com.strmr.tv.home.BrowseList
import com.strmr.tv.home.CAROUSEL_LOG
import com.strmr.tv.home.CarouselPick
import com.strmr.tv.home.ExploreCard
import com.strmr.tv.home.HeroSize
import com.strmr.tv.home.HomeCard
import com.strmr.tv.home.HomeLayoutStore
import com.strmr.tv.home.HomeShelf
import com.strmr.tv.home.HomeSlot
import com.strmr.tv.home.HomeStore
import com.strmr.tv.home.HomeViewStore
import com.strmr.tv.home.HomeViews
import com.strmr.tv.home.ShelfTransition
import com.strmr.tv.library.TitleActions
import com.strmr.tv.library.WatchlistStore
import com.strmr.tv.player.PlaybackCoordinator
import com.strmr.tv.ui.Accent
import com.strmr.tv.ui.ContentMargin
import com.strmr.tv.ui.LocalNavigator
import com.strmr.tv.ui.MessageDialog
import com.strmr.tv.ui.TitleMenu
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.filter

/**
 * Focus moving never scrolls a list by itself: the column scrolls from row to row, and each row
 * scrolls itself to its focused card ([ShelfRow]).
 */
@OptIn(ExperimentalFoundationApi::class)
internal val NoAutoScroll = object : BringIntoViewSpec {
    override fun calculateScrollDistance(offset: Float, size: Float, containerSize: Float) = 0f
}

/**
 * Home, laid out like the tvOS app: a full-bleed backdrop and hero text for the focused card,
 * the profile's rows below, the focused row kept at the same height on screen. Reloads when
 * the server's home manifest changes (on wake and every five minutes) and refreshes the
 * playback rows after playing. Shows the current Home view ([HomeViewStore]): All, Movies,
 * Shows or a custom page, each with its own rows.
 */
@Composable
fun HomeScreen(home: HomeState, tabsFocused: () -> Boolean = { false }) {
    // A store per Home view: going back to a view shows its rows at once, reloading only if stale.
    val view = HomeViewStore.current
    val store = home.store(view)
    val current by rememberUpdatedState(store)
    val sessionsEnded by PlaybackCoordinator.sessionsEnded.collectAsState()
    val wakes by AppSession.wakes.collectAsState()
    LaunchedEffect(sessionsEnded) { if (sessionsEnded > 0) current.reloadPlaybackRows() }
    LaunchedEffect(wakes) { if (wakes > 0) current.refreshIfStale() }
    LaunchedEffect(Unit) {
        while (true) {
            delay(5 * 60_000L)
            current.refreshIfStale()
        }
    }
    // A new view (or layout) starts at the top with fresh focus memory and its own hero; one
    // shown before comes back with the card left focused.
    val topCarousel = HomeLayoutStore.topCarousel
    val focusedInfo = HomeLayoutStore.focusedInfo
    key(view, topCarousel, focusedInfo) { HomePage(store, home.page("$view/$topCarousel/$focusedInfo"), tabsFocused) }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun HomePage(store: HomeStore, memory: HomeFocusMemory, tabsFocused: () -> Boolean) {
    val scope = rememberCoroutineScope()
    val navigator = LocalNavigator.current
    val onCard = rememberCardHandler { store.settings }
    val layout = HomeLayoutStore
    var hero by memory::hero
    val firstCard = remember { FocusRequester() }
    var focusedOnce by memory::focusedOnce
    var menuFor by remember { mutableStateOf<Pair<HomeShelf, HomeCard>?>(null) }
    var menuError by remember { mutableStateOf<String?>(null) }
    /** Bumped after a title action reloads the rows: focus returns to the rows if the focused card left. */
    var refocus by remember { mutableIntStateOf(0) }
    /** The row shown, which sizes the rows area under the full hero. */
    var shownRow by remember { mutableStateOf<String?>(null) }
    /** Focused Title Info with the full hero shows one shelf to a screen; with Swap it fades from row to row. */
    val oneShelf = layout.focusedInfo && layout.effectiveHeroSize == HeroSize.FULL &&
        layout.shelfTransition == ShelfTransition.SWAP
    val rowFade = remember { Animatable(1f) }

    LaunchedEffect(Unit) {
        if (store.settings == null) {
            store.ensureLoaded()
        } else {
            store.publishLayout()
            store.refreshIfStale()
        }
    }
    // Back from a details page or list grid (a title removed from a list there): reload if stale.
    LaunchedEffect(navigator) {
        snapshotFlow { navigator.isCovered }.drop(1).filter { !it }.collect { store.refreshIfStale() }
    }
    val shelves = store.slots.filterIsInstance<HomeSlot.Shelf>()
    // Top Carousel: one row feeds the carousel and isn't shown again below it.
    val carouselId = if (layout.topCarousel) CarouselPick.pick(store.slots, layout.carouselRow) else null
    val carouselShelf = (store.slots.firstOrNull { it.id == carouselId } as? HomeSlot.Shelf)?.shelf
    val slides = remember(carouselShelf) { carouselShelf?.let(CarouselPick::slides).orEmpty() }
    val slideCount = slides.size
    val hasSlides = slideCount > 0
    val carouselFocus = remember { FocusRequester() }
    val carouselState = remember { CarouselState() }
    LaunchedEffect(carouselId) {
        if (carouselId != null) AppLog.i(CAROUSEL_LOG, "row ${carouselId ?: "none"} (chosen ${layout.carouselRow ?: "default"}, view ${store.view})")
    }
    LaunchedEffect(shelves.isNotEmpty(), hasSlides) {
        if (focusedOnce || shelves.isEmpty()) return@LaunchedEffect
        // The carousel takes focus at launch once its row is in; it waits for a row still loading.
        if (carouselId != null && !hasSlides && store.slots.any { it.id == carouselId && it is HomeSlot.Loading }) return@LaunchedEffect
        hero = shelves.first().shelf.shown.firstOrNull()?.hero()
        focusedOnce = true
        // At launch the first card takes focus; arriving from the tab bar, focus stays on the Home tab.
        if (!tabsFocused()) runCatching { if (hasSlides) carouselFocus.requestFocus() else firstCard.requestFocus() }
    }
    val onExplore: (ExploreCard) -> Unit = { navigator.openList(it.list, store.settings?.viewQuery) }
    val onMenu: (HomeShelf, HomeCard) -> Unit = { shelf, card -> menuFor = shelf to card }

    val rows: @Composable (List<HomeSlot>, Modifier, (HeroContent) -> Unit, RowsHeader?) -> Unit = { slots, modifier, onHero, header ->
        if (!HomeStatus(store, modifier)) {
            HomeRows(slots, memory, firstCard, modifier, onHero, onCard = onCard, onExplore = onExplore, onMenu = onMenu, refocus = refocus, header = header,
                     onRow = { shownRow = it }, fade = rowFade.takeIf { oneShelf })
        }
    }

    if (!layout.topCarousel && layout.focusedInfo) {
        ClassicHome(hero, layout.fullHeroRowHeight(store.slots, shownRow), rowFade) { modifier -> rows(store.slots, modifier, { hero = it }, null) }
    } else {
        StackedHome(
            store.slots.filterNot { it.id == carouselId },
            carouselId?.let { CarouselSetup(carouselShelf, slides, carouselFocus, carouselState) },
            hero, shownRow, rowFade, onHero = { hero = it }, rows = rows,
        )
    }

    // A title action (Set Aside, hide, watched): every row reloads, as on tvOS, since any row's
    // cards carry the server's watch badge. Rows keep their cards until the new ones arrive.
    val actions = TitleActions.changes
    LaunchedEffect(actions) {
        if (actions == 0) return@LaunchedEffect
        store.load()
        refocus++
    }
    // A title added to or removed from the watchlist (from any menu or details page): the
    // Watchlist row follows, as on tvOS, and focus moves on if its card left.
    val watchlist = WatchlistStore.items
    var rowWatchlist by remember { mutableStateOf(watchlist) }
    LaunchedEffect(watchlist) {
        if (watchlist == rowWatchlist) return@LaunchedEffect
        rowWatchlist = watchlist
        store.reloadWatchlistRow()
        refocus++
    }
    menuFor?.let { (shelf, card) ->
        TitleMenu(card, scope, continueWatching = shelf.list == BrowseList.ContinueWatching, onError = { menuError = it }) { menuFor = null }
    }
    menuError?.let { MessageDialog("Couldn't Update", it) { menuError = null } }
}

/**
 * Top Carousel off, Focused Title Info on: the Classic Home, the hero following the focused card.
 */
@Composable
private fun ClassicHome(hero: HeroContent?, rowHeight: Dp, fade: Animatable<Float, AnimationVector1D>, rows: @Composable (Modifier) -> Unit) {
    val layout = HomeLayoutStore
    val size = layout.effectiveHeroSize
    val full = size == HeroSize.FULL
    Box(Modifier.fillMaxSize()) {
        HeroBackdrop(hero)
        // As on tvOS: with the full hero, the rows area is exactly the shown row's height above
        // the bottom safe area, so that shelf sits on the bottom edge and nothing peeks in under
        // it; it changes size while the page is faded out between rows. A smaller hero sits under
        // the tab bar and the rows run to the screen's bottom edge, so more of them show.
        Column(
            Modifier.fillMaxSize().padding(top = if (full) 0.dp else TAB_BAR_CLEARANCE, bottom = if (full) SAFE_BOTTOM else 0.dp)
                .graphicsLayer { alpha = fade.value },
            verticalArrangement = Arrangement.spacedBy(if (size == HeroSize.OFF) 0.dp else 18.dp, if (full) Alignment.Bottom else Alignment.Top),
        ) {
            HeroInfo(hero, size, layout.heroTextSize.scale, Modifier.padding(horizontal = ContentMargin))
            // Slide resizes the area with the scroll; Swap while the page is faded out.
            val areaHeight by animateDpAsState(rowHeight, rowResize(layout.shelfTransition), label = "rowsArea")
            rows(if (full) Modifier.height(areaHeight) else Modifier.weight(1f))
        }
    }
}

/** Loading, a load error or no rows, in place of the rows; false when there are rows to show. */
@Composable
private fun HomeStatus(store: HomeStore, modifier: Modifier): Boolean {
    when {
        store.loadError != null -> Text(store.loadError!!, color = Color(0xFFFF6B6B), modifier = modifier.padding(horizontal = ContentMargin))
        store.isLoading && store.slots.isEmpty() ->
            Box(modifier.padding(horizontal = ContentMargin)) { CircularProgressIndicator(color = Accent) }
        store.slots.isEmpty() -> Text(
            if (store.view == HomeViews.ALL) "Nothing on Home yet. Turn on rows in the web UI."
            else "Nothing on this page yet. Add rows to it in the web UI.",
            color = Color.White.copy(alpha = 0.6f), modifier = modifier.padding(horizontal = ContentMargin),
        )
        else -> return false
    }
    return true
}

/** Room above a smaller hero for the tab bar (tvOS: the 60 pt safe area plus 90). */
internal val TAB_BAR_CLEARANCE = 75.dp

/** The bottom safe area the full hero's row sits on (tvOS 60 pt). */
internal val SAFE_BOTTOM = 30.dp

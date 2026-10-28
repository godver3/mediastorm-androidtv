package com.strmr.tv.ui.home

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.AnimationVector1D
import androidx.compose.animation.core.tween
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.focusGroup
import androidx.compose.foundation.gestures.LocalBringIntoViewSpec
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.layout.layout
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.strmr.tv.home.ExploreCard
import com.strmr.tv.home.HomeCard
import com.strmr.tv.home.HomeLayoutStore
import com.strmr.tv.home.HomeShelf
import com.strmr.tv.home.HomeSlot
import kotlinx.coroutines.launch

/**
 * A header above Home's rows (the carousel's controls): the first item of the
 * column, scrolled to while focus is in it, and away like a row as focus moves into the rows.
 * [onDown] moves focus to the first row.
 */
class RowsHeader(
    val focus: FocusRequester,
    /**
     * The header is on screen: focus is in it or above it, not in the rows. Held by the page,
     * which crossfades the carousel to the rows on it.
     */
    val active: MutableState<Boolean>,
    /** Room below the last row, so every row can scroll up to the top of the rows area. */
    val tail: Dp = 0.dp,
    /**
     * Room the header's item runs past the rows' viewport (a focused button's scale): drawn,
     * not clipped, while the header is up and scrolled fully in. Never a row peeking below.
     */
    val overflow: Dp = 0.dp,
    val content: @Composable (onDown: () -> Unit) -> Unit,
)

/**
 * Home's rows. The focused row is scrolled to the top of [modifier]'s area (clipped there), so
 * it always sits in the same place and the row above is off screen.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
internal fun HomeRows(
    slots: List<HomeSlot>,
    firstCard: FocusRequester,
    modifier: Modifier,
    onHero: (HeroContent) -> Unit,
    onCard: (HomeCard) -> Unit,
    onExplore: (ExploreCard) -> Unit,
    onMenu: (HomeShelf, HomeCard) -> Unit,
    refocus: Int,
    header: RowsHeader? = null,
    /**
     * The row shown, by ID, once the move to it is done (null: the header): the full hero's rows
     * area takes its height.
     */
    onRow: (String?) -> Unit = {},
    /**
     * The full hero's page fade, set when it shows one shelf to a screen: moving from row to row
     * (or between the header and the first row), the page fades out, the next place takes over
     * unanimated and it fades back in, rather than the rows sliding up or down. Null: rows scroll.
     */
    fade: Animatable<Float, AnimationVector1D>? = null,
) {
    val layout = HomeLayoutStore
    val state = rememberLazyListState()
    val lead = if (header != null) 1 else 0
    // By row ID, not position: a row appearing above (Set Aside after a Set Aside action) or
    // leaving shifts positions, and the focused row must stay where it is on screen.
    var focusedId by remember { mutableStateOf<String?>(null) }
    /** Where the focused row was, for when it has left (its position is then the next row's). */
    var focusedIndex by remember { mutableIntStateOf(0) }
    /** Focus is in the header (or above it); it starts there (Home focuses the carousel at launch). */
    val noHeader = remember { mutableStateOf(false) }
    var headerFocused by (header?.active ?: noHeader)
    val memories = remember { mutableMapOf<String, ShelfMemory>() }
    /** The row whose loading placeholder holds focus: it hands focus on when the row arrives. */
    var placeholderFocused by remember { mutableStateOf<String?>(null) }
    val focusedRow = slots.indexOfFirst { it.id == focusedId }.takeIf { it >= 0 } ?: focusedIndex.coerceIn(0, maxOf(slots.lastIndex, 0))
    /** The row shown (null: the header): until a fade has swapped, the place being left. Its card's info is the hero. */
    var shown by remember { mutableStateOf<String?>(null) }
    /** Each row's last focused card's info, kept back while its row fades in. */
    val heroes = remember { mutableMapOf<String, HeroContent>() }
    val wasHeader = remember { booleanArrayOf(headerFocused) }
    LaunchedEffect(focusedRow, headerFocused) {
        val fromHeader = wasHeader[0]
        wasHeader[0] = headerFocused
        val id = if (headerFocused) null else slots.getOrNull(focusedRow)?.id
        val item = if (headerFocused) 0 else focusedRow + lead
        fun show() { shown = id; onRow(id); id?.let(heroes::get)?.let(onHero) }
        val moved = fromHeader != headerFocused || (id != null && shown != null && shown != id)
        if (fade != null && moved) {
            fade.animateTo(0f, tween(ROW_FADE_OUT_MS))
            state.scrollToItem(item)
            show()
            fade.animateTo(1f, tween(ROW_FADE_IN_MS))
        } else {
            fade?.snapTo(1f)
            show()
            state.animateScrollToItem(item)
        }
    }
    // After a title action the rows are rebuilt (a row appearing above the focused one, as Set
    // Aside does, is otherwise skipped by focus search) and focus goes back to the focused row,
    // or the row now in its place when it left.
    val requesters = remember { mutableMapOf<String, FocusRequester>() }
    val scope = rememberCoroutineScope()
    fun focusRow(target: Int) = scope.launch {
        state.scrollToItem(target + lead)
        withFrameNanos { }
        runCatching { requesters[slots[target].id]?.requestFocus() }
    }
    /**
     * Focus to [requester] once its item is composed: the column animates to it meanwhile (as
     * from row to row), so the header and the first row scroll as one shelf does.
     */
    fun focusWhenComposed(requester: FocusRequester) = scope.launch {
        repeat(90) {
            if (runCatching { requester.requestFocus() }.isSuccess) return@launch
            withFrameNanos { }
        }
    }
    /**
     * Up / down past failed rows (which hold nothing focusable) to the next row that takes focus:
     * one with cards, or one still loading, whose placeholder holds focus until it arrives (as on
     * tvOS; skipping it, Down at launch went past Continue Watching while it loaded). Focus search
     * can't: that row is off screen and not composed. False when the adjacent row takes focus
     * (plain focus search) or no such row lies that way.
     */
    fun skipPlaceholders(from: Int, step: Int): Boolean {
        if (slots.getOrNull(from + step).let { it == null || it !is HomeSlot.Failed }) return false
        val target = generateSequence(from + step) { it + step }.takeWhile { it in slots.indices }
            .firstOrNull { slots[it] !is HomeSlot.Failed } ?: return false
        focusRow(target)
        return true
    }
    /** Up from the first row that takes focus: back to the header, which scrolls back in. */
    fun toHeader(from: Int): Boolean {
        if (header == null || slots.take(from).any { it !is HomeSlot.Failed }) return false
        headerFocused = true
        focusWhenComposed(header.focus)
        return true
    }
    /** The first row that takes focus: Home's first focus at launch, and Down from the header. */
    val firstRow = slots.indexOfFirst { it !is HomeSlot.Failed }
    val onHeaderDown: () -> Unit = {
        if (firstRow >= 0) {
            val id = slots[firstRow].id
            focusedId = id
            focusedIndex = firstRow
            headerFocused = false
            focusWhenComposed(requesters.getOrPut(id) { FocusRequester() })
        }
    }
    // The row a placeholder held focus for has arrived: focus into its cards. Failed (or gone),
    // focus moves on to the next row that takes it, else the nearest above.
    LaunchedEffect(slots) {
        val id = placeholderFocused ?: return@LaunchedEffect
        val index = slots.indexOfFirst { it.id == id }
        when (slots.getOrNull(index)) {
            is HomeSlot.Loading -> return@LaunchedEffect
            is HomeSlot.Shelf -> { placeholderFocused = null; focusWhenComposed(requesters.getOrPut(id) { FocusRequester() }) }
            else -> {
                placeholderFocused = null
                val from = index.coerceAtLeast(0)
                val target = (from..slots.lastIndex).firstOrNull { slots[it] !is HomeSlot.Failed }
                    ?: (from - 1 downTo 0).firstOrNull { slots[it] !is HomeSlot.Failed } ?: return@LaunchedEffect
                focusRow(target)
            }
        }
    }
    LaunchedEffect(refocus) {
        if (refocus == 0) return@LaunchedEffect
        if (header != null && headerFocused) {
            // The column was rebuilt under the header (a watchlist toggle there): focus goes back to it.
            state.scrollToItem(0)
            withFrameNanos { }
            runCatching { header.focus.requestFocus() }
            return@LaunchedEffect
        }
        if (slots.isEmpty()) return@LaunchedEffect
        val index = focusedRow.coerceAtMost(slots.lastIndex)
        state.scrollToItem(index + lead)
        withFrameNanos { }
        runCatching { requesters[slots[index].id]?.requestFocus() }
    }
    CompositionLocalProvider(LocalBringIntoViewSpec provides NoAutoScroll) { Box(modifier) {
        key(refocus) { LazyColumn(Modifier.fillMaxSize().headerOverflow(if (headerFocused) header?.overflow ?: 0.dp else 0.dp, state), state = state) {
            if (header != null) {
                item(key = "__header__") {
                    Box(Modifier.onFocusChanged { if (it.hasFocus) headerFocused = true }) { header.content(onHeaderDown) }
                }
            }
            itemsIndexed(slots, key = { _, slot -> slot.id }) { index, slot ->
                val metrics = layout.metrics(slot.id)
                val requester = requesters.getOrPut(slot.id) { FocusRequester() }
                Box(
                    Modifier.focusRequester(requester)
                        .onFocusChanged {
                            if (it.hasFocus) {
                                focusedId = slot.id; focusedIndex = index; headerFocused = false
                                placeholderFocused = if (slot is HomeSlot.Loading) slot.id else null
                            }
                        }
                        .onPreviewKeyEvent { e ->
                            e.type == KeyEventType.KeyDown && when (e.key) {
                                Key.DirectionDown -> skipPlaceholders(index, 1)
                                Key.DirectionUp -> toHeader(index) || skipPlaceholders(index, -1)
                                else -> false
                            }
                        }
                        .focusGroup(),
                ) {
                    when (slot) {
                        is HomeSlot.Shelf -> ShelfRow(
                            slot.shelf, metrics, layout.effectiveFocusPosition, metrics.captions, layout.animateExpansion,
                            memory = memories.getOrPut(slot.id) { ShelfMemory() },
                            entry = if (index == firstRow) firstCard else null,
                            onFocus = { card, explore ->
                                (card?.hero() ?: explore?.hero())?.let { info ->
                                    heroes[slot.id] = info
                                    if (fade == null || shown == slot.id) onHero(info)
                                }
                            },
                            onOpen = onCard,
                            onExplore = onExplore,
                            onMenu = { card -> onMenu(slot.shelf, card) },
                        )
                        is HomeSlot.Loading -> PlaceholderRow(slot.title, failed = false, metrics = metrics,
                                                              entry = if (index == firstRow) firstCard else null)
                        is HomeSlot.Failed -> PlaceholderRow(slot.title, failed = true, metrics = metrics)
                    }
                }
            }
            if (header != null && header.tail > 0.dp) item(key = "__tail__") { Spacer(Modifier.height(header.tail)) }
        } }
    } }
}

/**
 * The column's viewport, [room] taller than its slot (so the header item's overflow is laid out
 * and drawn), clipped back to the slot except when scrolled fully to the header: going up from
 * a row, the row below never shows in the extra room. Plain clip-to-bounds with no room.
 */
private fun Modifier.headerOverflow(room: Dp, state: LazyListState): Modifier = layout { measurable, constraints ->
    val extra = if (constraints.hasBoundedHeight) room.roundToPx() else 0
    val tall = if (extra > 0) constraints.copy(minHeight = constraints.minHeight + extra, maxHeight = constraints.maxHeight + extra) else constraints
    val placeable = measurable.measure(tall)
    layout(placeable.width, placeable.height - extra) { placeable.place(0, 0) }
}.drawWithContent {
    val extra = room.toPx()
    val atHeader = state.firstVisibleItemIndex == 0 && state.firstVisibleItemScrollOffset == 0
    clipRect(bottom = size.height - if (atHeader) 0f else extra) { this@drawWithContent.drawContent() }
}

/** The full hero's fade from row to row: out, then in once the next row is in place. */
private const val ROW_FADE_OUT_MS = 120
private const val ROW_FADE_IN_MS = 200

internal fun HomeCard.hero() = HeroContent(title, subtitle, overview, backdropUrl ?: artworkUrl, logoUrl, drawable)

private fun ExploreCard.hero() = HeroContent(title, "$moreCount more", "Open the whole list.", backdropUrl, null)

package com.strmr.tv.ui.home

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.gestures.LocalBringIntoViewSpec
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.util.lerp
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.focusRestorer
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.tv.material3.Text
import com.strmr.tv.home.ExploreCard
import com.strmr.tv.home.FocusedCardPosition
import com.strmr.tv.home.HomeCard
import com.strmr.tv.home.HomeShelf
import com.strmr.tv.home.ShelfStyle
import com.strmr.tv.ui.ContentMargin
import com.strmr.tv.ui.uniqueKeys
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch

/** The page margin (tvOS 80 pt). */
private val ROW_INSET = ContentMargin
private val CARD_SPACING = 24.dp

/** A focus move along a row: the cards opening and folding, and the row's scroll. */
private val FocusMotion = spring<Float>(stiffness = Spring.StiffnessMediumLow)

/**
 * A row's focus, animated: [at] is the focused card's index, gliding between whole numbers as
 * focus moves, and [inRow] eases to 1 while focus is in the row. Every card's width and the
 * row's scroll are worked out from these two each frame, so the card folding, the next one
 * opening and the scroll move as one. The row used to leave the scroll to the focus
 * system's bring-into-view, which measured the card before it opened, then chased it back
 * as the widths changed under it.
 */
internal class RowFocus(private val list: LazyListState) {
    val at = Animatable(0f)
    val inRow = Animatable(0f)

    var count = 0
    var expands = false
    /** Whether an expanding row's cards open and fold in motion (Animate Expansion); off, they snap. */
    var animates = true
    var position = FocusedCardPosition.LEFT
    /** In px: a card's width, the extra an open poster takes, the gap, the page inset, the row's width. */
    var card = 0f
    var extra = 0f
    var spacing = 0f
    var inset = 0f
    var container = 0f

    /** How far the card at [index] has opened to 16:9, 0 to 1. */
    fun open(index: Int): Float = if (!expands) 0f else inRow.value * max(0f, 1f - abs(at.value - index))

    suspend fun focus(index: Int) = coroutineScope {
        if (expands && !animates) {
            inRow.snapTo(1f)
            at.snapTo(index.toFloat())
            scroll()
            return@coroutineScope
        }
        // Coming into the row: the remembered card opens in place, no glide from the last one.
        if (inRow.value == 0f && !inRow.isRunning) at.snapTo(index.toFloat())
        launch { inRow.animateTo(1f, FocusMotion) { scroll() } }
        at.animateTo(index.toFloat(), FocusMotion) { scroll() }
    }

    suspend fun leave() {
        if (expands && !animates) {
            inRow.snapTo(0f)
            scroll()
        } else {
            inRow.animateTo(0f, FocusMotion) { scroll() }
        }
    }

    /** Scrolls to where the focused card sits now, between the two cards [at] is between. */
    fun scroll() {
        if (count == 0 || container == 0f) return
        val from = at.value.toInt().coerceIn(0, count - 1)
        val to = min(from + 1, count - 1)
        val target = lerp(offset(from), offset(to), at.value - from)
        // To the card at the target and the offset into it, with the widths as drawn now.
        var left = 0f
        for (index in 0 until count) {
            val step = card + (extra * open(index)).roundToInt() + spacing
            if (target < left + step || index == count - 1) {
                list.requestScrollToItem(index, (target - left).roundToInt())
                return
            }
            left += step
        }
    }

    /**
     * The scroll, from the first card's left edge, that puts the card at [index] focused where
     * the Focused Card Position setting says, between the insets; clamped, so the row stays
     * put at its start and stops at its end (tvOS `RowMetrics.offset`).
     */
    private fun offset(index: Int): Float {
        val width = card + extra * inRow.value
        val leading = index * (card + spacing)
        val inner = container - 2 * inset
        val x = when (position) {
            FocusedCardPosition.LEFT -> leading
            FocusedCardPosition.CENTER -> leading + width / 2 - inner / 2
            FocusedCardPosition.RIGHT -> leading + width - inner
        }
        val content = count * card + (count - 1) * spacing + (width - card)
        return x.coerceIn(0f, max(0f, content - inner))
    }
}

/**
 * What a row keeps while Home scrolls it out of composition and back: its scroll, its focus
 * motion and the card it was left on, held by the page per row.
 */
class ShelfMemory {
    val list = LazyListState()
    internal val focus = RowFocus(list)
    /** The key of the card focus last left the row on; null until the row has been visited. */
    var lastKey: Any? = null
    /** Where [lastKey] sat in the row, for the card that takes its place when it leaves. */
    var lastIndex = 0
    /** Whether focus is in the row now; read while a refresh composes, before it takes a card away. */
    var hasFocus = false
}

/**
 * One Home row: its title over the cards, with the explore card first or last. Moving into
 * the row lands on the card it was left on, or on its first card if it hasn't been visited.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun ShelfRow(
    shelf: HomeShelf,
    metrics: CardMetrics,
    position: FocusedCardPosition,
    showTitles: Boolean,
    /** Whether an expanding poster opens and folds in motion; off, it snaps. */
    animateExpansion: Boolean,
    memory: ShelfMemory,
    entry: FocusRequester?,
    onFocus: (HomeCard?, ExploreCard?) -> Unit,
    onOpen: (HomeCard) -> Unit,
    onExplore: (ExploreCard) -> Unit,
    /** Long press on a title's card: the title menu. */
    onMenu: (HomeCard) -> Unit,
) {
    val explore = shelf.explore
    val cards = shelf.shown
    val exploreFirst = explore != null && shelf.exploreFirst
    val first = if (exploreFirst) 1 else 0
    val count = cards.size + (if (explore != null) 1 else 0)

    val keys = remember(cards, explore, exploreFirst) {
        buildList {
            if (exploreFirst) add(ExploreCard.ID)
            cards.forEach { add(it.id) }
            if (explore != null && !exploreFirst) add(ExploreCard.ID)
        }
    }
    val list = memory.list
    val focus = memory.focus
    // The card entering the row lands on, by focusRestorer's fallback: also when the row was
    // out of composition, which forgets the restorer's own saved card.
    /** The card taking a removed focused card's place, which focus moves to. */
    var replacement by remember { mutableStateOf<Any?>(null) }
    val landing = replacement?.takeIf { it in keys } ?: memory.lastKey?.takeIf { it in keys } ?: keys.firstOrNull()
    val target = remember { FocusRequester() }
    val scope = rememberCoroutineScope()
    val density = LocalDensity.current
    var container by remember { mutableFloatStateOf(0f) }
    SideEffect {
        with(density) {
            focus.count = count
            focus.expands = metrics.style == ShelfStyle.EXPANDING_POSTER
            focus.animates = animateExpansion
            focus.position = position
            focus.card = metrics.width.roundToPx().toFloat()
            focus.extra = (metrics.focusedWidth.roundToPx() - metrics.width.roundToPx()).toFloat()
            focus.spacing = CARD_SPACING.roundToPx().toFloat()
            focus.inset = ROW_INSET.roundToPx().toFloat()
            focus.container = container
        }
    }
    LaunchedEffect(count, metrics, position, container) { focus.scroll() }
    // While a refresh composes the old cards are still attached, so this is whether focus was
    // in the row before it.
    val focusedBefore = remember(cards) { memory.hasFocus }
    // A refresh can move the remembered card (a title played from Continue Watching comes back
    // at its front): follow it, so focus and the hero never point at a card scrolled off screen.
    LaunchedEffect(keys) {
        val index = keys.indexOf(landing).coerceAtLeast(0).toFloat()
        if (focus.at.targetValue != index) {
            focus.at.snapTo(index)
            focus.scroll()
        }
    }
    // The focused card's data can change under it too (Continue Watching's next episode), and
    // it can leave (a finished movie, a title removed from its list): the hero follows, and
    // focus moves to the card that took its place (the one before, for the row's last card).
    LaunchedEffect(cards) {
        if (!focusedBefore) return@LaunchedEffect
        if (memory.lastKey != null && memory.lastKey !in keys) {
            val index = memory.lastIndex.coerceAtMost(keys.lastIndex)
            val next = keys.getOrNull(index)
            memory.lastKey = next
            replacement = next
            // Scrolled to first, so the card is composed to take focus.
            focus.at.snapTo(index.coerceAtLeast(0).toFloat())
            focus.scroll()
            withFrameNanos {}
            runCatching { target.requestFocus() }
            replacement = null
            cards.firstOrNull { it.id == next }?.let { onFocus(it, null) }
        } else {
            cards.firstOrNull { it.id == memory.lastKey }?.let { onFocus(it, null) }
        }
    }
    // Composed again after scrolling off Home: focus isn't here, whatever leave() was doing then.
    LaunchedEffect(Unit) { if (!focus.inRow.isRunning) focus.inRow.snapTo(0f) }
    fun focused(index: Int) {
        memory.lastKey = keys.getOrNull(index)
        memory.lastIndex = index
        scope.launch { focus.focus(index) }
    }
    fun landingModifier(key: Any) = if (key == landing) Modifier.focusRequester(target) else Modifier

    Column(Modifier.height(metrics.rowHeight)) {
        Text(shelf.title, fontSize = 19.sp, fontWeight = FontWeight.Medium, modifier = Modifier.height(23.dp).padding(start = ROW_INSET))
        val keys = remember(cards) { uniqueKeys(cards) { it.id } }
        CompositionLocalProvider(LocalBringIntoViewSpec provides NoAutoScroll) {
            LazyRow(
                state = list,
                contentPadding = PaddingValues(start = ROW_INSET, end = ROW_INSET, top = metrics.liftRoom),
                horizontalArrangement = Arrangement.spacedBy(CARD_SPACING),
                modifier = Modifier
                    .focusRestorer(target)
                    .onSizeChanged { container = it.width.toFloat() }
                    .onFocusChanged {
                        memory.hasFocus = it.hasFocus
                        if (!it.hasFocus) scope.launch { focus.leave() }
                    },
            ) {
                if (explore != null && exploreFirst) {
                    item(key = ExploreCard.ID) {
                        ExploreCardView(
                            explore, metrics, showTitles, { focus.open(0) },
                            { focused(0); onFocus(null, explore) }, { onExplore(explore) },
                            entryModifier(entry, true).then(landingModifier(ExploreCard.ID)),
                        )
                    }
                }
                itemsIndexed(cards, key = { i, _ -> keys[i] }) { i, card ->
                    val index = first + i
                    HomeCardView(
                        card, metrics, showTitles, { focus.open(index) },
                        { focused(index); onFocus(card, null) }, { onOpen(card) },
                        entryModifier(entry, index == 0).then(landingModifier(card.id)),
                        onLongClick = if (card.target != null) { { onMenu(card) } } else null,
                    )
                }
                if (explore != null && !exploreFirst) {
                    item(key = ExploreCard.ID) {
                        ExploreCardView(
                            explore, metrics, showTitles, { focus.open(count - 1) },
                            { focused(count - 1); onFocus(null, explore) }, { onExplore(explore) },
                            landingModifier(ExploreCard.ID),
                        )
                    }
                }
            }
        }
    }
}

private fun entryModifier(entry: FocusRequester?, first: Boolean) =
    if (entry != null && first) Modifier.focusRequester(entry) else Modifier

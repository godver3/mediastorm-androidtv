package com.strmr.tv.ui.home

import androidx.compose.animation.core.AnimationVector1D
import androidx.compose.animation.core.snap
import androidx.compose.runtime.derivedStateOf
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.strmr.tv.app.MenuStyle
import com.strmr.tv.home.HeroSize
import com.strmr.tv.home.HomeCard
import com.strmr.tv.home.HomeLayoutStore
import com.strmr.tv.home.HomeShelf
import com.strmr.tv.home.HomeSlot
import com.strmr.tv.ui.ContentMargin

/** The Top Carousel's row, its slides and its page-held focus and slide. */
class CarouselSetup(val shelf: HomeShelf?, val slides: List<HomeCard>, val focus: FocusRequester, val state: CarouselState)

/**
 * Home without the Classic layout: the rows stacked under the Top Carousel
 * ([carousel], when on) or alone, the focused row scrolled to the top of the rows area.
 *
 * With the carousel the page is Classic's "hero + first shelf": the slide's art and its logo,
 * line and synopsis are fixed layers, and its buttons and dots are the rows' first item, at the
 * bottom safe area. Down scrolls that item away as a shelf scrolls and the first row lands
 * where Classic puts it (Focused Title Info on: on the bottom safe area under the Full hero,
 * else under a smaller one; off: under the menu), while the art crossfades to the focused card's
 * backdrop (or the page background) and the slide info to the card's (or out). Up reverses it.
 * The crossfades are one alpha read in the draw phase: nothing recomposes per frame.
 */
@Composable
internal fun StackedHome(
    slots: List<HomeSlot>,
    carousel: CarouselSetup?,
    hero: HeroContent?,
    shownRow: String?,
    /** The full hero's fade from row to row (`HomeRows`). */
    fade: Animatable<Float, AnimationVector1D>,
    onHero: (HeroContent) -> Unit,
    rows: @Composable (List<HomeSlot>, Modifier, (HeroContent) -> Unit, RowsHeader?) -> Unit,
) {
    val layout = HomeLayoutStore
    // Under the side menu without the view switcher nothing sits above the rows.
    val menuTop = if (MenuStyle.side && !layout.viewSwitcher) SIDE_CLEARANCE else TAB_BAR_CLEARANCE
    if (carousel == null) {
        rows(slots, Modifier.fillMaxSize().padding(top = menuTop), onHero, null)
        return
    }
    val info = layout.focusedInfo
    /** The carousel is on screen (focus in it or above it); false once focus is in the rows. */
    val active = remember { mutableStateOf(true) }
    /** 0 the carousel, 1 the rows. */
    val toRows = remember { Animatable(0f) }
    CrossfadeDriver(active, toRows)
    if (carousel.slides.isNotEmpty()) CarouselAutoAdvance(carousel.state, carousel.slides.size, active)
    BoxWithConstraints(Modifier.fillMaxSize()) {
        val size = layout.effectiveHeroSize
        val textScale = layout.heroTextSize.scale
        val full = size == HeroSize.FULL
        val gap = if (size == HeroSize.OFF) 0.dp else 18.dp
        // Where Classic puts the focused row: on the bottom safe area under the full hero, else under a smaller one.
        // The shown row's height (as Classic's), the tallest row's while the carousel is up. It
        // resizes with the carousel's scroll, and at once while the page is faded between rows.
        val tallest = layout.tallestRowHeight(slots)
        val faded by remember { derivedStateOf { fade.value < 1f } }
        val rowHeight by animateDpAsState(
            if (active.value) tallest else layout.fullHeroRowHeight(slots, shownRow),
            if (faded) snap() else tween(ROW_RESIZE_MS), label = "rowsArea",
        )
        val infoRowTop = if (full) maxHeight - SAFE_BOTTOM - rowHeight else TAB_BAR_CLEARANCE + heroHeight(size, textScale) + gap
        // The rows area, clipped as Classic's: one row tall over the safe area under the full hero, else to the bottom.
        val areaTop = if (info) infoRowTop else menuTop
        val areaBottom = if (info && full) SAFE_BOTTOM else 0.dp
        val areaHeight = maxHeight - areaTop - areaBottom
        if (info) InfoBackdrop(hero, carousel, active, toRows)
        CarouselArt(carousel.slides, carousel.state, Modifier.fillMaxSize().graphicsLayer { alpha = 1f - toRows.value })
        CarouselInfo(
            carousel.slides, carousel.state, carousel.shelf?.title,
            Modifier.align(Alignment.BottomStart).graphicsLayer { alpha = 1f - toRows.value },
        )
        if (info) InfoText(hero, size, textScale, infoRowTop - gap - heroHeight(size, textScale), toRows, fade)
        // The controls' item runs FOCUS_ROOM past the rows area under the buttons, so a focused
        // button isn't cut off at its foot (with the full hero that is the bottom safe area).
        val header = RowsHeader(carousel.focus, active, tail = if (info) areaHeight else 0.dp, overflow = FOCUS_ROOM) { onDown ->
            val bottom = if (areaBottom == 0.dp) SAFE_BOTTOM else 0.dp
            CarouselControls(carousel.slides, carousel.state, carousel.focus, areaHeight + FOCUS_ROOM, bottom + FOCUS_ROOM, onDown)
        }
        rows(slots, Modifier.fillMaxSize().padding(top = areaTop, bottom = areaBottom).graphicsLayer { alpha = fade.value }, onHero, header)
    }
}

/** Runs the crossfade as focus crosses between the carousel and the rows, in its own scope. */
@Composable
private fun CrossfadeDriver(active: MutableState<Boolean>, toRows: Animatable<Float, *>) {
    val target = if (active.value) 0f else 1f
    LaunchedEffect(target) { toRows.animateTo(target, tween(CROSSFADE_MS)) }
}

/** The carousel / rows crossfade (Classic's backdrop crossfade takes about as long). */
private const val CROSSFADE_MS = 400

/**
 * The info's backdrop, under the carousel's art: kept on the current slide's art while the
 * carousel shows, so going down Coil crossfades that art to the focused card's while the
 * carousel's own layer fades out over it. Not drawn while the carousel's art is fully over it.
 */
@Composable
private fun InfoBackdrop(hero: HeroContent?, carousel: CarouselSetup, active: MutableState<Boolean>, toRows: Animatable<Float, *>) {
    val shown = if (active.value) carousel.slides.getOrNull(carousel.state.index)?.hero() else hero
    Box(Modifier.fillMaxSize().graphicsLayer { alpha = if (toRows.value > 0f) 1f else 0f }) { HeroBackdrop(shown) }
}

/** The Focused Title Info text at [top], crossfading with the slide's as focus crosses into the rows and back. */
@Composable
private fun InfoText(hero: HeroContent?, size: HeroSize, textScale: Float, top: Dp, toRows: Animatable<Float, *>, fade: Animatable<Float, *>) {
    HeroInfo(
        hero, size, textScale,
        Modifier.padding(start = ContentMargin, end = ContentMargin, top = top).graphicsLayer { alpha = toRows.value * fade.value },
    )
}

/** Above the rows under the side menu (no tab bar). */
private val SIDE_CLEARANCE = 28.dp

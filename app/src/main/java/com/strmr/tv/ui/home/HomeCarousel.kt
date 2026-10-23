package com.strmr.tv.ui.home

import android.os.SystemClock
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.strmr.tv.app.AppLog
import com.strmr.tv.app.MenuStyle
import com.strmr.tv.home.CAROUSEL_LOG
import com.strmr.tv.home.HomeCard
import com.strmr.tv.ui.ContentMargin
import kotlinx.coroutines.delay

/** Remote presses in the main window (MainActivity counts them): each one restarts the carousel's 8 s. */
object RemotePresses {
    var count by mutableIntStateOf(0)
}

/** The carousel's slide, held by the page so it survives the controls scrolling out of composition. */
class CarouselState {
    var index by mutableIntStateOf(0)
    /** The button focus was last on: 0 Play, 1 Watchlist, 2 Info. */
    var button by mutableIntStateOf(0)
    /** When auto-advance last changed the slide (uptime ms). */
    var advancedAt by mutableLongStateOf(0L)

    /** Select within 1 s of an auto-advance is dropped: it was meant for the slide before. */
    fun selectBlocked(): Boolean = SystemClock.uptimeMillis() - advancedAt < SELECT_GUARD_MS
}

/** Time on a slide before the next (Apple TV app–like). */
private const val ADVANCE_MS = 8_000L
private const val SELECT_GUARD_MS = 1_000L

/**
 * Advances [state] every 8 s, focused or not; any remote press starts the 8 s again. Paused
 * while a page or the player covers Home, or the app is in the background (window focus: pages
 * are dialogs). Its own scope, so a slide change or a press recomposes only this.
 */
@Composable
fun CarouselAutoAdvance(state: CarouselState, count: Int) {
    val windowFocused = LocalWindowInfo.current.isWindowFocused
    val index = state.index
    val presses = RemotePresses.count
    val running = count > 1 && windowFocused
    LaunchedEffect(index, count, running, presses) {
        if (!running) return@LaunchedEffect
        delay(ADVANCE_MS)
        state.advancedAt = SystemClock.uptimeMillis()
        state.index = (index + 1) % count
        AppLog.i(CAROUSEL_LOG, "auto-advance to ${state.index + 1}/$count")
    }
}

/** The current slide's art, the next one fetched ahead. Its own scope: a slide change redraws only this. */
@Composable
fun CarouselArt(slides: List<HomeCard>, state: CarouselState, modifier: Modifier) {
    val count = slides.size
    val slide = slides.getOrNull(state.index)
    CarouselBackdrop(
        slide?.backdropUrl ?: slide?.artworkUrl,
        slides.getOrNull((state.index + 1) % maxOf(count, 1))?.let { it.backdropUrl ?: it.artworkUrl },
        modifier,
    )
}

/** The buttons row's height: the bottom of the slide info sits this far above the controls' bottom inset. */
internal val CONTROLS_HEIGHT = 40.dp

/**
 * How far a focused carousel button (its focus scale) reaches below its resting frame. The
 * carousel's item runs this far past the rows' viewport under the buttons, and the rows draw
 * that far down while the carousel is up ([RowsHeader.overflow]), so it isn't cut off.
 */
internal val FOCUS_ROOM = 20.dp

/**
 * The carousel's first "shelf": Play / Watchlist / Info at the bottom left and
 * the page dots at the bottom right, [bottom] above its foot (which includes [FOCUS_ROOM]), in an item [height] tall that
 * scrolls away like a row. Left on Play and Right on Info change slides (wrapping); in the side
 * menu style Left on the first slide is left to open the menu. Down goes to the rows.
 */
@Composable
fun CarouselControls(slides: List<HomeCard>, state: CarouselState, focus: FocusRequester, height: Dp, bottom: Dp, onDown: () -> Unit) {
    val count = slides.size
    if (count > 0 && state.index >= count) state.index = 0
    val slide = slides.getOrNull(state.index)
    fun step(by: Int): Boolean {
        if (count < 2) return false
        // From the state, not this composition's index: two presses can land in one frame.
        val at = state.index
        if (by < 0 && at == 0 && MenuStyle.side) return false
        state.index = (at + by + count) % count
        AppLog.i(CAROUSEL_LOG, "slide ${state.index + 1}/$count (${if (by > 0) "right" else "left"})")
        return true
    }
    Box(
        Modifier.fillMaxWidth().height(height).onPreviewKeyEvent { e ->
            if (e.type == KeyEventType.KeyDown && e.key == Key.DirectionDown) { onDown(); true } else false
        },
    ) {
        Box(Modifier.align(Alignment.BottomStart).fillMaxWidth().height(CONTROLS_HEIGHT + bottom).padding(start = ContentMargin, end = ContentMargin, bottom = bottom)) {
            if (slide != null) Box(Modifier.align(Alignment.CenterStart)) { CarouselButtons(slide, state, focus, onEdge = ::step) }
            CarouselDots(count, state.index, Modifier.align(Alignment.CenterEnd))
        }
    }
}

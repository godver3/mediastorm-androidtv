package com.strmr.tv.ui.home

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.outlined.Info
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.unit.dp
import androidx.tv.material3.Button
import androidx.tv.material3.ButtonDefaults
import androidx.tv.material3.Icon
import androidx.tv.material3.IconButton
import androidx.tv.material3.Text
import com.strmr.tv.api.DetailsTarget
import com.strmr.tv.app.AppLog
import com.strmr.tv.home.CAROUSEL_LOG
import com.strmr.tv.home.HomeCard
import com.strmr.tv.library.WatchlistStore
import com.strmr.tv.player.PlaybackCoordinator
import com.strmr.tv.ui.LocalNavigator
import com.strmr.tv.ui.Page
import kotlinx.coroutines.launch

/**
 * Play / Resume (opens details and starts its primary action, as a home screen Play link does),
 * Watchlist (toggles in place, + or ✓) and Info (details). [onEdge] gets Left on Play (-1)
 * and Right on Info (+1), which change slides; it returns whether it took the key. Select within
 * 1 s of an auto-advance is dropped ([CarouselState.selectBlocked]), so it can't act on the
 * title that just replaced the one the press was meant for.
 */
@Composable
fun CarouselButtons(slide: HomeCard, state: CarouselState, focus: FocusRequester, onEdge: (Int) -> Boolean) {
    val target = slide.target ?: return
    val navigator = LocalNavigator.current
    var focused by remember { mutableIntStateOf(-1) }
    /**
     * [focus] sits on the button last focused (held by [state]), so focusing the carousel again
     * (Up from the rows, or after a title action rebuilds Home's rows) lands where it was left.
     */
    fun button(i: Int) = Modifier
        .then(if (state.button == i) Modifier.focusRequester(focus) else Modifier)
        .onFocusChanged { if (it.isFocused) { focused = i; state.button = i } }
    Row(
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        modifier = Modifier.onPreviewKeyEvent { e ->
            e.type == KeyEventType.KeyDown && when (e.key) {
                Key.DirectionLeft -> focused == 0 && onEdge(-1)
                Key.DirectionRight -> focused == 2 && onEdge(1)
                else -> false
            }
        },
    ) {
        Button(
            onClick = {
                if (state.selectBlocked()) return@Button
                AppLog.i(CAROUSEL_LOG, "play ${target.titleId}")
                PlaybackCoordinator.prewarm(target)
                navigator.open(Page.Details(target, autoplay = true))
            },
            contentPadding = ButtonDefaults.ButtonWithIconContentPadding,
            modifier = button(0),
        ) {
            Icon(Icons.Filled.PlayArrow, contentDescription = null, modifier = Modifier.size(ButtonDefaults.IconSize))
            Spacer(Modifier.width(ButtonDefaults.IconSpacing))
            Text(if (slide.progress > 0f) "Resume" else "Play")
        }
        WatchlistToggle(target, state, button(1))
        IconButton(
            onClick = { if (!state.selectBlocked()) navigator.openDetails(target) },
            modifier = button(2),
        ) {
            Icon(Icons.Outlined.Info, contentDescription = "Details")
        }
    }
}

/** One button whose icon flips, so focus stays on it; presses while a change is in flight are ignored. */
@Composable
private fun WatchlistToggle(target: DetailsTarget, state: CarouselState, modifier: Modifier) {
    val scope = rememberCoroutineScope()
    var busy by remember { mutableStateOf(false) }
    val inList = WatchlistStore.contains(target)
    IconButton(
        onClick = {
            if (busy || state.selectBlocked()) return@IconButton
            busy = true
            scope.launch {
                runCatching { WatchlistStore.toggle(target) }
                    .onFailure { AppLog.w(CAROUSEL_LOG, "watchlist toggle failed: ${it.message}") }
                busy = false
            }
        },
        modifier = modifier,
    ) {
        Icon(if (inList) Icons.Filled.Check else Icons.Filled.Add, contentDescription = if (inList) "In Watchlist" else "Add to Watchlist")
    }
}

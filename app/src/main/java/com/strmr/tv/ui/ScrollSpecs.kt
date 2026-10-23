package com.strmr.tv.ui

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.gestures.BringIntoViewSpec
import androidx.compose.foundation.gestures.LocalBringIntoViewSpec
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.drawscope.clipRect
import kotlin.math.abs

/**
 * Scrolls only as far as needed to show the focused item whole. On TV, Compose's default
 * instead keeps focus about 30% down the screen, which floats a page's bottom-anchored header
 * up as soon as its buttons take focus.
 */
@OptIn(ExperimentalFoundationApi::class)
val MinimalScroll = object : BringIntoViewSpec {
    override fun calculateScrollDistance(offset: Float, size: Float, containerSize: Float): Float {
        val trailing = offset + size
        return when {
            offset >= 0 && trailing <= containerSize -> 0f
            offset < 0 && trailing > containerSize -> 0f
            abs(offset) < abs(trailing - containerSize) -> offset
            else -> trailing - containerSize
        }
    }
}

/**
 * For a page whose first screen is a bottom-anchored header (details): focus anywhere on that
 * screen scrolls back to the top, so the whole header shows again; below the fold, focus sits
 * 30% down the screen (the TV default), keeping a row's heading and captions in view.
 * `offset` is relative to the viewport, so `scroll.value + offset` is the item's page position.
 */
@OptIn(ExperimentalFoundationApi::class)
fun foldScroll(scroll: ScrollState) = object : BringIntoViewSpec {
    override fun calculateScrollDistance(offset: Float, size: Float, containerSize: Float): Float {
        if (scroll.value + offset + size <= containerSize) return -scroll.value.toFloat()
        val target = if (size <= containerSize * 0.7f) containerSize * 0.3f else maxOf(0f, containerSize - size)
        return offset - target
    }
}

/** `content` scrolls with [foldScroll]; [WithDefaultScroll] puts the TV default back inside it. */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun WithFoldScroll(scroll: ScrollState, content: @Composable (default: BringIntoViewSpec) -> Unit) {
    val default = LocalBringIntoViewSpec.current
    val spec = remember(scroll) { foldScroll(scroll) }
    CompositionLocalProvider(LocalBringIntoViewSpec provides spec) { content(default) }
}

/** `content` scrolls with [MinimalScroll]; [WithDefaultScroll] puts the TV default back inside it. */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun WithMinimalScroll(content: @Composable (default: BringIntoViewSpec) -> Unit) {
    val default = LocalBringIntoViewSpec.current
    CompositionLocalProvider(LocalBringIntoViewSpec provides MinimalScroll) { content(default) }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun WithDefaultScroll(spec: BringIntoViewSpec, content: @Composable () -> Unit) {
    CompositionLocalProvider(LocalBringIntoViewSpec provides spec, content = content)
}

/**
 * Clips scrolled content at the top and bottom only (rows never scroll under the tab bar),
 * leaving the sides open so a focused row's scale and corners aren't cut off.
 */
fun Modifier.clipVertically() = drawWithContent {
    clipRect(left = -size.width, right = size.width * 2) { this@drawWithContent.drawContent() }
}

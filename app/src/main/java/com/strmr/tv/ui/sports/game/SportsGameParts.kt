package com.strmr.tv.ui.sports.game

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.focusGroup
import androidx.compose.foundation.gestures.LocalBringIntoViewSpec
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.layout
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.tv.material3.LocalTextStyle
import androidx.tv.material3.Text
import coil.compose.SubcomposeAsyncImage
import com.strmr.tv.sports.SportsTeam
import com.strmr.tv.ui.MinimalScroll
import com.strmr.tv.ui.sports.SportsMuted

/** The page's secondary text, a step fainter than [SportsMuted] (tvOS `.tertiary`). */
internal val SportsFaint = Color.White.copy(alpha = 0.42f)

/** The current text style with tabular digits (tvOS `.monospacedDigit()`), so scores and times line up. */
internal val SportsDigits: TextStyle
    @Composable get() = LocalTextStyle.current.merge(TextStyle(fontFeatureSettings = "tnum"))

/**
 * A row of chips (tabs, sides, filters) across the page (tvOS `sportsChipRow`): one focus group
 * as wide as the page, so Up and Down reach it from anywhere above or below, and focus coming
 * in lands on [chosen], the selected chip (none: the nearest). [scrolls] lets a long row scroll
 * sideways.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun SportsChipRow(
    chosen: FocusRequester?,
    modifier: Modifier = Modifier,
    centered: Boolean = false,
    scrolls: Boolean = false,
    content: @Composable RowScope.() -> Unit,
) {
    val enter = if (chosen != null) Modifier.focusProperties { onEnter = { runCatching { chosen.requestFocus() } } } else Modifier
    // A sideways row reaches past the page's margins by the bleed and pads it back in, so its
    // clip leaves room for an end chip's focus scale; it scrolls only as far as a chip needs.
    val scroll = if (scrolls) Modifier.bleed(ChipBleed).horizontalScroll(rememberScrollState()).padding(horizontal = ChipBleed) else Modifier
    CompositionLocalProvider(LocalBringIntoViewSpec provides MinimalScroll) {
        Row(
            modifier.fillMaxWidth().then(enter).focusGroup().then(scroll),
            horizontalArrangement = Arrangement.spacedBy(10.dp, if (centered) Alignment.CenterHorizontally else Alignment.Start),
            verticalAlignment = Alignment.CenterVertically,
            content = content,
        )
    }
}

private val ChipBleed = 10.dp

/** Lays the content out [amount] wider on each side than the space it is given. */
private fun Modifier.bleed(amount: Dp) = layout { measurable, constraints ->
    val extra = amount.roundToPx() * 2
    val placeable = measurable.measure(constraints.copy(minWidth = constraints.minWidth + extra, maxWidth = constraints.maxWidth + extra))
    layout(placeable.width - extra, placeable.height) { placeable.place(-extra / 2, 0) }
}

/**
 * Columns sharing the width by weight (Expo's `flex` columns, tvOS `SportsColumns`), tops
 * aligned. Leave out a column with nothing to show and the others take its width. With
 * [fullHeight] every column's focus group spans the row (tvOS `SportsSplit` offering the row
 * height): Left or Right from a panel low in the taller column then lands in the other column
 * instead of the side menu, which a short column's bounds would leave closer.
 */
@Composable
fun SportsColumns(modifier: Modifier = Modifier, fullHeight: Boolean = false, content: @Composable RowScope.() -> Unit) {
    val heights = if (fullHeight) remember { SportsColumnHeights() } else null
    CompositionLocalProvider(LocalColumnHeights provides heights) {
        Row(modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(18.dp), content = content)
    }
}

/** One of [SportsColumns]: a focus group, so Left and Right move between the columns. */
@Composable
fun RowScope.SportsColumn(weight: Float = 1f, content: @Composable ColumnScope.() -> Unit) {
    val heights = LocalColumnHeights.current
    if (heights == null) {
        Column(Modifier.weight(weight).focusGroup(), verticalArrangement = Arrangement.spacedBy(12.dp), content = content)
        return
    }
    // The group is as tall as the tallest column; the column inside keeps its own height, which is what it reports.
    val id = remember { Any() }
    DisposableEffect(id) { onDispose { heights.forget(id) } }
    val density = LocalDensity.current
    Box(Modifier.weight(weight).heightIn(min = with(density) { heights.tallest.toDp() }).focusGroup()) {
        Column(
            Modifier.fillMaxWidth().onSizeChanged { heights.report(id, it.height) },
            verticalArrangement = Arrangement.spacedBy(12.dp), content = content,
        )
    }
}

/** Each full-height column's own height, and the tallest of them. */
private class SportsColumnHeights {
    private val heights = mutableStateMapOf<Any, Int>()
    val tallest: Int get() = heights.values.maxOrNull() ?: 0

    fun report(id: Any, height: Int) {
        if (heights[id] != height) heights[id] = height
    }

    fun forget(id: Any) {
        heights.remove(id)
    }
}

private val LocalColumnHeights = staticCompositionLocalOf<SportsColumnHeights?> { null }

/**
 * A player's headshot in a circle, the team colour with initials while it loads or when there
 * is none (Expo `SportsPlayerPortrait`).
 */
@Composable
fun SportsPlayerPortrait(name: String, headshot: String?, team: SportsTeam?, size: Dp) {
    val url = headshot?.takeIf { it.startsWith("https://") }
    val fallback = @Composable {
        Box(
            Modifier.size(size).clip(CircleShape)
                .background(SportsTeamColors.parse(team?.color)?.copy(alpha = 0.7f) ?: Color.White.copy(alpha = 0.15f)),
            contentAlignment = Alignment.Center,
        ) {
            val initials = name.split(" ").filter { it.isNotEmpty() }.take(2).joinToString("") { it.take(1) }
            Text(initials, fontSize = (size.value * 0.34f).sp, fontWeight = FontWeight.SemiBold)
        }
    }
    if (url == null) fallback()
    else SubcomposeAsyncImage(
        model = url, contentDescription = null, contentScale = ContentScale.Crop,
        loading = { fallback() }, error = { fallback() },
        modifier = Modifier.size(size).clip(CircleShape).background(Color.White.copy(alpha = 0.08f)),
    )
}

/** A small heading over a group of panels ("Matchup Preview"). */
@Composable
fun SportsSectionHeading(title: String, note: String? = null) {
    Column(Modifier.fillMaxWidth()) {
        Text(title, fontSize = 20.sp, fontWeight = FontWeight.SemiBold)
        if (!note.isNullOrEmpty()) Text(note, fontSize = 12.sp, color = SportsMuted)
    }
}

/** Table columns measured so headings and figures never truncate (tvOS `SportsTable.width`). */
class SportsTableMeasure(private val measurer: TextMeasurer, private val density: Density, private val base: TextStyle) {
    val valueStyle = base.merge(TextStyle(fontSize = 13.sp, fontFeatureSettings = "tnum"))
    val labelStyle = base.merge(TextStyle(fontSize = 11.sp))

    /** The width a column needs for its heading and values. */
    fun width(label: String, values: List<String>, minimum: Dp = 28.dp): Dp {
        val widest = maxOf(values.maxOfOrNull { measure(it, valueStyle) } ?: 0, measure(label, labelStyle))
        return maxOf(minimum, with(density) { widest.toDp() } + 10.dp)
    }

    /** The widest of [texts] in [style] (over the page's text style). */
    fun widest(texts: List<String>, style: TextStyle): Dp =
        with(density) { (texts.maxOfOrNull { measure(it, base.merge(style)) } ?: 0).toDp() }

    private fun measure(text: String, style: TextStyle) = measurer.measure(text, style, maxLines = 1, softWrap = false).size.width
}

@Composable
fun rememberSportsTableMeasure(): SportsTableMeasure {
    val measurer = rememberTextMeasurer()
    val density = LocalDensity.current
    val base = LocalTextStyle.current
    return remember(measurer, density, base) { SportsTableMeasure(measurer, density, base) }
}

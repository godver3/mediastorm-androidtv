package com.strmr.mobile.ui.sports.game

import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.HorizontalAlignmentLine
import androidx.compose.ui.layout.Measured
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.SubcomposeAsyncImage
import com.strmr.mobile.ui.sports.LocalSportsCompact
import com.strmr.mobile.ui.sports.SportsMuted
import com.strmr.tv.sports.SportsTeam
import com.strmr.tv.ui.sports.game.SportsTeamColors

/** The current text style with tabular digits (tvOS `.monospacedDigit()`), so scores and times line up. */
val SportsDigits: TextStyle
    @Composable get() = LocalTextStyle.current.merge(TextStyle(fontFeatureSettings = "tnum"))

/**
 * A row of chips (tabs, sides, filters) across the page. [chosen] is the TV's focus target,
 * unused on a phone; [scrolls] (and every row on a phone, where chips rarely fit) scrolls
 * sideways.
 */
@Suppress("UNUSED_PARAMETER")
@Composable
fun SportsChipRow(
    chosen: FocusRequester?,
    modifier: Modifier = Modifier,
    centered: Boolean = false,
    scrolls: Boolean = false,
    content: @Composable RowScope.() -> Unit,
) {
    Row(
        modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(8.dp, if (centered && !LocalSportsCompact.current) Alignment.CenterHorizontally else Alignment.Start),
        verticalAlignment = Alignment.CenterVertically,
        content = content,
    )
}

/**
 * Columns sharing the width by weight (Expo's `flex` columns), tops aligned; on a phone
 * ([LocalSportsCompact]) the columns stack, each the page's width. [fullHeight] is the TV's
 * focus-group sizing and has no effect here.
 */
@Suppress("UNUSED_PARAMETER")
@Composable
fun SportsColumns(modifier: Modifier = Modifier, fullHeight: Boolean = false, content: @Composable RowScope.() -> Unit) {
    if (LocalSportsCompact.current) {
        Column(modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(12.dp)) { StackedRowScope.content() }
    } else {
        Row(modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(16.dp), content = content)
    }
}

/**
 * The [RowScope] stacked columns run in: weights and row alignments have no meaning in a
 * column, so they leave the modifier as it is.
 */
private object StackedRowScope : RowScope {
    override fun Modifier.weight(weight: Float, fill: Boolean): Modifier = fillMaxWidth()
    override fun Modifier.align(alignment: Alignment.Vertical): Modifier = this
    override fun Modifier.alignBy(alignmentLine: HorizontalAlignmentLine): Modifier = this
    override fun Modifier.alignByBaseline(): Modifier = this
    override fun Modifier.alignBy(alignmentLineBlock: (Measured) -> Int): Modifier = this
}

/** One of [SportsColumns]. */
@Composable
fun RowScope.SportsColumn(weight: Float = 1f, content: @Composable ColumnScope.() -> Unit) {
    Column(Modifier.weight(weight), verticalArrangement = Arrangement.spacedBy(12.dp), content = content)
}

/** A player's headshot in a circle, the team colour with initials while it loads or when there is none. */
@Composable
fun SportsPlayerPortrait(name: String, headshot: String?, team: SportsTeam?, size: Dp) {
    val url = headshot?.takeIf { it.startsWith("https://") }
    val placeholder = MaterialTheme.colorScheme.surfaceContainerHighest
    val fallback = @Composable {
        Box(
            Modifier.size(size).clip(CircleShape).background(SportsTeamColors.parse(team?.color)?.copy(alpha = 0.7f) ?: placeholder),
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
        modifier = Modifier.size(size).clip(CircleShape).background(placeholder),
    )
}

/** A small heading over a group of panels ("Matchup Preview"). */
@Composable
fun SportsSectionHeading(title: String, note: String? = null) {
    Column(Modifier.fillMaxWidth()) {
        Text(title, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
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

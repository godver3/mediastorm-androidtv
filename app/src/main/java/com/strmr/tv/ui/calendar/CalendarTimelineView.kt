package com.strmr.tv.ui.calendar

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.tv.material3.Text
import com.strmr.tv.calendar.CalendarDay
import com.strmr.tv.calendar.CalendarTimeline
import com.strmr.tv.ui.Accent

private val MonthSpacing = 200.dp
private const val MARKER_POSITION = 0.28f
private val TrackLine = Color(0xFF24435C)
private val MonthLabel = Color(0xFF7187A1)
private val MonthDot = Color(0xFF173F62)
private val MarkerRing = Color(0xFFDBE9FF)
private val MarkerLabel = Color(0xFFF4F8FC)

/**
 * The date timeline over the feed (Expo `CalendarDateTimeline`): month nodes on a line that
 * slides (360 ms, ease-out) so the focused day sits under the marker, 28 % from the left.
 * Not focusable; it follows the feed.
 */
@Composable
fun CalendarTimelineView(days: List<CalendarDay>, activeDateKey: String?, modifier: Modifier = Modifier) {
    val months = remember(days) { CalendarTimeline.months(days) }
    val activeDay = days.firstOrNull { it.key == activeDateKey }
    BoxWithConstraints(modifier.fillMaxWidth().height(54.dp).clipToBounds()) {
        if (activeDay == null || activeDateKey == null) return@BoxWithConstraints
        val density = LocalDensity.current
        val spacingPx = with(density) { MonthSpacing.toPx() }
        val target = constraints.maxWidth * MARKER_POSITION - CalendarTimeline.position(months, activeDateKey) * spacingPx
        val shift = remember { Animatable(target) }
        LaunchedEffect(target) { shift.animateTo(target, tween(360, easing = CubicBezierEasing(0.33f, 1f, 0.68f, 1f))) }
        // Unbounded: the track runs past the viewport.
        Box(Modifier.wrapContentSize(Alignment.TopStart, unbounded = true).graphicsLayer { translationX = shift.value }) {
            Box(
                Modifier.offset(y = 27.dp).width(MonthSpacing * maxOf(1, months.size - 1) + 1.dp).height(1.dp).background(TrackLine),
            )
            months.forEachIndexed { index, month ->
                Column(
                    Modifier.offset(x = MonthSpacing * index - 28.dp, y = 4.dp).width(56.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    Text(month.label, color = MonthLabel, fontSize = 11.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.1.sp, maxLines = 1)
                    Box(Modifier.size(5.dp).background(MonthDot, CircleShape))
                }
            }
        }
        Column(
            Modifier.offset(x = maxWidth * MARKER_POSITION - 80.dp, y = 21.dp).width(160.dp)
                .semantics { contentDescription = CalendarTimeline.description(activeDay) },
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            Box(Modifier.size(12.dp).background(Accent, CircleShape).border(1.5.dp, MarkerRing, CircleShape))
            Text(
                CalendarTimeline.activeLabel(activeDateKey), color = MarkerLabel, fontSize = 13.sp, fontWeight = FontWeight.ExtraBold,
                letterSpacing = 0.5.sp, maxLines = 1, textAlign = TextAlign.Center,
            )
        }
    }
}

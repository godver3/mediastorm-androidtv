package com.strmr.tv.ui.sports.game.charts

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.strmr.tv.ui.sports.SportsMuted
import java.util.Locale

/** A chart's plot area and scales: elapsed seconds across, [low]…[high] up. */
class SportsChartArea(val plot: Rect, private val duration: Double, private val low: Double, private val high: Double) {
    fun x(elapsed: Double): Float = plot.left + (elapsed / duration).toFloat() * plot.width
    fun y(value: Double): Float = plot.bottom - ((value - low) / (high - low)).toFloat() * plot.height
    fun at(elapsed: Double, value: Double) = Offset(x(elapsed), y(value))
}

/**
 * A game chart drawn with Compose `Canvas` (tvOS Swift Charts): horizontal grid lines with their
 * labels down the left, the periods as dashed lines with "Q1"/"H2"/"OT1" along the bottom, then
 * the chart's own marks ([draw]). Hidden from accessibility: the panel's readout speaks for it.
 */
@Composable
fun SportsChart(
    height: Dp,
    duration: Double,
    low: Double,
    high: Double,
    ticks: List<Pair<Double, String>>,
    periods: List<SportsChartPeriod>,
    draw: DrawScope.(SportsChartArea, TextMeasurer) -> Unit,
) {
    val measurer = rememberTextMeasurer()
    Canvas(Modifier.fillMaxWidth().height(height).clearAndSetSemantics { }) {
        val plot = Rect(40.dp.toPx(), 6.dp.toPx(), size.width - 8.dp.toPx(), size.height - 20.dp.toPx())
        val area = SportsChartArea(plot, duration, low, high)
        for ((value, label) in ticks) {
            val y = area.y(value)
            drawLine(Grid, Offset(plot.left, y), Offset(plot.right, y), 1.dp.toPx())
            val text = measurer.measure(label, AxisLabel)
            drawText(text, topLeft = Offset(plot.left - 6.dp.toPx() - text.size.width, y - text.size.height / 2f))
        }
        val dash = PathEffect.dashPathEffect(floatArrayOf(4.dp.toPx(), 5.dp.toPx()))
        for (period in periods) {
            val x = area.x(period.start)
            drawLine(PeriodLine, Offset(x, plot.top), Offset(x, plot.bottom), 1.dp.toPx(), pathEffect = dash)
            drawText(measurer.measure(period.label, AxisLabel), topLeft = Offset(x + 3.dp.toPx(), plot.bottom + 3.dp.toPx()))
        }
        draw(area, measurer)
    }
}

private val Grid = Color.White.copy(alpha = 0.15f)
private val PeriodLine = Color.White.copy(alpha = 0.2f)
internal val AxisLabel = TextStyle(color = SportsMuted, fontSize = 11.sp)

/** A chart line, 3 dp with round ends. */
internal fun DrawScope.chartLine(path: Path, color: Color) =
    drawPath(path, color, style = Stroke(3.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round))

/** A straight line through [points]. */
internal fun linearPath(points: List<Offset>): Path = Path().apply {
    points.forEachIndexed { index, point -> if (index == 0) moveTo(point.x, point.y) else lineTo(point.x, point.y) }
}

/**
 * A monotone cubic through [points] (Fritsch–Carlson, Swift Charts' `.monotone`): it never
 * overshoots between two samples, so a probability stays within its range.
 */
internal fun monotonePath(points: List<Offset>): Path {
    if (points.size < 3) return linearPath(points)
    val n = points.size
    val slopes = FloatArray(n - 1) { i ->
        val dx = points[i + 1].x - points[i].x
        if (dx == 0f) 0f else (points[i + 1].y - points[i].y) / dx
    }
    val tangents = FloatArray(n) { i ->
        when (i) {
            0 -> slopes[0]
            n - 1 -> slopes[n - 2]
            else -> if (slopes[i - 1] * slopes[i] <= 0f) 0f else 2f / (1f / slopes[i - 1] + 1f / slopes[i])
        }
    }
    return Path().apply {
        moveTo(points[0].x, points[0].y)
        for (i in 0 until n - 1) {
            val a = points[i]
            val b = points[i + 1]
            val third = (b.x - a.x) / 3f
            cubicTo(a.x + third, a.y + tangents[i] * third, b.x - third, b.y - tangents[i + 1] * third, b.x, b.y)
        }
    }
}

/** "62.8%" (or "50%" with no decimals). */
fun sportsPercent(value: Double, digits: Int = 1): String = String.format(Locale.getDefault(), "%.${digits}f%%", value * 100)

package com.strmr.tv.ui.sports.game.football

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

// The marks drawn over a football drawing (Expo's ribbons, `Arrow` and dots, tvOS
// `GraphicsContext.lane/arrow/dot`), in whatever units the scope draws in: the field's units, or
// points on a drive's position bar.

/** A play's lane (Expo's ribbon): a band from [x1] to [x2] with a pointed head, or a dot when the play didn't move. */
fun DrawScope.lane(x1: Float, x2: Float, y: Float, halfHeight: Float, fill: Color, stroke: Float, alpha: Float = 1f) {
    if (x1 == x2) {
        dot(x1, y, max(3f, halfHeight * 0.5f), fill, stroke, alpha)
        return
    }
    val sign = if (x2 < x1) -1 else 1
    val tip = min(9f, abs(x2 - x1) / 2)
    val path = Path().apply {
        moveTo(x1, y - halfHeight)
        lineTo(x2 - sign * tip, y - halfHeight)
        lineTo(x2, y)
        lineTo(x2 - sign * tip, y + halfHeight)
        lineTo(x1, y + halfHeight)
        close()
    }
    drawPath(path, fill, alpha = alpha)
    drawPath(path, Color.White, alpha = alpha, style = Stroke(stroke))
}

/**
 * A drive's arrow (Expo `Arrow`): a line from [x1] to [x2] with an open head, a dot where it
 * started and, for the drive in progress or a score, a bigger dot where it ended.
 */
fun DrawScope.arrow(x1: Float, x2: Float, y: Float, color: Color, current: Boolean, score: Boolean, width: Float = 3f, size: Float = 1f) {
    val ink = if (current) Color.White else color
    drawLine(ink, Offset(x1, y), Offset(x2, y), strokeWidth = if (current) width + 2 else width)
    if (x1 != x2) {
        val back = (if (x2 >= x1) 1 else -1) * min(8 * size, abs(x2 - x1))
        val head = Path().apply {
            moveTo(x2 - back, y - 5 * size)
            lineTo(x2, y)
            lineTo(x2 - back, y + 5 * size)
        }
        drawPath(head, ink, style = Stroke(2 * size))
    }
    dot(x1, y, 3 * size, color, size)
    if (current || score) dot(x2, y, (if (current) 7 else 6) * size, color, 2 * size)
}

/** A dot with a white rim. */
fun DrawScope.dot(x: Float, y: Float, radius: Float, fill: Color, stroke: Float, alpha: Float = 1f) {
    drawCircle(fill, radius, Offset(x, y), alpha = alpha)
    drawCircle(Color.White, radius, Offset(x, y), alpha = alpha, style = Stroke(stroke))
}

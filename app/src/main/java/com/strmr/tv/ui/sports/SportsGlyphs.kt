package com.strmr.tv.ui.sports

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Star
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.tv.material3.Icon
import com.strmr.tv.ui.sports.game.SportsPossessionMark

/**
 * A sport's small mark, drawn (the app has no sports icon set; tvOS uses SF Symbols): football,
 * basketball, hockey puck, soccer ball, or a star for the rest.
 */
@Composable
fun SportsGlyphMark(glyph: SportsGlyph, size: Dp = 14.dp, color: Color = SportsMuted) {
    when (glyph) {
        SportsGlyph.FOOTBALL -> SportsPossessionMark()
        SportsGlyph.BASKETBALL -> Canvas(Modifier.size(size)) { basketball(SportsBall, Color.Black.copy(alpha = 0.6f)) }
        SportsGlyph.HOCKEY -> Canvas(Modifier.size(size)) { puck(color) }
        SportsGlyph.SOCCER -> Canvas(Modifier.size(size)) { soccerBall(color) }
        SportsGlyph.TROPHY -> Icon(Icons.Filled.Star, contentDescription = null, tint = color, modifier = Modifier.size(size))
    }
}

/** The basketball's orange (tvOS #ffc25a on the shot marker). */
internal val SportsBall = Color(0xFFFFC25A)

/** A basketball filling the draw area: the ball and its seams. */
internal fun DrawScope.basketball(ball: Color, seam: Color) {
    val r = size.minDimension / 2
    val c = center
    drawCircle(ball, r, c)
    val width = r * 0.14f
    drawLine(seam, Offset(c.x - r, c.y), Offset(c.x + r, c.y), width)
    drawLine(seam, Offset(c.x, c.y - r), Offset(c.x, c.y + r), width)
    val arc = Size(r * 1.3f, r * 2f)
    drawArc(seam, -60f, 120f, false, Offset(c.x - r * 1.55f, c.y - r), arc, style = Stroke(width))
    drawArc(seam, 120f, 120f, false, Offset(c.x + r * 0.25f, c.y - r), arc, style = Stroke(width))
}

/** A hockey puck seen from the side. */
private fun DrawScope.puck(color: Color) {
    val w = size.width
    val h = size.height
    drawRect(color, Offset(0f, h * 0.38f), Size(w, h * 0.24f))
    drawOval(color, Offset(0f, h * 0.2f), Size(w, h * 0.36f))
    drawOval(color, Offset(0f, h * 0.44f), Size(w, h * 0.36f))
}

/** A ball with a centre patch. */
private fun DrawScope.soccerBall(color: Color) {
    val r = size.minDimension / 2
    drawCircle(color, r - 0.75.dp.toPx(), center, style = Stroke(1.5.dp.toPx()))
    drawCircle(color, r * 0.36f, center)
    val rect = Rect(center, r)
    for (step in 0 until 5) {
        val angle = Math.toRadians(-90.0 + step * 72)
        val from = Offset(center.x + (r * 0.36f * kotlin.math.cos(angle)).toFloat(), center.y + (r * 0.36f * kotlin.math.sin(angle)).toFloat())
        val to = Offset(rect.center.x + (r * kotlin.math.cos(angle)).toFloat(), rect.center.y + (r * kotlin.math.sin(angle)).toFloat())
        drawLine(color, from, to, 1.dp.toPx())
    }
}

/** The sports accent (#8ebcf7, the Expo Midnight sports theme's): the score-change cue, hockey goal badges. */
internal val SportsAccent = Color(0xFF8EBCF7)

package com.strmr.tv.ui.sports.game.football

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import com.strmr.tv.sports.SportsTeam
import com.strmr.tv.ui.sports.SportsTeamLogo
import com.strmr.tv.ui.sports.game.SportsTeamColors

/**
 * A football field drawn to scale with Compose `Canvas` (Expo `FootballPossessionField`'s turf,
 * after `FootballFieldMarkings`, tvOS `SportsFootballField`): five-yard stripes, yard lines, hash
 * marks and numbers between the goal lines, and each end zone in its side's colour with the side's
 * name and logo. The [left] side attacks right. [marks] draws on top in [SportsFieldSpace] units.
 */
@Composable
fun SportsFootballField(
    left: SportsTeam,
    right: SportsTeam,
    leftColor: String,
    rightColor: String,
    modifier: Modifier = Modifier,
    marks: SportsFieldPen.() -> Unit = {},
) {
    val measurer = rememberTextMeasurer()
    BoxWithConstraints(modifier.aspectRatio(SportsFieldSpace.WIDTH / SportsFieldSpace.HEIGHT)) {
        val unit = maxWidth / SportsFieldSpace.WIDTH
        Canvas(Modifier.fillMaxSize()) {
            val scale = size.width / SportsFieldSpace.WIDTH
            withTransform({ scale(scale, scale, Offset.Zero) }) {
                val pen = SportsFieldPen(this, measurer)
                pen.turf()
                pen.endZone(left, leftColor, 18f, -90f)
                pen.endZone(right, rightColor, 768f, 90f)
                pen.lines()
                pen.marks()
            }
        }
        listOf(Triple(left, Offset(48f, 60f), -90f), Triple(right, Offset(798f, 302f), 90f)).forEach { (team, center, rotation) ->
            if (team.logo != null) {
                Box(Modifier.offset(unit * (center.x - 21), unit * (center.y - 21)).size(unit * 42).rotate(rotation)) {
                    SportsTeamLogo(team, unit * 42)
                }
            }
        }
    }
}

/** Draws on a football drawing in [SportsFieldSpace] units: the turf, and the marks over it. */
class SportsFieldPen(val scope: DrawScope, private val measurer: TextMeasurer) {
    enum class Anchor { LEADING, CENTER, TRAILING }

    /** Text [size] units high, its anchor point at ([x], [y]), vertically centred there. */
    fun text(text: String, x: Float, y: Float, size: Float, color: Color, weight: FontWeight = FontWeight.Bold, anchor: Anchor = Anchor.CENTER) {
        with(scope) {
            val layout: TextLayoutResult = measurer.measure(text, TextStyle(color = color, fontSize = size.toSp(), fontWeight = weight))
            val width = layout.size.width
            val left = when (anchor) {
                Anchor.LEADING -> x
                Anchor.CENTER -> x - width / 2f
                Anchor.TRAILING -> x - width
            }
            drawText(layout, topLeft = Offset(left, y - layout.size.height / 2f))
        }
    }

    internal fun turf() {
        for (index in 0 until 10) {
            val x = SportsFieldSpace.x(index * 10.0).toFloat()
            scope.drawRect(
                if (index % 2 == 0) TurfDark else TurfLight, Offset(x, SportsFieldSpace.TOP),
                Size(69f, SportsFieldSpace.BOTTOM - SportsFieldSpace.TOP),
            )
        }
    }

    internal fun endZone(team: SportsTeam, hex: String, x: Float, rotation: Float) {
        scope.drawRect(
            SportsTeamColors.parse(hex) ?: Color.Gray, Offset(x, SportsFieldSpace.TOP),
            Size(60f, SportsFieldSpace.BOTTOM - SportsFieldSpace.TOP),
        )
        val name = (team.nickname?.ifEmpty { null } ?: team.name).uppercase()
        val ink = if (SportsTeamColors.contrast(hex, "#ffffff") >= 4.5) Color.White else Color.Black
        scope.rotate(rotation, Offset(x + 30, 181f)) { text(name, x + 30, 181f, if (name.length > 20) 11f else 16f, ink) }
    }

    internal fun lines() {
        for (index in 0..20) {
            val x = SportsFieldSpace.x(index * 5.0).toFloat()
            scope.drawLine(
                Chalk.copy(alpha = if (index % 2 == 1) 0.22f else 0.45f), Offset(x, SportsFieldSpace.TOP), Offset(x, SportsFieldSpace.BOTTOM),
                strokeWidth = if (index == 0 || index == 20) 2f else 1f,
            )
        }
        for (yard in 1..99) {
            if (yard % 5 == 0) continue
            val x = SportsFieldSpace.x(yard.toDouble()).toFloat()
            for (top in listOf(29f, 178f, 323f)) scope.drawLine(Hash.copy(alpha = 0.6f), Offset(x, top), Offset(x, top + 10), strokeWidth = 1f)
        }
        for (y in listOf(52f, 310f)) {
            for (index in 1..9) text("${minOf(index, 10 - index) * 10}", SportsFieldSpace.x(index * 10.0).toFloat(), y, 17f, Numbers)
        }
    }

    /** A full-height line across the turf at a spot (the ball, the line to gain). */
    fun yardLine(yard: Double, color: Color, width: Float) {
        val x = SportsFieldSpace.x(yard).toFloat()
        scope.drawLine(color, Offset(x, SportsFieldSpace.TOP), Offset(x, SportsFieldSpace.BOTTOM), strokeWidth = width)
    }

    companion object {
        val TurfDark = Color(0.03f, 0.33f, 0.2f)
        val TurfLight = Color(0.03f, 0.36f, 0.21f)
        val Chalk = Color(0.86f, 0.94f, 0.89f)
        val Hash = Color(0.72f, 0.84f, 0.76f)
        val Numbers = Color(0.93f, 0.97f, 0.95f)

        /** The ball spot (blue) and the line to gain (yellow), as Expo marks them. */
        val Ball = Color(0.19f, 0.75f, 1f)
        val BallDot = Color(0f, 0.62f, 1f)
        val LineToGain = Color(1f, 0.86f, 0.23f)
        val ToGo = Color(0.79f, 0.91f, 0.84f).copy(alpha = 0.2f)
        val Card = Color(0.03f, 0.07f, 0.09f).copy(alpha = 0.9f)
    }
}

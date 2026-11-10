package com.strmr.tv.ui.sports.game.basketball

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import com.strmr.tv.sports.SportsTeam
import com.strmr.tv.ui.sports.SportsTeamLogo
import com.strmr.tv.ui.sports.game.SportsTeamColors
import com.strmr.tv.ui.sports.game.basketball.SportsCourtGeometry.Companion.LEFT_RIM
import com.strmr.tv.ui.sports.game.basketball.SportsCourtGeometry.Companion.MIDLINE
import com.strmr.tv.ui.sports.game.basketball.SportsCourtGeometry.Companion.RIGHT_RIM
import kotlin.math.asin
import kotlin.math.sqrt
import kotlin.math.tan

/** A shot to mark: its drawing-unit spot, its side's colour, and whether it went in. */
data class SportsCourtMark(val spot: Offset, val color: Color, val made: Boolean)

/**
 * The court drawn with Compose `Canvas` (Expo `SportsBasketballLiveCard`'s court, tvOS
 * `SportsBasketballCourt`): the floor (wood, Boston's parquet or Brooklyn's herringbone), both
 * lanes painted in the home colour, the lines, backboards and rims, the home logo at centre court,
 * then every shot: made shots filled, missed ones as rings, each in its side's colour. Drawn in
 * [SportsCourtGeometry] units scaled to the width.
 */
@Composable
fun SportsBasketballCourt(geometry: SportsCourtGeometry, home: SportsTeam, marks: List<SportsCourtMark>, modifier: Modifier = Modifier) {
    var width by remember { mutableIntStateOf(0) }
    val logo = with(LocalDensity.current) { (190 * width / SportsCourtGeometry.WIDTH).toFloat().toDp() }
    Box(
        modifier.fillMaxWidth().aspectRatio((SportsCourtGeometry.WIDTH / SportsCourtGeometry.HEIGHT).toFloat())
            .clip(RoundedCornerShape(6.dp)).onSizeChanged { width = it.width },
        contentAlignment = Alignment.Center,
    ) {
        Canvas(Modifier.fillMaxSize()) {
            scale((size.width / SportsCourtGeometry.WIDTH).toFloat(), Offset.Zero) {
                translate(-40f, -40f) {
                    clipRect(40f, 40f, 980f, 540f) {
                        floor(geometry)
                        lines(geometry)
                        for (mark in marks) {
                            if (mark.made) {
                                drawCircle(mark.color, 7f, mark.spot)
                                drawCircle(Color.Black.copy(alpha = 0.55f), 7f, mark.spot, style = Stroke(1.5f))
                            } else {
                                drawCircle(mark.color, 7f, mark.spot, style = Stroke(3f))
                            }
                        }
                    }
                }
            }
        }
        if (width > 0) Box(Modifier.alpha(0.88f)) { SportsTeamLogo(home, logo) }
    }
}

private fun hex(value: String) = SportsTeamColors.parse(value) ?: Color.Gray

private val LineColor = Color(0xFFF1E8D7)

private fun DrawScope.floor(geometry: SportsCourtGeometry) {
    when (geometry.floor) {
        SportsCourtGeometry.Floor.WOOD -> {
            drawRect(hex("#a18b69"), Offset(40f, 40f), Size(940f, 500f))
            val seam = hex("#604d36")
            for (row in 0 until 25) {
                val y = 40f + row * 20
                drawRect(hex(if (row % 2 == 0) "#927b5c" else "#baa079").copy(alpha = 0.2f), Offset(40f, y), Size(940f, 20f))
                drawLine(seam.copy(alpha = 0.18f), Offset(40f, y), Offset(980f, y), 1f)
                for (joint in 0 until 4) {
                    val x = 80f + joint * 240 + (row % 3) * 55
                    drawLine(seam.copy(alpha = 0.16f), Offset(x, y), Offset(x, y + 20), 1f)
                }
            }
        }
        SportsCourtGeometry.Floor.PARQUET -> {
            val grain = hex("#735b3b").copy(alpha = 0.3f)
            for (tx in 0 until 12) for (ty in 0 until 7) {
                val x = 40f + tx * 80
                val y = 40f + ty * 80
                val across = (tx + ty) % 2 == 0
                drawRect(hex(if (across) "#b99b70" else "#9e805a"), Offset(x, y), Size(80f, 80f))
                for (g in 1..4) {
                    val o = g * 16f
                    if (across) drawLine(grain, Offset(x, y + o), Offset(x + 80, y + o), 1f)
                    else drawLine(grain, Offset(x + o, y), Offset(x + o, y + 80), 1f)
                }
            }
        }
        SportsCourtGeometry.Floor.HERRINGBONE -> {
            drawRect(hex("#919394"), Offset(40f, 40f), Size(940f, 500f))
            val light = hex("#a2a4a5")
            val dark = hex("#8d9092")
            val edge = hex("#777b7d")
            translate(510f, 290f) {
                rotate(45f, Offset.Zero) {
                    for (i in -40..40) for (j in -10..10) {
                        val x = (i + 4 * j) * 16f
                        val y = (-i + 4 * j) * 16f
                        for ((rect, fill) in listOf(Rect(x, y, x + 64, y + 16) to light, Rect(x + 64, y, x + 80, y + 64) to dark)) {
                            if (kotlin.math.abs(rect.center.x) >= 760 || kotlin.math.abs(rect.center.y) >= 760) continue
                            drawRect(fill, rect.topLeft, rect.size)
                            drawRect(edge, rect.topLeft, rect.size, style = Stroke(0.8f))
                        }
                    }
                }
            }
        }
    }
}

private fun DrawScope.lines(geometry: SportsCourtGeometry) {
    val paint = hex(geometry.paint)
    val lane = geometry.laneHalf.toFloat()
    val mid = MIDLINE.toFloat()
    val line = Stroke(2f)
    val dashed = Stroke(2f, pathEffect = PathEffect.dashPathEffect(floatArrayOf(8f, 8f)))
    for (x in listOf(40f, 790f)) {
        drawRect(paint.copy(alpha = 0.88f), Offset(x, mid - lane), Size(190f, lane * 2))
        drawRect(LineColor, Offset(x, mid - lane), Size(190f, lane * 2), style = line)
    }
    drawRect(LineColor, Offset(40f, 40f), Size(940f, 500f), style = line)
    drawLine(LineColor, Offset(510f, 40f), Offset(510f, 540f), 2f)
    for (r in listOf(60f, 20f)) drawCircle(LineColor, r, Offset(510f, mid), style = line)
    // Free-throw circles: solid toward centre court, dashed toward the baseline.
    for ((x, rightSolid) in listOf(230f to true, 790f to false)) {
        val topLeft = Offset(x - 60, mid - 60)
        drawArc(LineColor, if (rightSolid) -90f else 90f, 180f, false, topLeft, Size(120f, 120f), style = line)
        drawArc(LineColor, if (rightSolid) 90f else -90f, 180f, false, topLeft, Size(120f, 120f), style = dashed)
    }
    for (x in listOf(110f, 150f, 180f, 210f, 810f, 840f, 870f, 910f)) {
        drawLine(LineColor, Offset(x, mid - lane), Offset(x, mid - lane - 6), 2f)
        drawLine(LineColor, Offset(x, mid + lane), Offset(x, mid + lane + 6), 2f)
    }
    listOf(
        320f to 40f to (320f to 70f), 700f to 40f to (700f to 70f), 320f to 540f to (320f to 510f), 700f to 540f to (700f to 510f),
        40f to 180f to (46f to 180f), 40f to 400f to (46f to 400f), 980f to 180f to (974f to 180f), 980f to 400f to (974f to 400f),
    ).forEach { (from, to) -> drawLine(LineColor, Offset(from.first, from.second), Offset(to.first, to.second), 2f) }
    drawPath(threePointLine(geometry), LineColor, style = line)
    if (geometry.restricted) {
        for ((rim, side) in listOf(LEFT_RIM.toFloat() to 1f, RIGHT_RIM.toFloat() to -1f)) {
            val arc = Path().apply {
                moveTo(rim - side * 12.5f, 250f)
                lineTo(rim, 250f)
                arcTo(Rect(Offset(rim, mid), 40f), -90f, side * 180f, false)
                lineTo(rim - side * 12.5f, 330f)
            }
            drawPath(arc, LineColor, style = line)
        }
    }
    for (x in listOf(80f, 940f)) drawLine(hex("#fff3dc"), Offset(x, 260f), Offset(x, 320f), 4f)
    for (x in listOf(LEFT_RIM.toFloat(), RIGHT_RIM.toFloat())) drawCircle(hex("#ffac64"), 7.5f, Offset(x, mid), style = Stroke(3f))
}

/** Both three-point lines: straight from the baseline to the corner, then the arc around the rim (two cubics a side, as Expo). */
private fun threePointLine(geometry: SportsCourtGeometry): Path {
    val r = geometry.threeRadius
    val corner = geometry.cornerDistance
    val offset = sqrt(r * r - corner * corner)
    val k = 4.0 / 3.0 * tan(asin(corner / r) / 4)
    val low = MIDLINE - corner
    val high = MIDLINE + corner
    return Path().apply {
        for (side in listOf(1.0, -1.0)) {
            val rim = if (side == 1.0) LEFT_RIM else RIGHT_RIM
            val edge = if (side == 1.0) 40.0 else 980.0
            val x = rim + side * offset
            val apex = rim + side * r
            moveTo(edge.toFloat(), low.toFloat())
            lineTo(x.toFloat(), low.toFloat())
            cubicTo((x + side * k * corner).toFloat(), (low + k * offset).toFloat(), apex.toFloat(), (MIDLINE - k * r).toFloat(), apex.toFloat(), MIDLINE.toFloat())
            cubicTo(apex.toFloat(), (MIDLINE + k * r).toFloat(), (x + side * k * corner).toFloat(), (high - k * offset).toFloat(), x.toFloat(), high.toFloat())
            lineTo(edge.toFloat(), high.toFloat())
        }
    }
}

package com.strmr.tv.ui.sports.game.baseball

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.border
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.tv.material3.Text
import com.strmr.tv.sports.SportsBaseballSituation
import com.strmr.tv.ui.sports.SportsMuted

/** A base with a runner on (Expo's gold). */
val SportsRunner = Color(0xFFFFD45C)

/**
 * The infield drawn with Compose `Canvas` (Expo `Diamond`, tvOS `SportsBaseballDiamond`): the base
 * paths, the mound and home plate, and first, second and third, gold with a runner on, white when
 * empty, dashed outlines when the feed didn't say who is on.
 */
@Composable
fun SportsBaseballDiamond(bases: List<Boolean>?, size: Dp = 72.dp) {
    Canvas(Modifier.size(size)) {
        scale(this.size.width / 120f, Offset.Zero) {
            drawPath(square(Offset(60f, 60f), 56f), Color.White.copy(alpha = 0.08f))
            drawPath(square(Offset(60f, 60f), 46f), Color.White.copy(alpha = 0.55f), style = Stroke(2f))
            drawCircle(Color.White.copy(alpha = 0.2f), 9f, Offset(60f, 65f))
            listOf(Offset(106f, 60f), Offset(60f, 14f), Offset(14f, 60f)).forEachIndexed { index, spot ->
                val base = square(spot, 10f)
                val occupied = bases?.get(index) == true
                if (bases != null) drawPath(base, if (occupied) SportsRunner else Color.White)
                drawPath(
                    base, if (occupied) SportsRunner else Color.White.copy(alpha = 0.55f),
                    style = Stroke(2f, pathEffect = if (bases == null) PathEffect.dashPathEffect(floatArrayOf(3f, 3f)) else null),
                )
            }
            val plate = Path().apply {
                moveTo(53f, 100f); lineTo(67f, 100f); lineTo(67f, 107f); lineTo(60f, 113f); lineTo(53f, 107f); close()
            }
            drawPath(plate, Color.White.copy(alpha = 0.55f))
        }
    }
}

/** A square standing on its corner around [center]. */
private fun square(center: Offset, half: Float) = Path().apply {
    moveTo(center.x, center.y - half)
    lineTo(center.x + half, center.y)
    lineTo(center.x, center.y + half)
    lineTo(center.x - half, center.y)
    close()
}

/**
 * Balls, strikes and outs as lights (Expo `BaseballCount`): green, yellow and red, each row only
 * when the feed reports a plain count; "Last reported" over old data.
 */
@Composable
fun SportsBaseballCount(situation: SportsBaseballSituation?, stale: Boolean) {
    Column(verticalArrangement = Arrangement.spacedBy(5.dp)) {
        if (stale) Text("Last reported", fontSize = 11.sp, color = SportsMuted)
        SportsCountRow.of(situation).forEach { row ->
            Row(
                Modifier.clearAndSetSemantics { contentDescription = "${row.label}: ${row.value}" },
                verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                Text("${row.label} ${row.value}", fontSize = 11.sp, modifier = Modifier.width(55.dp))
                repeat(row.top) { light ->
                    val on = light < row.value
                    Box(
                        Modifier.size(8.dp).clip(CircleShape).background(if (on) row.color else Color.Transparent)
                            .border(1.dp, if (on) row.color else SportsMuted, CircleShape),
                    )
                }
            }
        }
    }
}

/** A row of [SportsBaseballCount]: balls 0–3, strikes 0–2, outs 0–2. */
data class SportsCountRow(val label: String, val value: Int, val top: Int, val color: Color) {
    companion object {
        fun of(situation: SportsBaseballSituation?): List<SportsCountRow> = listOf(
            SportsCountRow("Balls", situation?.balls ?: -1, 3, Color(0xFF35CC87)),
            SportsCountRow("Strikes", situation?.strikes ?: -1, 2, SportsRunner),
            SportsCountRow("Outs", situation?.outs ?: -1, 2, Color(0xFFFF6474)),
        ).filter { it.value in 0..it.top }
    }
}

/**
 * Who is on base at a glance (Expo's hub card diamond): second on top, third and first below, gold
 * with a runner on, an outline when empty. For the scoreboard's situation line.
 */
@Composable
fun SportsBasesGlyph(bases: List<Boolean>, size: Dp = 11.dp) {
    Canvas(Modifier.size(width = size * 2.36f, height = size * 1.68f)) {
        val unit = this.size.width / 2.36f
        val half = unit / 2
        val step = unit * 0.68f
        val centerX = this.size.width / 2
        val top = half
        listOf(Offset(centerX + step, top + step), Offset(centerX, top), Offset(centerX - step, top + step)).forEachIndexed { index, spot ->
            val base = square(spot, half * 0.98f)
            if (bases[index]) drawPath(base, SportsRunner)
            drawPath(base, if (bases[index]) SportsRunner else Color.White.copy(alpha = 0.6f), style = Stroke(1.5.dp.toPx()))
        }
    }
}

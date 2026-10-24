package com.strmr.tv.ui.sports.game.soccer

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.tv.material3.Text
import com.strmr.tv.sports.SportsTeam
import com.strmr.tv.ui.sports.SportsMuted
import com.strmr.tv.ui.sports.SportsPanel
import com.strmr.tv.ui.sports.SportsReadout
import com.strmr.tv.ui.sports.SportsTeamLogo
import com.strmr.tv.ui.sports.game.SportsGameFormat
import com.strmr.tv.ui.sports.game.SportsGamePage
import com.strmr.tv.ui.sports.game.SportsTeamColors
import kotlin.math.acos

/**
 * Both sides' published formations (Expo `SportsSoccerPitch` / `SoccerFormationShape`, tvOS
 * `SportsSoccerPitch`): each side's crest, abbreviation and formation, then a pitch drawn with
 * Compose `Canvas`, the home side's shape attacking right and the away side's attacking left, one
 * marker per player in the team colours. These are the published shapes, never tracked
 * positions. [summaryOnly] (the Overview while play is on) keeps the formations and points to the
 * Lineups tab for the pitch. One stop for the remote.
 */
@Composable
fun SportsSoccerPitch(page: SportsGamePage, summaryOnly: Boolean = false) {
    val game = page.game
    val sides = listOf(game.homeTeam, game.awayTeam).map { team ->
        val formation = page.detail.lineups.firstOrNull { it.teamId == team.id }?.formation
        Side(team, formation, SportsSoccerFormation.rows(formation), SportsTeamColors.color(team, game))
    }
    val available = sides.any { it.rows != null }
    val spoken = sides.joinToString(". ") { "${it.team.name}: ${if (it.rows == null) "formation not supplied" else it.formation}" } +
        ". Published formations, not live player positions."
    SportsPanel("Formations") {
        SportsReadout(Modifier.semantics { contentDescription = spoken }) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                sides.forEach { side ->
                    Row(Modifier.weight(1f), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(7.dp)) {
                        SportsTeamLogo(side.team, 22.dp)
                        Column {
                            Text(side.team.shortName, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
                            Text(if (side.rows == null) "Not supplied" else side.formation.orEmpty(), fontSize = 12.sp, color = SportsMuted)
                        }
                    }
                }
            }
            if (available && !summaryOnly) {
                Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                    Canvas(Modifier.heightIn(max = 210.dp).aspectRatio(1.65f).clip(RoundedCornerShape(5.dp))) {
                        pitch(sides.map { (it.rows ?: emptyList()) to it.color })
                    }
                }
            }
            Text(
                when {
                    !available -> "Formations have not been supplied for this match."
                    summaryOnly -> "Published formations · Full pitch in Lineups"
                    else -> "Published formations · Not live player positions"
                },
                fontSize = 12.sp, color = SportsMuted,
            )
        }
    }
}

private class Side(val team: SportsTeam, val formation: String?, val rows: List<Int>?, val color: Color)

/** Soccer formation rules (Expo `SoccerFormationShape`). */
object SportsSoccerFormation {
    /** Goalkeeper plus the outfield lines ("4-3-3" → [1, 4, 3, 3]) when the lines add up to ten. */
    fun rows(formation: String?): List<Int>? {
        if (formation == null || !SportsGameFormat.matches("""^\d(?:-\d){1,4}$""", formation)) return null
        val rows = formation.split("-").map { it.toInt() }
        if (rows.any { it !in 1..5 } || rows.sum() != 10) return null
        return listOf(1) + rows
    }
}

private val Turf = Color(0xFF173D30)
private val StripeA = Color(0xFF204934)
private val StripeB = Color(0xFF24513C)
private val Line = Color(0xFFE7F1DF)

/**
 * The pitch in Expo's 660 × 400 units: striped turf, touch and goal lines, halfway line and centre
 * circle, both penalty and goal areas with their arcs and spots, the goals, then the home side's
 * markers from the left goal and the away side's from the right.
 */
private fun DrawScope.pitch(sides: List<Pair<List<Int>, Color>>) {
    val scale = minOf(size.width / 660f, size.height / 400f)
    translate((size.width - 660f * scale) / 2, (size.height - 400f * scale) / 2) {
        withTransform({ scale(scale, scale, Offset.Zero) }) {
            drawRect(Turf, Offset.Zero, Size(660f, 400f))
            for (i in 0 until 10) drawRect(if (i % 2 == 0) StripeA else StripeB, Offset(20f + i * 62f, 20f), Size(62f, 360f))
            val stroke = Stroke(2f)
            fun box(x: Float, y: Float, w: Float, h: Float) = drawRect(Line, Offset(x, y), Size(w, h), style = stroke)
            box(20f, 20f, 620f, 360f)
            drawLine(Line, Offset(330f, 20f), Offset(330f, 380f), 2f)
            drawCircle(Line, 52f, Offset(330f, 200f), style = stroke)
            box(20f, 92f, 96f, 216f); box(544f, 92f, 96f, 216f)
            box(20f, 150f, 34f, 100f); box(606f, 150f, 34f, 100f)
            box(9f, 177f, 11f, 46f); box(640f, 177f, 11f, 46f)
            // The penalty arcs: the part of each spot's 52-unit circle outside the area.
            val reach = Math.toDegrees(acos(31.0 / 52.0)).toFloat()
            drawArc(Line, -reach, reach * 2, false, Offset(85f - 52f, 148f), Size(104f, 104f), style = stroke)
            drawArc(Line, 180f - reach, reach * 2, false, Offset(575f - 52f, 148f), Size(104f, 104f), style = stroke)
            listOf(330f, 85f, 575f).forEach { drawCircle(Line, 3f, Offset(it, 200f)) }
            sides.forEachIndexed { index, (rows, color) ->
                rows.forEachIndexed { row, count ->
                    val x = 44f + row * 244f / maxOf(1, rows.size - 1)
                    for (i in 0 until count) {
                        val center = Offset(if (index == 0) x else 660f - x, 20f + (i + 1) * 360f / (count + 1))
                        drawCircle(color, 10f, center)
                        drawCircle(Color.White, 10f, center, style = Stroke(2.5f))
                    }
                }
            }
        }
    }
}

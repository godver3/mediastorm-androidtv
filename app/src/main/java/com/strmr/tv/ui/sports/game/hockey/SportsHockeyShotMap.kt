package com.strmr.tv.ui.sports.game.hockey

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.tv.material3.Text
import com.strmr.tv.sports.SportsHockeyShot
import com.strmr.tv.sports.SportsTeam
import com.strmr.tv.ui.sports.SportsAccent
import com.strmr.tv.ui.sports.SportsMuted
import com.strmr.tv.ui.sports.SportsPanel
import com.strmr.tv.ui.sports.SportsReadout
import com.strmr.tv.ui.sports.SportsTeamLogo
import com.strmr.tv.ui.sports.game.SportsGamePage
import kotlin.math.abs

/**
 * The NHL's shot attempts on a rink drawn with Compose `Canvas` (Expo `SportsExtendedOverview`,
 * "Shot attempts"; tvOS `SportsHockeyShotMap`): the boards, centre red line, blue lines, goal lines
 * and creases, faceoff circles, then every attempt in the provider's coordinates (feet, 200 × 85
 * around centre ice; the teams change ends each period): the away side cyan, the home side gold,
 * goals larger with a white ring. One stop for the remote; nothing without attempts on the rink.
 */
@Composable
fun SportsHockeyShotMap(page: SportsGamePage) {
    val shots = hockeyRinkShots(page)
    val game = page.game
    val spoken = "Shot attempts map, ${game.awayTeam.name} cyan, ${game.homeTeam.name} gold, goals outlined"
    SportsPanel("Shot Attempts", note = "Includes saved, missed and blocked attempts · Provider coordinates; teams change ends") {
        SportsReadout(Modifier.semantics { contentDescription = spoken }) {
            Rink(page, shots)
            Row(horizontalArrangement = Arrangement.spacedBy(16.dp), verticalAlignment = Alignment.CenterVertically) {
                Key(game.awayTeam, AwayShot, shots.count { it.teamId == game.awayTeam.id })
                Key(game.homeTeam, HomeShot, shots.count { it.teamId == game.homeTeam.id })
                Row(horizontalArrangement = Arrangement.spacedBy(5.dp), verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.size(9.dp).clip(CircleShape).background(SportsMuted).border(1.dp, Color.White, CircleShape))
                    Text("Goal", fontSize = 12.sp)
                }
            }
        }
    }
}

/** Attempts on the rink (Expo keeps |x| ≤ 100, |y| ≤ 42.5); NHL only. */
fun hockeyRinkShots(page: SportsGamePage): List<SportsHockeyShot> {
    if (page.game.league != "nhl") return emptyList()
    return page.detail.shots.filter { it.x.isFinite() && it.y.isFinite() && abs(it.x) <= 100 && abs(it.y) <= 42.5 }
}

private val AwayShot = SportsAccent
private val HomeShot = Color(0xFFFFD478)
private val Unknown = Color(0xFFAAB3BD)

@Composable
private fun Key(team: SportsTeam, color: Color, count: Int) {
    Row(horizontalArrangement = Arrangement.spacedBy(5.dp), verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(8.dp).clip(CircleShape).background(color))
        SportsTeamLogo(team, 16.dp)
        Text("${team.shortName} · $count", fontSize = 12.sp)
    }
}

@Composable
private fun Rink(page: SportsGamePage, shots: List<SportsHockeyShot>) {
    val game = page.game
    Canvas(Modifier.fillMaxWidth().aspectRatio(208f / 94f)) {
        scale(size.width / 208f, Offset.Zero) {
            translate(104f, 47f) {
                val boards = Path().apply { addRoundRect(RoundRect(Rect(-100f, -42.5f, 100f, 42.5f), CornerRadius(20f))) }
                drawPath(boards, Color(0xFF202B36))
                val red = Color(0xFFA36B73)
                val blue = Color(0xFF47698C)
                val faint = Color(0xFF66778A)
                clipPath(boards) {
                    fun vertical(x: Float, color: Color, width: Float) = drawLine(color, Offset(x, -42.5f), Offset(x, 42.5f), width)
                    vertical(0f, red, 0.8f)
                    vertical(-25f, blue, 1f)
                    vertical(25f, blue, 1f)
                    vertical(-89f, red, 0.5f)
                    vertical(89f, red, 0.5f)
                    // Creases: half circles in front of each goal, toward centre ice.
                    drawArc(Color(0xFF4D7399).copy(alpha = 0.5f), -90f, 180f, true, Offset(-95f, -6f), Size(12f, 12f))
                    drawArc(Color(0xFF4D7399).copy(alpha = 0.5f), 90f, 180f, true, Offset(83f, -6f), Size(12f, 12f))
                    drawCircle(faint, 15f, Offset.Zero, style = Stroke(0.6f))
                    for (x in listOf(-69f, 69f)) for (y in listOf(-22f, 22f)) {
                        drawCircle(red.copy(alpha = 0.6f), 15f, Offset(x, y), style = Stroke(0.5f))
                        drawCircle(red, 1f, Offset(x, y))
                    }
                }
                drawPath(boards, faint, style = Stroke(0.6f))
                for (shot in shots.sortedBy { it.isGoal }) {
                    val radius = if (shot.isGoal) 2.6f else 1.6f
                    val spot = Offset(shot.x.toFloat(), -shot.y.toFloat())
                    val color = when (shot.teamId) {
                        game.awayTeam.id -> AwayShot
                        game.homeTeam.id -> HomeShot
                        else -> Unknown
                    }
                    drawCircle(color.copy(alpha = 0.85f), radius, spot)
                    if (shot.isGoal) drawCircle(Color.White, radius, spot, style = Stroke(0.7f))
                }
            }
        }
    }
}

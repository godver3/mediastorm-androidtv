package com.strmr.tv.ui.sports.game.football

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.widthIn
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.tv.material3.Text
import com.strmr.tv.sports.SportsTeam
import com.strmr.tv.ui.Accent
import com.strmr.tv.ui.sports.SportsMuted
import com.strmr.tv.ui.sports.SportsPanel
import com.strmr.tv.ui.sports.SportsReadout
import com.strmr.tv.ui.sports.game.SportsGamePage
import com.strmr.tv.ui.sports.game.SportsTeamColors

/**
 * The Overview's field (Expo `FootballPossessionField`, tvOS `SportsFootballPossessionField`): the
 * offense attacking right from its own end zone, the ball (blue) and the line to gain (yellow)
 * with the yards to go shaded, and the current drive's latest four plays as lanes of net movement,
 * or the drive's arrow up to the ball before its plays arrive. A break, a score, a final or old
 * data shows as a card over the turf. One stop for the remote, read out as one sentence.
 */
@Composable
fun SportsFootballPossessionField(page: SportsGamePage, possession: SportsFootballPossession) {
    val fieldPlays = possession.plays.filter(SportsFootballPlayInfo::isFieldPlay)
    val lanes = fieldPlays.takeLast(4).map { SportsFootballPlayInfo.of(it, possession.offense, page.game) }
    val pause = possession.pause
    val offense = possession.offense
    SportsPanel(null) {
        SportsReadout(Modifier.semantics { contentDescription = spoken(possession, lanes) }) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(pause?.title ?: "${offense.shortName} possession →", fontSize = 15.sp, fontWeight = FontWeight.SemiBold, color = Accent)
                Spacer(Modifier.weight(1f))
                Text(pause?.let { it.subtitle.orEmpty() } ?: situationLine(possession), fontSize = 15.sp, fontWeight = FontWeight.SemiBold, textAlign = TextAlign.End)
            }
            val stats = possession.currentDrive?.statsLine
            if (pause == null && stats != null) Text("${offense.nickname?.ifEmpty { null } ?: offense.name} · Current drive · $stats", fontSize = 11.sp, color = SportsMuted)
            SportsFootballField(
                offense, possession.opponent, page.colorHex(offense), page.colorHex(possession.opponent),
                Modifier.widthIn(max = 500.dp).fillMaxWidth(),
            ) { marks(page, possession, lanes) }
            Text(legend(possession, more = fieldPlays.size > 4), fontSize = 11.sp, color = SportsMuted)
            when {
                pause == null && possession.currentDrive == null -> Text("Waiting for current-drive details.", fontSize = 11.sp, color = SportsMuted)
                pause == null && possession.plays.isEmpty() -> Text("Individual plays not supplied yet.", fontSize = 11.sp, color = SportsMuted)
            }
        }
    }
}

private fun situationLine(possession: SportsFootballPossession) =
    listOfNotNull(possession.situation?.downDistance, possession.situation?.fieldPosition).filter { it.isNotEmpty() }.joinToString(" · ")

private fun legend(possession: SportsFootballPossession, more: Boolean): String =
    (listOf(possession.pause?.title ?: "Current drive", "Blue: ball", "Yellow: first down", "Yards: net field movement") +
        if (more) listOf("Latest four plays on field") else emptyList()).joinToString(" · ")

private fun SportsFieldPen.marks(page: SportsGamePage, possession: SportsFootballPossession, lanes: List<SportsFootballPlayInfo>) {
    val hex = page.colorHex(possession.offense)
    val color = SportsTeamColors.parse(hex) ?: Accent
    val arrowColor = if (SportsTeamColors.contrast(hex, "#075332") >= 3) color else Color.White
    val ink = if (SportsTeamColors.contrast(hex, "#ffffff") >= 4.5) Color.White else Color.Black
    val ball = possession.ball
    if (ball != null) {
        possession.target?.let { target ->
            val x1 = SportsFieldSpace.x(minOf(ball, target)).toFloat()
            val x2 = SportsFieldSpace.x(maxOf(ball, target)).toFloat()
            scope.drawRect(SportsFieldPen.ToGo, Offset(x1, SportsFieldSpace.TOP), Size(x2 - x1, SportsFieldSpace.BOTTOM - SportsFieldSpace.TOP))
            yardLine(target, SportsFieldPen.LineToGain, 2f)
        }
        yardLine(ball, SportsFieldPen.Ball, 2f)
    }
    if (possession.pause == null) {
        lanes.forEachIndexed { index, lane ->
            val y = 105f + index * 58f
            val start = lane.start
            val end = lane.end
            if (start != null && end != null) {
                scope.lane(
                    SportsFieldSpace.x(start).toFloat(), SportsFieldSpace.x(end).toFloat(), y, 11f, color,
                    if (index == lanes.size - 1) 2.5f else 1.2f,
                )
            }
            val place = SportsRibbonLabel.of(lane.start, lane.end, lane.movement)
            text(lane.movement, place.x.toFloat(), if (place.above) y - 22 else y, 12f, if (place.inside) ink else Color.White, anchor = place.anchor.pen)
        }
        val start = possession.currentDrive?.knownStart
        if (lanes.isEmpty() && start != null && ball != null) {
            val own = SportsFootballYards.own(start, possession.offense, page.game)
            scope.arrow(SportsFieldSpace.x(own).toFloat(), SportsFieldSpace.x(ball).toFloat(), 296f, arrowColor, current = true, score = false)
        }
    }
    if (ball != null) scope.dot(SportsFieldSpace.x(ball).toFloat(), 296f, 7f, SportsFieldPen.BallDot, 2f)
    possession.pause?.let { pause ->
        val height = if (pause.subtitle == null) 60f else 84f
        scope.drawRoundRect(SportsFieldPen.Card, Offset(185f, 142f), Size(490f, height), CornerRadius(10f))
        text(pause.title, 430f, 172f, 28f, Color.White)
        pause.subtitle?.let { text(it, 430f, 204f, 17f, Color(0xFFE6E6E6), FontWeight.Normal) }
    }
}

/** The label anchor as the pen takes it. */
internal val SportsRibbonLabel.Anchor.pen: SportsFieldPen.Anchor
    get() = when (this) {
        SportsRibbonLabel.Anchor.LEADING -> SportsFieldPen.Anchor.LEADING
        SportsRibbonLabel.Anchor.CENTER -> SportsFieldPen.Anchor.CENTER
        SportsRibbonLabel.Anchor.TRAILING -> SportsFieldPen.Anchor.TRAILING
    }

/** What TalkBack reads (Expo's readout label). */
private fun spoken(possession: SportsFootballPossession, lanes: List<SportsFootballPlayInfo>): String {
    val pause = possession.pause
    if (pause != null && pause.kind == SportsFootballPause.Kind.WAITING) return listOfNotNull(pause.title, pause.subtitle).joinToString(". ")
    val opening = pause?.let { listOfNotNull(it.title, it.subtitle).joinToString(". ") } ?: "${possession.offense.name} possession, attacks right."
    val spot = possession.situation?.fieldPosition?.ifEmpty { null } ?: "Position unavailable"
    val plays = lanes.joinToString(". ") { "${it.type}, ${it.movement}" }
    return listOf(opening, possession.situation?.downDistance.orEmpty(), spot, "Current drive only.", plays).filter { it.isNotEmpty() }.joinToString(" ")
}

/** The sides' readable colours as `#rrggbb` (Expo `sportsMatchupColors`). */
val SportsGamePage.colorPair: SportsTeamColors.HexPair
    get() = SportsTeamColors.hexPair(game.awayTeam.color, game.homeTeam.color, game.awayTeam.alternateColor, game.homeTeam.alternateColor)

fun SportsGamePage.colorHex(team: SportsTeam): String = if (team.id == game.homeTeam.id) colorPair.home else colorPair.away

/** A side's colour for text on the page's dark panels, plain white when it would be too dark. */
fun SportsGamePage.textColor(team: SportsTeam): Color {
    val hex = colorHex(team)
    return if (SportsTeamColors.contrast(hex, "#262628") >= 3) SportsTeamColors.parse(hex) ?: Color.White else Color.White
}

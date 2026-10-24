package com.strmr.tv.ui.sports.game.football

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.key
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.tv.material3.Text
import com.strmr.tv.sports.SportsFootballDrive
import com.strmr.tv.sports.SportsFootballPlay
import com.strmr.tv.sports.SportsTeam
import com.strmr.tv.ui.Accent
import com.strmr.tv.ui.sports.SportsMuted
import com.strmr.tv.ui.sports.SportsReadout
import com.strmr.tv.ui.sports.game.SportsGamePage
import com.strmr.tv.ui.sports.game.SportsTeamColors
import kotlin.math.min

/**
 * The chosen drive in the Drives tab (tvOS `SportsDriveChart`): its side, quarter, span, result and
 * figures, the field with one lane per snap (the play in focus highlighted) over the drive's own
 * arrow, then its plays in order. The field is one stop for the remote ([fieldFocus]); each play
 * is one ([firstPlay] the first), in a list that scrolls on its own so the field stays in view.
 * Left from any of them goes back to the chosen drive ([back]), wherever it sits in the list.
 */
@Composable
fun ColumnScope.SportsDriveChart(
    page: SportsGamePage,
    drive: SportsFootballDrive,
    number: Int,
    result: SportsDriveResult,
    fieldFocus: FocusRequester,
    firstPlay: FocusRequester,
    back: FocusRequester,
    highlighted: String?,
    onHighlight: (String?) -> Unit,
) {
    val game = page.game
    val team = page.team(drive.teamId) ?: game.awayTeam
    val opponent = if (team.id == game.awayTeam.id) game.homeTeam else game.awayTeam
    val lanes = SportsDriveExplorer.lanes(drive.plays)
    Row(Modifier.fillMaxWidth().padding(horizontal = 10.dp), verticalAlignment = Alignment.Top) {
        Column(Modifier.weight(1f)) {
            Text("Drive $number · ${team.name}", fontSize = 16.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(
                (listOf(drive.period, drive.span) + listOfNotNull(drive.statsLine)).filter { it.isNotEmpty() }.joinToString(" · "),
                fontSize = 11.sp, color = SportsMuted,
            )
        }
        SportsDriveResultText(result, 15.sp)
    }
    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        SportsReadout(
            Modifier.width(280.dp).focusRequester(fieldFocus).focusProperties { left = back }
                .semantics { contentDescription = spoken(page, drive, number, team, result, lanes) },
        ) {
            SportsFootballField(team, opponent, page.colorHex(team), page.colorHex(opponent)) { marks(page, drive, team, result, lanes, highlighted) }
        }
        Column(Modifier.padding(top = 8.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            legend(drive, lanes).forEach { Text(it, fontSize = 11.sp, color = SportsMuted) }
        }
    }
    Column(Modifier.weight(1f).fillMaxWidth().verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(2.dp)) {
        if (drive.plays.isEmpty()) SportsReadout { Text("Plays not supplied for this drive.", fontSize = 14.sp, color = SportsMuted) }
        drive.plays.forEachIndexed { index, play ->
            key(play.id) {
                SportsDrivePlayRow(
                    play, SportsFootballPlayInfo.of(play, team, game), page.textColor(team),
                    (if (index == 0) Modifier.focusRequester(firstPlay) else Modifier).focusProperties { left = back }.onFocusChanged {
                        if (it.isFocused) onHighlight(play.id) else if (highlighted == play.id) onHighlight(null)
                    },
                )
            }
        }
    }
}

private fun legend(drive: SportsFootballDrive, lanes: List<SportsFootballPlay>): List<String> {
    val snaps = drive.plays.count(SportsFootballPlayInfo::isFieldPlay)
    return listOf("Attacking right →", "Lanes: each snap", "Arrow: whole drive", "Gold ring: a score") +
        if (snaps > lanes.size && lanes.isNotEmpty()) listOf("Latest ${lanes.size} snaps") else emptyList()
}

private fun SportsFieldPen.marks(
    page: SportsGamePage, drive: SportsFootballDrive, team: SportsTeam, result: SportsDriveResult, lanes: List<SportsFootballPlay>, highlighted: String?,
) {
    val hex = page.colorHex(team)
    val color = SportsTeamColors.parse(hex) ?: Accent
    val ink = if (SportsTeamColors.contrast(hex, "#ffffff") >= 4.5) Color.White else Color.Black
    fun x(yard: Double) = SportsFieldSpace.x(SportsFootballYards.own(yard, team, page.game)).toFloat()
    val start = drive.knownStart
    val end = drive.knownEnd
    if (drive.hasReliablePath && start != null && end != null) {
        val arrowColor = if (SportsTeamColors.contrast(hex, "#075332") >= 3) color else Color.White
        // Under the lanes, or across the middle when the feed sent no plays.
        val y = if (lanes.isEmpty()) 181f else 304f
        scope.arrow(x(start), x(end), y, arrowColor, result.current, result.scoring, width = if (lanes.isEmpty()) 5f else 3f)
        if (result.scoring) scope.drawCircle(SportsTeamColors.scoring, 11f, Offset(x(end), y), style = Stroke(2.5f))
    } else if (lanes.isEmpty()) {
        scope.drawRoundRect(SportsFieldPen.Card, Offset(250f, 152f), Size(346f, 58f), CornerRadius(10f))
        text("Positions not supplied", 423f, 181f, 24f, Color.White, FontWeight.SemiBold)
    }
    val count = lanes.size
    val spacing = if (count > 1) min(58f, 196f / (count - 1)) else 0f
    val first = if (count > 1) 80 + (196 - spacing * (count - 1)) / 2 else 178f
    val half = if (count > 1) min(11f, spacing * 0.36f) else 11f
    lanes.forEachIndexed { index, play ->
        val laneStart = play.start ?: return@forEachIndexed
        val laneEnd = play.end ?: return@forEachIndexed
        val y = first + index * spacing
        val lit = highlighted == null || highlighted == play.id
        scope.lane(x(laneStart), x(laneEnd), y, half, color, if (highlighted == play.id) 2.5f else 1f, alpha = if (lit) 1f else 0.45f)
        if (highlighted == play.id || (highlighted == null && count <= 4)) {
            val info = SportsFootballPlayInfo.of(play, team, page.game)
            val place = SportsRibbonLabel.of(info.start, info.end, info.movement)
            // Inside a ribbon wide and tall enough, else beside its tail, else above it.
            val inside = place.inside && half >= 8
            val beside = !place.inside && !place.above
            text(info.movement, place.x.toFloat(), if (inside || beside) y else y - half - 9, 13f, if (inside) ink else Color.White, anchor = place.anchor.pen)
        }
    }
}

private fun spoken(page: SportsGamePage, drive: SportsFootballDrive, number: Int, team: SportsTeam, result: SportsDriveResult, lanes: List<SportsFootballPlay>) =
    (listOf("Drive $number, ${team.name}, attacking right.", drive.span, result.text) +
        lanes.map { SportsFootballPlayInfo.of(it, team, page.game).let { info -> "${info.type}, ${info.movement}" } }).joinToString(". ")

/**
 * A play of the chosen drive: when, the down and distance and the spot, the result in a few words,
 * the feed's narrative, and the net movement in the side's colour.
 */
@Composable
private fun SportsDrivePlayRow(play: SportsFootballPlay, info: SportsFootballPlayInfo, color: Color, modifier: Modifier) {
    val scoring = play.scoring == true
    SportsReadout(modifier) {
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Column(Modifier.width(40.dp)) {
                Text(play.period.orEmpty(), fontSize = 11.sp, color = SportsMuted)
                Text(play.clock.orEmpty(), fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
            }
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                val situation = listOfNotNull(info.down, info.spot).joinToString(" · ")
                if (situation.isNotEmpty()) Text(situation, fontSize = 11.sp, color = SportsMuted)
                Text(
                    (if (scoring) "◆ " else "") + info.result, fontSize = 14.sp, fontWeight = FontWeight.SemiBold,
                    color = if (scoring) SportsTeamColors.scoring else Color.White,
                )
                play.description.trim().takeIf { it.isNotEmpty() }?.let {
                    Text(it, fontSize = 12.sp, color = SportsMuted, maxLines = 3, overflow = TextOverflow.Ellipsis)
                }
            }
            Spacer(Modifier.width(4.dp))
            if (info.start != null && info.end != null && SportsFootballPlayInfo.isFieldPlay(play)) {
                Text(info.movement, fontSize = 14.sp, fontWeight = FontWeight.SemiBold, color = color)
            }
        }
    }
}

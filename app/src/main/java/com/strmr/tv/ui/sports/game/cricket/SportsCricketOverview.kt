package com.strmr.tv.ui.sports.game.cricket

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.tv.material3.Text
import com.strmr.tv.sports.SportsLifecycle
import com.strmr.tv.ui.sports.SportsMuted
import com.strmr.tv.ui.sports.SportsPanel
import com.strmr.tv.ui.sports.SportsReadout
import com.strmr.tv.ui.sports.SportsTeamLogo
import com.strmr.tv.ui.sports.game.SportsDigits
import com.strmr.tv.ui.sports.game.SportsColumn
import com.strmr.tv.ui.sports.game.SportsColumns
import com.strmr.tv.ui.sports.game.SportsGameFormat
import com.strmr.tv.ui.sports.game.SportsGamePage
import com.strmr.tv.ui.sports.game.SportsMatchInfo
import com.strmr.tv.ui.sports.game.SportsPregamePreview
import com.strmr.tv.ui.sports.game.hasMatchNotes
import com.strmr.tv.ui.sports.game.matchInfo

/**
 * The Overview of cricket matches (Expo `SportsSportOverview`'s cricket branch, tvOS
 * `SportsCricketOverview`): the innings scorecard beside the reported highlights, the player
 * scorecards and the match information.
 */
@Composable
fun SportsCricketOverview(page: SportsGamePage) {
    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(20.dp)) {
        SportsColumns(fullHeight = true) {
            SportsColumn(1.2f) { SportsCricketInningsPanel(page) }
            SportsColumn(1f) {
                SportsCricketScorecards(page)
                if (page.matchInfo.isNotEmpty() || page.hasMatchNotes) SportsMatchInfo(page)
            }
        }
        if (page.lifecycle == SportsLifecycle.SCHEDULED) SportsPregamePreview(page, page.detail.pregame)
    }
}

/**
 * Every innings (Expo `SportsCricketOverview`, "Innings scorecard"): the reported state or result,
 * then per innings the side, its label ("1st innings", "Batting · 2nd innings"), the score and
 * overs, and for the side chasing the target, runs needed and a progress bar. One stop each.
 */
@Composable
fun SportsCricketInningsPanel(page: SportsGamePage) {
    val lines = SportsCricket.lines(page)
    val final = page.lifecycle == SportsLifecycle.FINAL
    SportsPanel("Innings Scorecard") {
        Text(
            SportsGameFormat.detailStatus(page), fontSize = if (final) 19.sp else 14.sp, fontWeight = if (final) FontWeight.Bold else FontWeight.Normal,
            color = if (final) Color.White else SportsMuted, modifier = Modifier.padding(horizontal = 10.dp, vertical = 2.dp),
        )
        if (lines.isEmpty()) {
            SportsReadout {
                Text(
                    if (page.lifecycle == SportsLifecycle.SCHEDULED) "Innings will appear when play begins." else "Innings details have not been supplied.",
                    fontSize = 14.sp, color = SportsMuted,
                )
            }
        }
        lines.forEach { Innings(it) }
    }
}

@Composable
private fun Innings(line: SportsCricket.Line) {
    val name = line.team?.name ?: "Team not supplied"
    val spoken = listOfNotNull(name, line.state, line.label, line.score, line.innings.overs?.let { "$it overs" }, line.chase, line.innings.description)
        .filter { it.isNotEmpty() }.joinToString(". ")
    Box(Modifier.fillMaxWidth().padding(horizontal = 10.dp).height(1.dp).background(Color.White.copy(alpha = 0.12f)))
    SportsReadout(Modifier.semantics { contentDescription = spoken }) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            line.team?.let { SportsTeamLogo(it, 22.dp) }
            Text(name, fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
            Spacer(Modifier.weight(1f))
            Text(if (line.current) "${line.state} · ${line.label}" else line.state, fontSize = 12.sp, color = SportsMuted)
        }
        Row(verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(
                line.score, fontSize = if (line.current) 26.sp else 20.sp, style = SportsDigits,
                fontWeight = if (line.current) FontWeight.Bold else FontWeight.SemiBold,
            )
            line.innings.overs?.let { Text("$it overs", fontSize = 14.sp, color = SportsMuted, modifier = Modifier.padding(bottom = 3.dp)) }
        }
        line.progress?.let { progress ->
            Box(Modifier.fillMaxWidth().height(3.dp).clip(CircleShape).background(Color.White.copy(alpha = 0.14f))) {
                Box(Modifier.fillMaxWidth(progress.toFloat()).fillMaxHeight().clip(CircleShape).background(Color.White))
            }
        }
        if (line.chase.isNotEmpty()) Text(line.chase, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
        line.innings.description?.let { Text(it, fontSize = 12.sp, color = SportsMuted) }
    }
}

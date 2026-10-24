package com.strmr.tv.ui.sports.game

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.tv.material3.Text
import com.strmr.tv.sports.SportsFootballSituation
import com.strmr.tv.sports.SportsLifecycle
import com.strmr.tv.sports.SportsTeam
import com.strmr.tv.sports.liveBasketballSituation
import com.strmr.tv.sports.liveFootballSituation
import com.strmr.tv.sports.liveHockeySituation
import com.strmr.tv.sports.side
import com.strmr.tv.ui.Accent
import com.strmr.tv.ui.sports.SportsMuted
import com.strmr.tv.ui.sports.game.baseball.SportsBaseballState
import com.strmr.tv.ui.sports.game.baseball.SportsBasesGlyph

/**
 * The live situation the scoreboard carries (Expo `SportsEditorialScoreboard`, tvOS
 * `SportsHeaderSituation`): for NFL and college football, a football by the side with the ball,
 * each side's timeouts under its score, and the down and distance and the ball's spot under the
 * clock; for MLB, the runners and the outs and count under the inning (`BaseballSituationLine`);
 * for basketball, possession and the bonus, and for the NHL the strength and each side's shots
 * (Expo `Situation`; the shots come from the match stats when the feed sends no situation).
 * Only while live with scores shown, so nothing of it shows with scores hidden.
 */
data class SportsHeaderSituation(
    val football: SportsFootballSituation?,
    val offense: SportsTeam?,
    /** An at-bat under way. */
    val baseball: SportsBaseballState?,
    val stale: Boolean,
    /** Basketball's and hockey's lines ("PHI possession", "Shots BOS 31 · NSH 28"). */
    val other: List<String> = emptyList(),
) {
    fun hasBall(team: SportsTeam): Boolean = offense?.id == team.id

    /** The lines under the clock: "3rd & 4" and "Ball at CHI 12", or "2 outs · Count 3–2". */
    val lines: List<String>
        get() {
            football?.let { return listOfNotNull(it.downDistance.ifEmpty { null }, it.ballAt) }
            baseball?.scoreboardLine(stale)?.let { return listOf(it) }
            return other
        }

    companion object {
        fun of(page: SportsGamePage, spoilers: Boolean): SportsHeaderSituation {
            val shown = !spoilers && page.lifecycle == SportsLifecycle.LIVE && page.game.showsScore
            val football = if (shown) page.game.liveFootballSituation else null
            val state = if (shown && page.game.league == "mlb") SportsBaseballState.of(page.game) else null
            return SportsHeaderSituation(
                football, football?.offense(page.game), state?.takeIf { it.active }, page.isStale(), if (shown) otherLines(page) else emptyList(),
            )
        }

        private fun otherLines(page: SportsGamePage): List<String> {
            val game = page.game
            game.liveBasketballSituation?.let { basketball ->
                val side = game.side(basketball.possession)?.shortName ?: basketball.possession
                return listOfNotNull(side.ifEmpty { null }?.let { "$it possession" }, basketball.bonus)
            }
            if (game.league != "nhl") return emptyList()
            val situation = game.liveHockeySituation
            val row = page.detail.comparisons.firstOrNull { SportsGameFormat.matches("^shots on goal$", it.label) }
            fun shots(team: SportsTeam): String? {
                val count = if (team.id == game.homeTeam.id) situation?.shotsHome else situation?.shotsAway
                return count?.toString() ?: row?.let { page.value(it, team) }?.ifEmpty { null }
            }
            val teams = page.teams
            val line = if (teams.all { shots(it) != null }) "Shots ${teams[0].shortName} ${shots(teams[0])} · ${teams[1].shortName} ${shots(teams[1])}" else null
            return listOfNotNull(situation?.strength?.ifEmpty { null }, line)
        }
    }
}

/** Under the clock: football's down and distance and the ball's spot, or baseball's runners with the outs and the count. */
@Composable
fun SportsHeaderSituationLines(situation: SportsHeaderSituation) {
    val lines = situation.lines
    if (lines.isEmpty()) return
    val bases = situation.baseball?.situation?.occupiedBases
    val spoken = (listOfNotNull(situation.baseball?.situation?.bases) + lines).joinToString(", ")
    Column(
        Modifier.clearAndSetSemantics { contentDescription = spoken },
        horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(3.dp),
    ) {
        if (bases != null) SportsBasesGlyph(bases)
        lines.forEach { Text(it, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, textAlign = TextAlign.Center) }
    }
}

/** A side's timeouts left under its score (Expo `FootballTimeouts`): "TO" and three lights, a dash when the feed didn't say. */
@Composable
fun SportsTimeouts(count: Int?, team: SportsTeam) {
    val spoken = count?.let { "${team.shortName}: $it timeouts remaining" } ?: "${team.shortName}: timeouts unknown"
    Row(
        Modifier.clearAndSetSemantics { contentDescription = spoken },
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(3.dp),
    ) {
        Text("TO", fontSize = 10.sp, fontWeight = FontWeight.SemiBold, color = SportsMuted)
        if (count != null) {
            repeat(3) { index ->
                Box(Modifier.size(5.dp).clip(CircleShape).background(if (index < count) Color.White else Color.Transparent).border(0.5.dp, SportsMuted, CircleShape))
            }
        } else {
            Text("—", fontSize = 10.sp, color = SportsMuted)
        }
    }
}

/** The football by the name of the side with the ball. */
@Composable
fun SportsPossessionMark() {
    Canvas(Modifier.size(16.dp, 10.dp)) {
        drawOval(Accent)
        val y = size.height / 2
        drawLine(Color.Black.copy(alpha = 0.55f), Offset(size.width * 0.3f, y), Offset(size.width * 0.7f, y), strokeWidth = 1.dp.toPx())
        for (step in 0..2) {
            val x = size.width * (0.4f + step * 0.1f)
            drawLine(Color.Black.copy(alpha = 0.55f), Offset(x, y - 2.dp.toPx()), Offset(x, y + 2.dp.toPx()), strokeWidth = 0.8.dp.toPx())
        }
    }
}

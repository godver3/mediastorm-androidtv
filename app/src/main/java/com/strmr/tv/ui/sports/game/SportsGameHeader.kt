package com.strmr.tv.ui.sports.game

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.text.TextAutoSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.tv.material3.Text
import com.strmr.tv.sports.SportsGame
import com.strmr.tv.sports.SportsLifecycle
import com.strmr.tv.sports.SportsTeam
import com.strmr.tv.ui.sports.SportsAmber
import com.strmr.tv.ui.sports.SportsFormat
import com.strmr.tv.ui.sports.SportsMuted
import com.strmr.tv.ui.sports.SportsStatusPill
import com.strmr.tv.ui.sports.SportsTeamLogo
import com.strmr.tv.ui.sports.game.combat.SportsCombatHeader
import com.strmr.tv.ui.sports.game.rugby.SportsRugby
import com.strmr.tv.ui.sports.game.tennis.SportsTennisHeaderSets
import com.strmr.tv.ui.sports.game.tennis.sportsTennisHeaderSetsShow
import kotlinx.coroutines.delay
import java.time.Duration
import java.time.Instant

/**
 * The top of a game's page (Expo `SportsEditorialScoreboard` on TV): the competition and state,
 * both sides with logos, rankings, names, records and scores around the game's status (with
 * football's and baseball's live situation, [SportsHeaderSituation], tennis's set scores, rugby's
 * halves; fights have their own, [SportsCombatHeader]), then the result, delayed-data and starting-soon notes, and when and where it is played. With scores
 * hidden it keeps the names, logos, time and state only.
 */
@Composable
fun SportsGameHeader(page: SportsGamePage, spoilers: Boolean) {
    val game = page.game
    Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(page.leagueName.uppercase(), fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = SportsMuted)
            SportsStatusPill(page.lifecycle)
        }
        if (page.kind == SportsKind.MMA || page.kind == SportsKind.BOXING) {
            SportsCombatHeader(page, spoilers)
        } else if (game.isMatchup) {
            if (page.kind.isRugby) Text(SportsRugby.code(page.kind), fontSize = 12.sp, color = SportsMuted)
            Scoreline(page, spoilers)
        } else {
            Text(game.displayTitle, fontSize = 26.sp, fontWeight = FontWeight.SemiBold, textAlign = TextAlign.Center)
            game.combat?.division?.ifEmpty { null }?.let { Text(it, fontSize = 15.sp, color = SportsMuted) }
            Text(
                if (spoilers) SportsFormat.statusLine(game, spoilers = true) else SportsGameFormat.detailStatus(page),
                fontSize = 17.sp, fontWeight = FontWeight.SemiBold, textAlign = TextAlign.Center,
            )
        }
        Notes(page, spoilers)
        // With scores hidden the page's first panel says when and where.
        val meta = meta(game)
        if (!spoilers && meta.isNotEmpty()) Text(meta, fontSize = 14.sp, color = SportsMuted, textAlign = TextAlign.Center)
    }
}

@Composable
private fun Scoreline(page: SportsGamePage, spoilers: Boolean) {
    val teams = page.teams
    val scores = !spoilers && page.game.showsScore
    // The state between the sides; delayed data has its own note under the scores.
    val center = if (spoilers && page.lifecycle != SportsLifecycle.SCHEDULED) {
        SportsFormat.statusLine(page.game, spoilers = true)
    } else if (page.kind.isRugby) {
        SportsRugby.status(page)
    } else {
        SportsGameFormat.detailStatus(page)
    }
    val situation = SportsHeaderSituation.of(page, spoilers)
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
        Side(page, teams[0], trailing = false, spoilers, Modifier.weight(1f), ball = situation.hasBall(teams[0]))
        if (scores) Score(page, teams[0], situation)
        Column(Modifier.width(140.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(center, fontSize = 17.sp, fontWeight = FontWeight.SemiBold, textAlign = TextAlign.Center)
            SportsHeaderSituationLines(situation)
            if (sportsTennisHeaderSetsShow(page, spoilers)) SportsTennisHeaderSets(page)
        }
        if (scores) Score(page, teams[1], situation)
        Side(page, teams[1], trailing = true, spoilers, Modifier.weight(1f), ball = situation.hasBall(teams[1]))
    }
}

/**
 * A side's score; the loser's final score dimmed. Long scores shrink to fit; cricket's overs and
 * target ("161/5 (18/20 ov, target 156)") go under the runs as a caption. Football's timeouts left
 * sit under it while live.
 */
@Composable
private fun Score(page: SportsGamePage, team: SportsTeam, situation: SportsHeaderSituation) {
    val final = page.lifecycle == SportsLifecycle.FINAL
    val color = if (final && team.winner != true) SportsMuted else Color.White
    Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(2.dp)) {
        val full = team.score ?: "–"
        val split = if (page.kind == SportsKind.CRICKET) full.indexOf(" (").takeIf { it >= 0 } else null
        ScoreText(split?.let { full.substring(0, it) } ?: full, color, if (split != null) 110.dp else 85.dp)
        split?.let {
            Text(
                full.substring(it + 2).removeSuffix(")"), fontSize = 12.sp, color = SportsMuted, textAlign = TextAlign.Center,
                modifier = Modifier.width(110.dp),
            )
        }
        situation.football?.let { SportsTimeouts(it.timeouts(team, page.game), team) }
    }
}

@Composable
private fun ScoreText(score: String, color: Color, width: Dp) {
    BasicText(
        score,
        style = TextStyle(color = color, fontSize = 44.sp, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center, fontFeatureSettings = "tnum"),
        maxLines = 1,
        autoSize = TextAutoSize.StepBased(minFontSize = 26.sp, maxFontSize = 44.sp),
        modifier = Modifier.width(width),
    )
}

/** A side: the logo outside, the name (with a football while it has the [ball]) and record towards the centre. */
@Composable
private fun Side(page: SportsGamePage, team: SportsTeam, trailing: Boolean, spoilers: Boolean, modifier: Modifier, ball: Boolean = false) {
    val name = listOfNotNull(
        if (spoilers) null else team.displayRank?.let { "#$it" },
        team.name,
        if (!spoilers && page.kind == SportsKind.TENNIS) team.displaySeed?.let { "[$it]" } else null,
    ).joinToString(" ")
    val record = if (spoilers) emptyList() else listOfNotNull(
        team.record, if (page.isFootballLeague) team.standingSummary?.let { "Current: $it" } else null,
    ).filter { it.isNotEmpty() }
    val align = if (trailing) TextAlign.End else TextAlign.Start
    Row(
        modifier, verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp, if (trailing) Alignment.End else Alignment.Start),
    ) {
        if (!trailing) SportsTeamLogo(team, 55.dp)
        Column(Modifier.weight(1f, fill = false), horizontalAlignment = if (trailing) Alignment.End else Alignment.Start) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(
                    name, fontSize = 20.sp, fontWeight = FontWeight.SemiBold, maxLines = if (page.kind == SportsKind.TENNIS) 3 else 2, overflow = TextOverflow.Ellipsis, textAlign = align,
                    modifier = Modifier.weight(1f, fill = false).semantics { if (ball) contentDescription = "$name, has possession" },
                )
                if (ball) SportsPossessionMark()
            }
            if (record.isNotEmpty()) Text(record.joinToString(" · "), fontSize = 14.sp, color = SportsMuted, textAlign = align)
        }
        if (trailing) SportsTeamLogo(team, 55.dp)
    }
}

@Composable
private fun Notes(page: SportsGamePage, spoilers: Boolean) {
    // A fight's header line already names the winner.
    val fight = page.kind == SportsKind.MMA || page.kind == SportsKind.BOXING
    if (!spoilers && !fight) SportsGameFormat.result(page)?.let { Text(it, fontSize = 15.sp, fontWeight = FontWeight.SemiBold) }
    if (!spoilers && page.isStale()) {
        Text("Updates delayed · Showing the last available data", fontSize = 12.sp, color = SportsAmber)
    }
    if (page.lifecycle == SportsLifecycle.SCHEDULED) Countdown(page.game)
}

/**
 * "Starts in 11:47 · Broadcast availability unconfirmed" in the 15 minutes before a scheduled
 * start, ticking each second, then "Starting soon". Until then it looks again every half minute,
 * so it appears on time without a refresh.
 */
@Composable
private fun Countdown(game: SportsGame) {
    val now by produceState(Instant.now(), game.start) {
        while (true) {
            value = Instant.now()
            delay(if (Duration.between(value, game.start).toMinutes() < 16) 1_000 else 30_000)
        }
    }
    SportsGameFormat.pregameCountdown(game, now)?.let { Text("$it · Broadcast availability unconfirmed", fontSize = 15.sp) }
}

/** When (the status says it before the start), where and on which channels. */
private fun meta(game: SportsGame): String = listOfNotNull(
    if (game.lifecycle == SportsLifecycle.SCHEDULED) null else SportsFormat.startTime(game),
    game.venue,
    game.broadcasts?.joinToString(", "),
).filter { it.isNotEmpty() }.joinToString(" · ")

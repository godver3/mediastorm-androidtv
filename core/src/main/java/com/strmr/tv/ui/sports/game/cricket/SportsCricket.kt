package com.strmr.tv.ui.sports.game.cricket

import com.strmr.tv.sports.SportsCricketInnings
import com.strmr.tv.sports.SportsCricketPlayer
import com.strmr.tv.sports.SportsCricketScorecard
import com.strmr.tv.sports.SportsLifecycle
import com.strmr.tv.sports.SportsTeam
import com.strmr.tv.ui.sports.game.SportsGameFormat
import com.strmr.tv.ui.sports.game.SportsGamePage

/**
 * Cricket's innings rules (Expo `SportsCricketOverview` and `SportsCricketScorecards`, tvOS
 * `SportsCricket`): which innings is in progress, how each is labelled, the chase, and a final's
 * top performers.
 */
object SportsCricket {
    /** One innings as the scorecard shows it. */
    data class Line(
        val innings: SportsCricketInnings,
        val team: SportsTeam?,
        val current: Boolean,
        val score: String,
        /** "1st innings", "2nd innings", or "Match innings 3" when earlier innings are missing. */
        val label: String,
        /** "Batting", "Current innings" during a break, "Last reported batting" when old; else the label. */
        val state: String,
        /** "Target 156 · 12 runs needed". */
        val chase: String,
        /** Runs toward the target (0…1) while chasing. */
        val progress: Double?,
    )

    /** The innings, the one in progress first (only while live and when the feed names exactly one). */
    fun lines(page: SportsGamePage): List<Line> {
        val innings = if (page.lifecycle == SportsLifecycle.SCHEDULED) emptyList() else page.detail.innings
        val batting = innings.filter { it.batting == true }
        val active = if (page.lifecycle == SportsLifecycle.LIVE && batting.size == 1) batting[0] else null
        val ordered = active?.let { a -> listOf(a) + innings.filter { it != a } } ?: innings
        val breakInPlay = SportsGameFormat.matches("""break|stumps|rain|delay|suspend|lunch|tea\b""", SportsGameFormat.detailStatus(page))
        return ordered.map { row ->
            val current = row == active
            val target = row.target?.takeIf { it > 0 }
            val runs = row.runs
            val needed = if (current && !breakInPlay && target != null && runs != null && runs in 0 until target && (row.wickets ?: 0) < 10) {
                target - runs
            } else null
            val complete = row.number in 1..4 && (1..row.number).all { n -> innings.any { it.number == n } }
            val ordinal = innings.count { it.teamId == row.teamId && it.number <= row.number }
            val label = if (complete && ordinal <= 2) (if (ordinal == 1) "1st innings" else "2nd innings") else "Match innings ${row.number}"
            val state = when {
                !current -> label
                page.isStale() -> "Last reported batting"
                breakInPlay -> "Current innings"
                else -> "Batting"
            }
            val chase = listOfNotNull(target?.let { "Target $it" }, needed?.let { if (it == 1) "1 run needed" else "$it runs needed" })
                .joinToString(" · ")
            val progress = if (current && target != null && runs != null && runs >= 0) minOf(1.0, runs.toDouble() / target) else null
            Line(
                innings = row, team = page.team(row.teamId), current = current,
                score = row.score ?: "${row.runs?.toString() ?: "–"}/${row.wickets?.toString() ?: "–"}",
                label = label, state = state, chase = chase, progress = progress,
            )
        }
    }

    /** A final's best batter (most runs) or bowler (most wickets) of a card; every one tied at the top. */
    data class Highlight(val player: SportsCricketPlayer, val value: String, val metric: String)

    /** The last innings reported and, for a final, its top batters and bowlers (none when the best is zero). */
    fun highlights(page: SportsGamePage): Pair<Int, List<Highlight>> {
        val cards = page.detail.cricketScorecards
        val latest = cards.maxOfOrNull { it.innings } ?: 0
        if (page.lifecycle != SportsLifecycle.FINAL) return latest to emptyList()
        val players = cards.filter { it.innings == latest }.flatMap { card ->
            val batting = card.kind == SportsCricketScorecard.Kind.BATTING
            val candidates = card.players.mapNotNull { player ->
                val value = player.value(if (batting) "Runs" else "Wickets")?.trim() ?: return@mapNotNull null
                value.toDoubleOrNull()?.let { Triple(player, value, it) }
            }
            val best = candidates.maxOfOrNull { it.third }?.takeIf { it > 0 } ?: return@flatMap emptyList()
            candidates.filter { it.third == best }.map { Highlight(it.first, it.second, if (batting) "runs" else "wickets") }
        }
        return latest to players
    }
}

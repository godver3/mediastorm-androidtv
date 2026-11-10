package com.strmr.tv.ui.sports.game.baseball

import com.strmr.tv.sports.SportsBaseballSituation
import com.strmr.tv.sports.SportsGame
import com.strmr.tv.sports.SportsLifecycle
import com.strmr.tv.sports.SportsTeam
import com.strmr.tv.sports.liveBaseballSituation
import com.strmr.tv.sports.periodLabel
import com.strmr.tv.ui.sports.game.SportsGameFormat
import com.strmr.tv.ui.sports.game.SportsGamePage

/**
 * Whether an MLB at-bat is under way and how it stands (Expo `baseballState`, tvOS
 * `SportsBaseballState`): only a reported, active half-inning (the top or bottom of one; not a
 * break, a delay, a pitching change or the third out) has a batting and a pitching side, a count
 * and outs. Otherwise [status] says why. Works from a scoreboard game too (its `liveSituation`).
 */
data class SportsBaseballState(
    val situation: SportsBaseballSituation?,
    val active: Boolean,
    /** The inning number of the half-inning label. */
    val inning: Int?,
    val batting: SportsTeam?,
    val pitching: SportsTeam?,
    /** "Count 3–2" and "2 outs", while active and plainly reported. */
    val count: String?,
    val outs: String?,
    /** The break or delay reported, "Half-inning complete" or "No active at-bat reported". */
    val status: String,
) {
    /**
     * The scoreboard's line under the inning (Expo `BaseballSituationLine`): "2 outs · Count 3–2",
     * "Last reported · …" when the data is old; null when no at-bat is under way.
     */
    fun scoreboardLine(stale: Boolean): String? {
        if (!active) return null
        val shown = listOfNotNull(outs, count).ifEmpty { return null }
        return ((if (stale) listOf("Last reported") else emptyList()) + shown).joinToString(" · ")
    }

    companion object {
        /** Breaks in play: between or after half-innings, delays, suspensions, pitching changes. */
        private const val STOPS = """middle|mid\b|end\b|delay|suspend|postpon|break|pitching change|pitcher change"""
        private val half = Regex("""^(top|bottom|bot)\b""", RegexOption.IGNORE_CASE)
        private val digits = Regex("""\d+""")

        fun of(game: SportsGame): SportsBaseballState {
            val situation = game.liveBaseballSituation
            val phase = game.periodLabel?.ifEmpty { null } ?: situation?.inning ?: ""
            val stopped = SportsGameFormat.matches("$STOPS|final", "$phase ${game.statusDetail.orEmpty()} ${situation?.inning.orEmpty()}")
            val side = half.find(phase)?.groupValues?.get(1)?.lowercase()
            val active = game.lifecycle == SportsLifecycle.LIVE && !stopped && side != null && situation != null && situation.outs != 3
            fun valid(value: Int?, top: Int) = value?.takeIf { it in 0..top }
            val balls = valid(situation?.balls, 3)
            val strikes = valid(situation?.strikes, 2)
            val transition = listOfNotNull(game.statusDetail, phase, situation?.inning)
                .firstOrNull { it.isNotEmpty() && SportsGameFormat.matches(STOPS, it) }
            return SportsBaseballState(
                situation = situation,
                active = active,
                inning = digits.find(phase)?.value?.toIntOrNull()?.takeIf { it != 0 },
                batting = if (active) (if (side == "top") game.awayTeam else game.homeTeam) else null,
                pitching = if (active) (if (side == "top") game.homeTeam else game.awayTeam) else null,
                count = if (active && balls != null && strikes != null) "Count $balls–$strikes" else null,
                outs = if (active) valid(situation?.outs, 2)?.let(::outs) else null,
                status = transition ?: if (situation?.outs == 3) "Half-inning complete" else "No active at-bat reported",
            )
        }

        fun outs(count: Int) = if (count == 1) "1 out" else "$count outs"
    }
}

/**
 * MLB's line score (Expo `SportsPeriodBreakdown` for MLB): at least nine innings (more for extra
 * innings or the one being played), then runs, hits and errors for each side.
 */
data class SportsBaseballLineScore(val innings: List<Inning>, val totals: List<Total>, val current: Int?) {
    data class Inning(val number: Int, val away: String, val home: String)

    data class Total(val label: String, val away: String, val home: String)

    companion object {
        fun of(page: SportsGamePage): SportsBaseballLineScore {
            val state = SportsBaseballState.of(page.game)
            val periods = page.detail.periods
            val count = maxOf(9, state.inning ?: 0, periods.mapNotNull { it.label.toIntOrNull() }.maxOrNull() ?: 0)
            fun shown(value: String?) = value?.ifEmpty { null } ?: "–"
            val innings = (1..count).map { number ->
                val period = periods.firstOrNull { it.label.toIntOrNull() == number }
                Inning(number, shown(period?.away), shown(period?.home))
            }
            fun stat(label: String) = page.detail.comparisons.firstOrNull { it.label == label }
            val totals = listOf(
                Total("R", shown(page.game.awayTeam.score), shown(page.game.homeTeam.score)),
                Total("H", shown(stat("Hits")?.away), shown(stat("Hits")?.home)),
                Total("E", shown(stat("Errors")?.away), shown(stat("Errors")?.home)),
            )
            return SportsBaseballLineScore(innings, totals, if (state.active) state.inning else null)
        }
    }
}

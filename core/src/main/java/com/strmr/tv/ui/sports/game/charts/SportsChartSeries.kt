package com.strmr.tv.ui.sports.game.charts

import com.strmr.tv.sports.SportsLifecycle
import com.strmr.tv.sports.SportsTeam
import com.strmr.tv.ui.sports.game.SportsGamePage
import com.strmr.tv.ui.sports.game.SportsTeamColors
import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.max
import kotlin.math.min

/**
 * A basketball game's clock as elapsed seconds (Expo `basketballElapsed` and the regulation of
 * `basketballLeadGeometry`, tvOS `SportsGameClock`): four 12-minute NBA quarters, two 20-minute
 * men's college halves, four 10-minute quarters for the WNBA and women's college, then 5-minute
 * overtimes.
 */
data class SportsGameClock(val periods: Int, val seconds: Double) {
    fun length(period: Int): Double = if (period > periods) 300.0 else seconds

    /** When a period starts. */
    fun start(period: Int): Double = min(period - 1, periods) * seconds + max(0, period - 1 - periods) * 300.0

    /** Seconds played at a period's clock ("7:21", "16.9", "0.0"); null for a malformed clock. */
    fun elapsed(period: Int, clock: String): Double? {
        if (period < 1) return null
        val match = ClockPattern.matchEntire(clock) ?: return null
        val seconds = match.groupValues[2].toDoubleOrNull()?.takeIf { it < 60 } ?: return null
        val remaining = (match.groupValues[1].toIntOrNull() ?: 0) * 60 + seconds
        if (remaining > length(period)) return null
        return start(period) + length(period) - remaining
    }

    /** "Q1", "H2", "OT1". */
    fun label(period: Int): String = if (period > periods) "OT${period - periods}" else "${if (periods == 2) "H" else "Q"}$period"

    /** The period lines of a chart that runs [duration] seconds. */
    fun marks(duration: Double, lastPeriod: Int): List<SportsChartPeriod> =
        (1..max(1, lastPeriod)).map { SportsChartPeriod(start(it), label(it)) }.filter { it.start <= duration }

    companion object {
        private val ClockPattern = Regex("""(?:(\d+):)?(\d{1,2}(?:\.\d+)?)""")

        /** The win-probability chart's clock: any league, NBA and men's college by their own lengths. */
        fun of(league: String): SportsGameClock {
            val periods = if (league == "mens-college-basketball") 2 else 4
            return SportsGameClock(periods, if (league == "nba") 720.0 else if (periods == 2) 1200.0 else 600.0)
        }

        /** The lead chart's clock: the four basketball leagues only. */
        fun basketball(league: String): SportsGameClock? =
            if (league in setOf("nba", "wnba", "mens-college-basketball", "womens-college-basketball")) of(league) else null
    }
}

/** Where a period starts on a chart, and its short name. */
data class SportsChartPeriod(val start: Double, val label: String)

/**
 * ESPN's win probability over the game (Expo `SportsWinProbability`): at least two points, each at
 * a readable clock, a probability within 0…1 and in game order, else nothing is drawn ([of] null).
 */
data class SportsWinProbabilitySeries(val points: List<Point>, val duration: Double, val periods: List<SportsChartPeriod>) {
    data class Point(val elapsed: Double, val home: Double, val period: Int, val periodLabel: String, val clock: String)

    val latest: Point get() = points.last()

    companion object {
        fun of(page: SportsGamePage): SportsWinProbabilitySeries? {
            val clock = SportsGameClock.of(page.game.league)
            val points = mutableListOf<Point>()
            for (sample in page.detail.winProbability) {
                val elapsed = clock.elapsed(sample.period, sample.clock) ?: return null
                if (!sample.home.isFinite() || sample.home !in 0.0..1.0 || elapsed < (points.lastOrNull()?.elapsed ?: 0.0)) return null
                points += Point(elapsed, sample.home, sample.period, sample.periodLabel, sample.clock)
            }
            if (points.size < 2) return null
            val duration = max(1.0, points.last().elapsed)
            return SportsWinProbabilitySeries(points, duration, clock.marks(duration, points.last().period))
        }
    }
}

/**
 * The lead over the game (Expo `basketballLeadGeometry`): only a complete history of this game
 * and its sides, every point at a readable clock and in order, ending on the scoreboard's score;
 * otherwise nothing is drawn rather than a made-up curve. The lead is away minus home (away above
 * zero), kept where it changes.
 */
data class SportsLeadSeries(val points: List<Point>, val duration: Double, val extent: Double, val periods: List<SportsChartPeriod>) {
    data class Point(val elapsed: Double, val lead: Double)

    val latest: Double get() = points.last().lead

    /**
     * The curve with a point added wherever it crosses zero, so the part above zero and the part
     * below each fill exactly to the axis in their side's colour.
     */
    val crossings: List<Point>
        get() {
            val out = mutableListOf<Point>()
            for (point in points) {
                val previous = out.lastOrNull()
                if (previous != null && previous.lead * point.lead < 0) {
                    val at = previous.elapsed + (point.elapsed - previous.elapsed) * previous.lead / (previous.lead - point.lead)
                    out += Point(at, 0.0)
                }
                out += point
            }
            return out
        }

    /** A stretch of the line on one side of zero: [away] true above, false below, null tied. */
    data class Run(val away: Boolean?, val points: List<Point>)

    /** The line in stretches on one side of zero, each drawn in that side's colour. */
    val runs: List<Run>
        get() {
            val curve = crossings
            val runs = mutableListOf<Run>()
            for ((a, b) in curve.zipWithNext()) {
                val sum = a.lead + b.lead
                val side = if (sum > 0) true else if (sum < 0) false else null
                val last = runs.lastOrNull()
                if (last != null && last.away == side) runs[runs.size - 1] = last.copy(points = last.points + b)
                else runs += Run(side, listOf(a, b))
            }
            return runs
        }

    companion object {
        fun of(page: SportsGamePage): SportsLeadSeries? {
            val game = page.game
            val history = page.detail.scoreHistory ?: return null
            if (page.lifecycle == SportsLifecycle.SCHEDULED || !history.complete || history.eventId != game.id ||
                history.awayTeamId != game.awayTeam.id || history.homeTeamId != game.homeTeam.id || history.points.size < 2
            ) return null
            val clock = SportsGameClock.basketball(game.league) ?: return null
            val all = mutableListOf<Point>()
            for (point in history.points) {
                // ESPN gives seconds-only clocks inside the final minute ("23.2").
                if (point.away < 0 || point.home < 0 || !point.away.isFinite() || !point.home.isFinite()) return null
                val elapsed = clock.elapsed(point.period, point.clock) ?: return null
                if (elapsed < (all.lastOrNull()?.elapsed ?: 0.0)) return null
                all += Point(elapsed, point.away - point.home)
            }
            val last = history.points.last()
            if (game.awayTeam.score?.toDoubleOrNull() != last.away || game.homeTeam.score?.toDoubleOrNull() != last.home) return null
            val kept = all.filterIndexed { index, point -> index == 0 || index == all.lastIndex || point.lead != all[index - 1].lead }
            val duration = max(1.0, all.last().elapsed)
            val extent = ceil(max(5.0, all.maxOf { abs(it.lead) }) / 10) * 10
            return SportsLeadSeries(kept, duration, extent, clock.marks(duration, last.period))
        }
    }
}

/**
 * Chart colours (Expo `basketballChartColors`): each side's colour, else its alternate, that reads
 * on the dark chart (contrast 3 against #101417), else light grey; then kept apart.
 */
fun SportsTeamColors.chartPair(away: SportsTeam, home: SportsTeam): SportsTeamColors.HexPair {
    fun visible(vararg candidates: String?): String {
        for (candidate in candidates) {
            val hex = candidate?.replace("#", "")?.lowercase()?.takeIf { it.length == 6 && it.toLongOrNull(16) != null } ?: continue
            if (contrast("#$hex", "#101417") >= 3) return "#$hex"
        }
        return "#d0d8df"
    }
    return hexPair(visible(away.color, away.alternateColor), visible(home.color, home.alternateColor),
                   visible(away.alternateColor), visible(home.alternateColor))
}

package com.strmr.tv.ui.sports.game.tennis

import androidx.compose.ui.graphics.Color
import com.strmr.tv.sports.SportsGame
import com.strmr.tv.sports.SportsPeriodScore
import com.strmr.tv.sports.SportsTeam

/**
 * Tennis wording and set-score rules (Expo `sports-tennis.ts` and the set rows of
 * `SportsTennisSetScores` / `SportsTennisCard`, tvOS `SportsTennis`).
 */
object SportsTennis {
    /** The tennis-ball accent Expo marks the set in play with (`sportsIconColor('tennis')`). */
    val accent = Color(0xFFC7EA46)

    /**
     * The tournament, draw and round ("US Open · Men's Singles · Qualifying 1st Round"); older
     * feeds only prefix the title with the tournament ("US Open: A vs B").
     */
    fun eventContext(game: SportsGame): String? {
        if (game.league != "atp" && game.league != "wta") return null
        game.eventContext?.trim()?.ifEmpty { null }?.let { return it }
        val suffix = ": ${game.awayTeam.name} vs ${game.homeTeam.name}"
        val title = game.title ?: return null
        return if (title.endsWith(suffix)) title.dropLast(suffix.length) else null
    }

    /** Tiebreak points as superscript digits, a reported zero included ("⁷", "¹⁰"); nothing when not reported. */
    fun tiebreak(points: Int?): String {
        if (points == null || points < 0) return ""
        return points.toString().map { Superscripts[it - '0'] }.joinToString("")
    }

    private const val Superscripts = "⁰¹²³⁴⁵⁶⁷⁸⁹"

    /** The last set with games reported (the one in play while live). */
    fun latestSet(periods: List<SportsPeriodScore>): Int? =
        periods.indexOfLast { it.away.isNotEmpty() || it.home.isNotEmpty() }.takeIf { it >= 0 }

    /** A side's games in a set with its tiebreak points ("7⁵"), "–" when not reported. */
    fun games(period: SportsPeriodScore, away: Boolean): String {
        val value = if (away) period.away else period.home
        return value.ifEmpty { "–" } + tiebreak(if (away) period.awayTiebreak else period.homeTiebreak)
    }

    /** Whether a side won the set (bold). */
    fun won(period: SportsPeriodScore, away: Boolean): Boolean = (if (away) period.awayWinner else period.homeWinner) == true

    /** A player or pair ("A / B" on two lines) with the seed ("Jannik Sinner [1]"). */
    fun name(player: SportsTeam, seed: Boolean = true): String {
        val name = player.name.ifEmpty { player.shortName }
        val lines = name.split("/").joinToString("\n") { it.trim() }
        val value = player.displaySeed
        return if (seed && value != null) "$lines [$value]" else lines
    }
}

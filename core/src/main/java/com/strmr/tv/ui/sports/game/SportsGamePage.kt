package com.strmr.tv.ui.sports.game

import com.strmr.tv.sports.SportsComparison
import com.strmr.tv.sports.SportsGame
import com.strmr.tv.sports.SportsGameDetail
import com.strmr.tv.sports.SportsLeague
import com.strmr.tv.sports.SportsLifecycle
import com.strmr.tv.sports.SportsTeam
import com.strmr.tv.sports.parseInstant
import java.time.Duration
import java.time.Instant

/**
 * A game's sport family, which picks its page's tabs, labels and Overview: the league catalog's
 * `applicationSport` (Expo `SPORTS_COMPETITIONS[league].sport`), else the game's own `sport`,
 * else what the league id says.
 */
enum class SportsKind(val id: String) {
    FOOTBALL("american-football"), BASKETBALL("basketball"), BASEBALL("baseball"), SOFTBALL("softball"),
    HOCKEY("ice-hockey"), SOCCER("soccer"), TENNIS("tennis"), GOLF("golf"), CRICKET("cricket"), MMA("mma"),
    BOXING("boxing"), RUGBY_UNION("rugby-union"), RUGBY_LEAGUE("rugby-league"), VOLLEYBALL("volleyball"), OTHER("other");

    val isRugby: Boolean get() = this == RUGBY_UNION || this == RUGBY_LEAGUE

    /**
     * Sides listed away first (Expo `sportsTeamSides`): the feed's first-named player or fighter
     * is its "away" side. Team sports show the home side first.
     */
    val awayFirst: Boolean get() = this == TENNIS || this == MMA || this == BOXING || this == GOLF

    /** Soccer and tennis play matches; the rest, games (the page's notices). */
    val playsMatches: Boolean get() = this == SOCCER || this == TENNIS

    companion object {
        fun of(league: SportsLeague?, game: SportsGame): SportsKind =
            league?.applicationSport?.let(::byId)
                ?: game.sport?.let { feedSports[it] ?: byId(it) }
                ?: ofLeague(game.league)

        private fun byId(id: String): SportsKind? = entries.firstOrNull { it.id == id }

        /** The scoreboard's own `sport` names that differ from the catalog's. */
        private val feedSports = mapOf("football" to FOOTBALL, "hockey" to HOCKEY, "rugby" to RUGBY_UNION)

        fun ofLeague(id: String): SportsKind = when (id) {
            "nfl", "college-football" -> FOOTBALL
            "nba", "wnba", "mens-college-basketball", "womens-college-basketball" -> BASKETBALL
            "mlb" -> BASEBALL
            "nhl" -> HOCKEY
            "atp", "wta" -> TENNIS
            "pga", "lpga" -> GOLF
            "ufc" -> MMA
            "boxing" -> BOXING
            else -> when {
                id.startsWith("soccer-") -> SOCCER
                id.startsWith("rugby-league") -> RUGBY_LEAGUE
                id.startsWith("rugby-") -> RUGBY_UNION
                id.startsWith("cricket") -> CRICKET
                else -> OTHER
            }
        }
    }
}

/**
 * Everything a game page's panels read: the game, its detail (empty when the server sent none),
 * its league and sport family. Equal while the game and league are, so the panels redraw on
 * each refresh that brings new data.
 */
data class SportsGamePage(val game: SportsGame, val league: SportsLeague?) {
    val kind: SportsKind = SportsKind.of(league, game)
    val detail: SportsGameDetail = game.detail ?: SportsGameDetail()

    val lifecycle: SportsLifecycle get() = game.lifecycle
    val leagueName: String get() = league?.label ?: game.league.uppercase()
    val coverageNote: String? get() = detail.coverageNote ?: league?.coverageNote?.ifEmpty { null }
    val isCollege: Boolean get() = league?.college ?: (game.league.startsWith("college-") || "-college-" in game.league)

    /** NFL and college football, the leagues with drives (Expo `football`). */
    val isFootballLeague: Boolean get() = game.league == "nfl" || game.league == "college-football"

    /** A golf tournament of individual players (Expo `isIndividualGolf`). */
    val isIndividualGolf: Boolean
        get() {
            if (kind != SportsKind.GOLF) return false
            if (league?.adapter == "team-golf" || league?.eventKind.orEmpty() in setOf("matchup", "game", "match")) return false
            return league?.eventKind != null || league?.supportsTeams != true
        }

    /** The two sides in display order (Expo `sportsOrderedTeams`). */
    val teams: List<SportsTeam> get() = if (kind.awayFirst) listOf(game.awayTeam, game.homeTeam) else listOf(game.homeTeam, game.awayTeam)

    fun team(id: String?): SportsTeam? = id?.let { listOf(game.awayTeam, game.homeTeam).firstOrNull { team -> team.id == it } }

    fun teamByAbbreviation(abbreviation: String): SportsTeam? =
        listOf(game.awayTeam, game.homeTeam).firstOrNull { it.abbreviation == abbreviation }

    /** The comparison value of one side. */
    fun value(row: SportsComparison, team: SportsTeam): String = if (team.id == game.homeTeam.id) row.home else row.away

    /**
     * Old or flagged data (Expo's detail freshness): the server says stale, or the detail is
     * older than 2 minutes while live, 10 minutes otherwise.
     */
    fun isStale(now: Instant = Instant.now()): Boolean {
        val sent = game.detail ?: return false
        if (sent.stale) return true
        val updated = sent.updatedAt?.let(::parseInstant) ?: return true
        return Duration.between(updated, now).toMillis() > if (lifecycle == SportsLifecycle.LIVE) 120_000 else 600_000
    }

    /** Whether the feed sent any of the generic data (Expo `SportsMatchAvailability`'s `missing`). */
    val hasGameData: Boolean
        get() = detail.leaders.isNotEmpty() || detail.plays.isNotEmpty() || detail.comparisons.isNotEmpty() ||
            detail.periods.isNotEmpty() || detail.footballDrives.isNotEmpty() || detail.shots.isNotEmpty() ||
            (lifecycle != SportsLifecycle.SCHEDULED && detail.scoreHistory != null)
}

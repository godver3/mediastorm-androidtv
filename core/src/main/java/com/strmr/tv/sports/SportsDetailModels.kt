package com.strmr.tv.sports

/**
 * A game page's data: `detail` of `GET /api/sports/game/{id}` (Go `models.SportsGameDetail`,
 * Expo `SportsEventDetail`), read leniently by [readGameDetail] (see [SportsFields]): a missing,
 * `null` or malformed field is empty and a bad list item is skipped, so one odd value never
 * costs the page. `SportsGameDetail()` is the empty detail. The sport-specific extras (shot
 * charts, win probability, golf leaderboard, cricket innings, fighter profiles) are added
 * beside the views that draw them: a field here and one line in [readGameDetail].
 */
data class SportsGameDetail(
    val source: String? = null,
    /** When the server built this detail (RFC 3339), for the page's freshness. */
    val updatedAt: String? = null,
    val stale: Boolean = false,
    val coverageNote: String? = null,
    /** The soccer feed's competition and round ("2025-26 English Premier League"). */
    val tournamentContext: String? = null,
    val capabilities: SportsCapabilities = SportsCapabilities(),
    val periods: List<SportsPeriodScore> = emptyList(),
    /** Newest first, as the feed sends them. */
    val plays: List<SportsPlay> = emptyList(),
    val comparisons: List<SportsComparison> = emptyList(),
    val leaders: List<SportsLeader> = emptyList(),
    val playerStats: List<SportsPlayerStats> = emptyList(),
    val pregame: SportsPregame? = null,
    val standings: List<SportsStandingGroup> = emptyList(),
    val lineups: List<SportsLineup> = emptyList(),
    val footballDrives: List<SportsFootballDrive> = emptyList(),
    /** MLB's at-bat with the players' ids and the pitch count (`detail.sport`). */
    val sport: SportsBaseballSituation? = null,
    /** Basketball's field-goal attempts, oldest first (the shot chart). */
    val basketballShots: List<SportsBasketballShot> = emptyList(),
    val winProbability: List<SportsWinProbabilityPoint> = emptyList(),
    val scoreHistory: SportsScoreHistory? = null,
    /** The NHL's shot attempts (the rink map). */
    val shots: List<SportsHockeyShot> = emptyList(),
    /** `detail.sport` of kind `nba` / `nhl`; the backend doesn't source them yet. */
    val basketballSituation: SportsBasketballSituation? = null,
    val hockeySituation: SportsHockeySituation? = null,
    /** Golf's leaderboard in the provider's order. */
    val leaderboard: List<SportsGolfEntry> = emptyList(),
    val innings: List<SportsCricketInnings> = emptyList(),
    val cricketScorecards: List<SportsCricketScorecard> = emptyList(),
    /** Both fighters' height, reach, weight and stance (by fighter id). */
    val fighterProfiles: List<SportsPlayerStats> = emptyList(),
)

internal fun readGameDetail(f: SportsFields) = SportsGameDetail(
    source = f.string("source"),
    updatedAt = f.string("updatedAt"),
    stale = f.bool("stale") ?: false,
    coverageNote = f.filled("coverageNote"),
    tournamentContext = f.filled("tournamentContext"),
    capabilities = f.item("capabilities", ::readCapabilities) ?: SportsCapabilities(),
    periods = f.list("periods", ::readPeriodScore),
    plays = f.list("plays", ::readPlay),
    comparisons = f.list("comparisons", ::readComparison),
    leaders = f.list("leaders", ::readLeader),
    playerStats = f.list("playerStats", ::readPlayerStats),
    pregame = f.item("pregame", ::readPregame),
    standings = f.list("standings", ::readStandingGroup),
    lineups = f.list("lineups", ::readLineup),
    footballDrives = f.list("footballDrives", ::readFootballDrive),
    sport = f.item("sport", ::readBaseballSituation),
    basketballShots = f.list("basketballShots", ::readBasketballShot),
    winProbability = f.list("winProbability", ::readWinProbabilityPoint),
    scoreHistory = f.item("scoreHistory", ::readScoreHistory),
    shots = f.list("shots", ::readHockeyShot),
    basketballSituation = f.item("sport", ::readBasketballSituation),
    hockeySituation = f.item("sport", ::readHockeySituation),
    leaderboard = f.list("leaderboard", ::readGolfEntry),
    innings = f.list("innings", ::readCricketInnings),
    cricketScorecards = f.list("cricketScorecards", ::readCricketScorecard),
    fighterProfiles = f.list("fighterProfiles", ::readPlayerStats),
)

/** Which tabs the feed can fill for this game (Expo `detail.capabilities`). */
data class SportsCapabilities(val plays: Boolean = false, val stats: Boolean = false, val winProbability: Boolean = false)

private fun readCapabilities(f: SportsFields) = SportsCapabilities(
    plays = f.bool("plays") ?: false,
    stats = f.bool("stats") ?: false,
    winProbability = f.bool("winProbability") ?: false,
)

/** One period's (quarter, half, set, inning) points for each side. */
data class SportsPeriodScore(
    val label: String,
    val away: String = "",
    val home: String = "",
    /** Tennis tiebreak points and set winners. */
    val awayTiebreak: Int? = null,
    val homeTiebreak: Int? = null,
    val awayWinner: Boolean? = null,
    val homeWinner: Boolean? = null,
)

private fun readPeriodScore(f: SportsFields) = SportsPeriodScore(
    label = f.require("label"),
    away = f.text("away").orEmpty(),
    home = f.text("home").orEmpty(),
    awayTiebreak = f.int("awayTiebreak"),
    homeTiebreak = f.int("homeTiebreak"),
    awayWinner = f.bool("awayWinner"),
    homeWinner = f.bool("homeWinner"),
)

/** A team statistic for both sides ("Total yards", "Possession"). */
data class SportsComparison(val label: String, val away: String = "", val home: String = "")

internal fun readComparison(f: SportsFields) = SportsComparison(
    label = f.require("label"),
    away = f.text("away").orEmpty(),
    home = f.text("home").orEmpty(),
)

/** A game leader ("Passing Yards", "Caleb Williams", "CHI", "22/33, 308 YDS, 2 TD"). */
data class SportsLeader(
    val label: String,
    val name: String,
    val teamAbbreviation: String,
    val value: String,
    val athleteId: String? = null,
    val headshotUrl: String? = null,
)

private fun readLeader(f: SportsFields) = SportsLeader(
    label = f.require("label"),
    name = f.require("name"),
    teamAbbreviation = f.require("teamAbbreviation"),
    value = f.requireText("value"),
    athleteId = f.text("athleteId"),
    headshotUrl = f.filled("headshotUrl"),
)

/** A play or match event. [teamId] is the side it belongs to when the feed says. */
data class SportsPlay(
    val id: String,
    val periodLabel: String = "",
    val clock: String? = null,
    val title: String,
    val description: String? = null,
    val scoring: Boolean = false,
    val teamId: String? = null,
    val participants: List<SportsPlayParticipant> = emptyList(),
    /** A penalty-shootout kick (soccer, hockey). */
    val shootout: Boolean = false,
)

private fun readPlay(f: SportsFields) = SportsPlay(
    id = f.require("id"),
    periodLabel = f.string("periodLabel").orEmpty(),
    clock = f.filled("clock"),
    title = f.require("title"),
    description = f.filled("description"),
    scoring = f.bool("scoring") ?: false,
    teamId = f.filled("teamId"),
    participants = f.list("participants") { SportsPlayParticipant(it.require("id"), it.require("name")) },
    shootout = f.bool("shootout") ?: false,
)

data class SportsPlayParticipant(val id: String, val name: String)

/** One player's line in one category of the box score ("Passing": C/ATT 24/31, YDS 321…). */
data class SportsPlayerStats(
    val id: String,
    val teamId: String,
    val name: String,
    val category: String = "",
    val jersey: String? = null,
    val headshotUrl: String? = null,
    val stats: List<SportsStat> = emptyList(),
) {
    fun value(label: String): String? = stats.firstOrNull { it.label == label }?.value
}

internal fun readPlayerStats(f: SportsFields) = SportsPlayerStats(
    id = f.require("id"),
    teamId = f.require("teamId"),
    name = f.require("name"),
    category = f.string("category").orEmpty(),
    jersey = f.filled("jersey"),
    headshotUrl = f.filled("headshotUrl"),
    stats = f.list("stats", ::readStat),
)

/** A labelled value: a box-score column, a standings or pregame figure. */
data class SportsStat(val label: String, val value: String)

internal fun readStat(f: SportsFields) = SportsStat(f.require("label"), f.text("value").orEmpty())

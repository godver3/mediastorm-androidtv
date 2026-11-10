package com.strmr.tv.sports

// The context a game's detail carries beside its stats: the pre-game preview, the league table
// snapshot, match lineups and American football drives (Go `models/sports_pregame.go` and
// `models/sports_detail.go`, Expo `sports-model.ts`). Read leniently, as the detail is.

/**
 * Season context for a game (`detail.pregame`, Expo `SportsPregame`): each side's record,
 * standing, recent results, season leaders and probable starters, season comparisons, and the
 * previous meeting. Shown before the start; with scores hidden only what was known before the
 * start shows (`spoilerSafe`).
 */
data class SportsPregame(
    val seasonLabel: String? = null,
    val updatedAt: String? = null,
    val teams: List<SportsPregameTeam> = emptyList(),
    val comparisons: List<SportsComparison> = emptyList(),
    val previousMeeting: SportsPreviousMeeting? = null,
) {
    fun team(id: String): SportsPregameTeam? = teams.firstOrNull { it.teamId == id }
}

internal fun readPregame(f: SportsFields) = SportsPregame(
    seasonLabel = f.filled("seasonLabel"),
    updatedAt = f.string("updatedAt"),
    teams = f.list("teams", ::readPregameTeam),
    comparisons = f.list("comparisons", ::readComparison),
    previousMeeting = f.item("previousMeeting", ::readPreviousMeeting),
)

/** One side's season context. */
data class SportsPregameTeam(
    val teamId: String,
    val record: String? = null,
    val homeRecord: String? = null,
    val awayRecord: String? = null,
    val conferenceRecord: String? = null,
    val rank: Int? = null,
    /** The table it stands in ("2026-27 Central Standings"). */
    val standingLabel: String? = null,
    val standingStats: List<SportsStat> = emptyList(),
    /** Latest completed games first. */
    val recent: List<SportsRecentResult> = emptyList(),
    val leaders: List<SportsPregamePlayer> = emptyList(),
    val probables: List<SportsPregamePlayer> = emptyList(),
)

private fun readPregameTeam(f: SportsFields) = SportsPregameTeam(
    teamId = f.require("teamId"),
    record = f.string("record"),
    homeRecord = f.string("homeRecord"),
    awayRecord = f.string("awayRecord"),
    conferenceRecord = f.string("conferenceRecord"),
    rank = f.int("rank"),
    standingLabel = f.string("standingLabel"),
    standingStats = f.list("standingStats", ::readStat),
    recent = f.list("recent") {
        SportsRecentResult(it.require("id"), it.require("date"), it.require("opponent"), it.require("result"), it.string("score"))
    },
    leaders = f.list("leaders", ::readPregamePlayer),
    probables = f.list("probables", ::readPregamePlayer),
)

/** A completed game in a side's recent form ("W", "Toronto Raptors", "135-92"); [date] is RFC 3339. */
data class SportsRecentResult(val id: String, val date: String, val opponent: String, val result: String, val score: String? = null)

/** A season leader ("Points", "Norman Powell", "15.0") or probable starter. */
data class SportsPregamePlayer(val name: String, val label: String, val value: String? = null)

private fun readPregamePlayer(f: SportsFields) = SportsPregamePlayer(f.require("name"), f.require("label"), f.text("value"))

data class SportsPreviousMeeting(
    val date: String,
    val homeTeamId: String,
    val awayTeamId: String,
    val homeScore: String = "",
    val awayScore: String = "",
)

internal fun readPreviousMeeting(f: SportsFields) = SportsPreviousMeeting(
    date = f.require("date"),
    homeTeamId = f.require("homeTeamId"),
    awayTeamId = f.require("awayTeamId"),
    homeScore = f.text("homeScore").orEmpty(),
    awayScore = f.text("awayScore").orEmpty(),
)

/**
 * A league table snapshot sent with the game (`detail.standings`, Expo `SportsStandingGroup`):
 * soccer tables carry played / goal difference / points, others records.
 */
data class SportsStandingGroup(val title: String = "", val rows: List<SportsStandingRow> = emptyList())

internal fun readStandingGroup(f: SportsFields) = SportsStandingGroup(
    title = f.string("title").orEmpty(),
    rows = f.list("rows") {
        SportsStandingRow(
            teamId = it.text("teamId").orEmpty(),
            name = it.require("name"),
            rank = it.text("rank"),
            played = it.text("played"),
            points = it.text("points"),
            goalDifference = it.text("goalDifference"),
            record = it.string("record"),
            conferenceRecord = it.string("conferenceRecord"),
        )
    },
)

data class SportsStandingRow(
    val teamId: String,
    val name: String,
    val rank: String? = null,
    val played: String? = null,
    val points: String? = null,
    val goalDifference: String? = null,
    val record: String? = null,
    val conferenceRecord: String? = null,
)

/**
 * A side's match squad (`detail.lineups`, Expo `SportsLineup`): starters, substitutes and who
 * came on or went off.
 */
data class SportsLineup(val teamId: String, val formation: String? = null, val players: List<SportsLineupPlayer> = emptyList())

internal fun readLineup(f: SportsFields) = SportsLineup(
    teamId = f.require("teamId"),
    formation = f.filled("formation"),
    players = f.list("players") {
        SportsLineupPlayer(
            id = it.require("id"),
            name = it.require("name"),
            number = it.text("number"),
            position = it.filled("position"),
            starter = it.bool("starter") ?: false,
            subbedIn = it.bool("subbedIn") ?: false,
            subbedOut = it.bool("subbedOut") ?: false,
        )
    },
)

data class SportsLineupPlayer(
    val id: String,
    val name: String,
    val number: String? = null,
    val position: String? = null,
    val starter: Boolean = false,
    val subbedIn: Boolean = false,
    val subbedOut: Boolean = false,
)

/**
 * An American football drive (`detail.footballDrives`, NFL and college only), oldest first as
 * sent. Field positions run 0 (away end zone) to 100 (home end zone).
 */
data class SportsFootballDrive(
    val id: String,
    val teamId: String = "",
    val period: String = "",
    /** "TD", "FG", "PUNT", "DOWNS"… */
    val result: String = "",
    val startLabel: String = "",
    val endLabel: String = "",
    /** "6 plays, 23 yards, 3:30". */
    val description: String = "",
    val current: Boolean = false,
    val start: Double? = null,
    val end: Double? = null,
    /** False when the feed says it couldn't place the start or the end. */
    val startKnown: Boolean? = null,
    val endKnown: Boolean? = null,
    val playCount: Int? = null,
    val yards: Double? = null,
    val elapsed: String? = null,
    val plays: List<SportsFootballPlay> = emptyList(),
)

internal fun readFootballDrive(f: SportsFields) = SportsFootballDrive(
    id = f.require("id"),
    teamId = f.text("teamId").orEmpty(),
    period = f.string("period").orEmpty(),
    result = f.string("result").orEmpty(),
    startLabel = f.string("startLabel").orEmpty(),
    endLabel = f.string("endLabel").orEmpty(),
    description = f.string("description").orEmpty(),
    current = f.bool("current") ?: false,
    start = f.double("start"),
    end = f.double("end"),
    startKnown = f.bool("startKnown"),
    endKnown = f.bool("endKnown"),
    playCount = f.int("playCount"),
    yards = f.double("yards"),
    elapsed = f.filled("elapsed"),
    plays = f.list("plays", ::readFootballPlay),
)

/** A play of a drive, with down, distance and field positions. */
data class SportsFootballPlay(
    val id: String,
    val type: String,
    val description: String,
    val clock: String? = null,
    val period: String? = null,
    val typeId: String? = null,
    val wallclock: String? = null,
    val down: Int? = null,
    val distance: Double? = null,
    val yards: Double? = null,
    val possessionTeamId: String? = null,
    val start: Double? = null,
    val end: Double? = null,
    val scoring: Boolean? = null,
)

private fun readFootballPlay(f: SportsFields) = SportsFootballPlay(
    id = f.require("id"),
    type = f.require("type"),
    description = f.require("description"),
    clock = f.string("clock"),
    period = f.string("period"),
    typeId = f.string("typeId"),
    wallclock = f.string("wallclock"),
    down = f.int("down"),
    distance = f.double("distance"),
    yards = f.double("yards"),
    possessionTeamId = f.string("possessionTeamId"),
    start = f.double("start"),
    end = f.double("end"),
    scoring = f.bool("scoring"),
)

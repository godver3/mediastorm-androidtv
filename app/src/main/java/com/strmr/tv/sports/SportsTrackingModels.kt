package com.strmr.tv.sports

import kotlinx.serialization.SerializationException

// A game's tracking data (Go `models.SportsBasketballShot`, `SportsHockeyShot`,
// `SportsWinProbabilityPoint` and `SportsScoreHistory`, Expo `sports-model.ts`, tvOS
// `SportsTrackingModels`) and the live basketball and hockey situations of Expo's
// `SportsSituation` (`detail.sport` kinds `nba` and `nhl`). Read leniently ([SportsFields]): a
// missing, `null` or malformed field is empty and a bad list item is skipped, never the page.

/**
 * A field-goal attempt on the shot chart: feet across the court ([x], 0 to 50) and from the
 * attacking rim ([y]), whether it went in and for how many points, and who took it.
 */
data class SportsBasketballShot(
    val id: String,
    val teamId: String,
    val x: Double,
    val y: Double,
    val made: Boolean = false,
    val points: Int = 0,
    val period: Int = 0,
    val periodLabel: String = "",
    val clock: String = "",
    val description: String = "",
    val athleteId: String? = null,
    val playerName: String? = null,
    val headshotUrl: String? = null,
    val jersey: String? = null,
)

internal fun readBasketballShot(f: SportsFields): SportsBasketballShot {
    fun text(key: String) = f.text(key)?.takeIf { it.isNotBlank() }
    return SportsBasketballShot(
        id = f.require("id"),
        teamId = f.require("teamId"),
        x = f.double("x") ?: throw SerializationException("missing x"),
        y = f.double("y") ?: throw SerializationException("missing y"),
        made = f.bool("made") ?: false,
        points = f.int("points") ?: 0,
        period = f.int("period") ?: 0,
        periodLabel = f.string("periodLabel").orEmpty(),
        clock = f.string("clock").orEmpty(),
        description = f.string("description").orEmpty(),
        athleteId = text("athleteId"),
        playerName = text("playerName"),
        headshotUrl = text("headshotUrl"),
        jersey = text("jersey"),
    )
}

/**
 * An NHL shot attempt in the provider's rink coordinates (feet, centre ice at 0,0; the teams
 * change ends each period): [kind] is "shot" (saved), "missed", "blocked" or "goal".
 */
data class SportsHockeyShot(
    val id: String,
    val teamId: String,
    val x: Double,
    val y: Double,
    val kind: String = "",
    val period: String = "",
    val clock: String = "",
    val description: String = "",
) {
    val isGoal: Boolean get() = kind == "goal"
}

internal fun readHockeyShot(f: SportsFields) = SportsHockeyShot(
    id = f.require("id"),
    teamId = f.string("teamId").orEmpty(),
    x = f.double("x") ?: throw SerializationException("missing x"),
    y = f.double("y") ?: throw SerializationException("missing y"),
    kind = f.string("kind")?.lowercase().orEmpty(),
    period = f.string("period").orEmpty(),
    clock = f.string("clock").orEmpty(),
    description = f.string("description").orEmpty(),
)

/** ESPN's home win probability (0 to 1) after a play, at the period and game clock it happened. */
data class SportsWinProbabilityPoint(
    val playId: String = "",
    val period: Int,
    val periodLabel: String = "",
    val clock: String,
    val home: Double,
)

internal fun readWinProbabilityPoint(f: SportsFields) = SportsWinProbabilityPoint(
    period = f.int("period") ?: throw SerializationException("missing period"),
    clock = f.require("clock"),
    home = f.double("home") ?: throw SerializationException("missing home"),
    playId = f.text("playId").orEmpty(),
    periodLabel = f.string("periodLabel").orEmpty(),
)

/** Every reported score of a basketball game in order, once the server has a complete, validated history (the lead chart). */
data class SportsScoreHistory(
    val eventId: String = "",
    val awayTeamId: String = "",
    val homeTeamId: String = "",
    val complete: Boolean = false,
    val points: List<SportsScorePoint> = emptyList(),
)

internal fun readScoreHistory(f: SportsFields) = SportsScoreHistory(
    eventId = f.text("eventId").orEmpty(),
    awayTeamId = f.text("awayTeamId").orEmpty(),
    homeTeamId = f.text("homeTeamId").orEmpty(),
    complete = f.bool("complete") ?: false,
    points = f.list("points", ::readScorePoint),
)

/** The score at one moment of the history. */
data class SportsScorePoint(
    val id: String = "",
    val period: Int,
    val periodLabel: String = "",
    val clock: String,
    val away: Double,
    val home: Double,
)

private fun readScorePoint(f: SportsFields) = SportsScorePoint(
    period = f.int("period") ?: throw SerializationException("missing period"),
    clock = f.require("clock"),
    away = f.double("away") ?: throw SerializationException("missing away"),
    home = f.double("home") ?: throw SerializationException("missing home"),
    id = f.text("id").orEmpty(),
    periodLabel = f.string("periodLabel").orEmpty(),
)

/**
 * Basketball's live situation (Expo `SportsSituation` kind `nba`): who has the ball (an id,
 * abbreviation or name) and the bonus ("PHI in the bonus"). Only a `detail.sport` of that kind.
 */
data class SportsBasketballSituation(val possession: String = "", val bonus: String? = null)

/** Null for a situation of another kind. */
internal fun readBasketballSituation(f: SportsFields): SportsBasketballSituation? {
    if (f.string("kind") != "nba") return null
    return SportsBasketballSituation(
        possession = f.string("possession")?.trim().orEmpty(),
        bonus = f.string("bonus")?.takeIf { it.isNotBlank() },
    )
}

/**
 * Hockey's live situation (Expo `SportsSituation` kind `nhl`): the strength ("Even strength",
 * "Boston Bruins power play · 1:18") and each side's shots.
 */
data class SportsHockeySituation(val strength: String = "", val shotsAway: Int? = null, val shotsHome: Int? = null)

/** Null for a situation of another kind. */
internal fun readHockeySituation(f: SportsFields): SportsHockeySituation? {
    if (f.string("kind") != "nhl") return null
    return SportsHockeySituation(
        strength = f.string("strength")?.trim().orEmpty(),
        shotsAway = f.int("shotsAway")?.takeIf { it >= 0 },
        shotsHome = f.int("shotsHome")?.takeIf { it >= 0 },
    )
}

/** The basketball leagues (their page, their card's context line). */
val SportsBasketballLeagues = setOf("nba", "wnba", "mens-college-basketball", "womens-college-basketball")

/** Basketball's situation while live (any basketball league). */
val SportsGame.liveBasketballSituation: SportsBasketballSituation?
    get() = if (lifecycle == SportsLifecycle.LIVE && (league in SportsBasketballLeagues || sport == "basketball")) {
        detail?.basketballSituation
    } else {
        null
    }

/** The NHL's situation while live. */
val SportsGame.liveHockeySituation: SportsHockeySituation?
    get() = if (lifecycle == SportsLifecycle.LIVE && league == "nhl") detail?.hockeySituation else null

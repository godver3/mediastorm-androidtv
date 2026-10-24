package com.strmr.tv.sports

import com.strmr.tv.live.LiveChannel
import kotlinx.serialization.Serializable
import java.time.Instant

/** `GET /api/sports/status`: whether the hub is on and whether the server answers per-day scoreboards. */
@Serializable
data class SportsStatus(val enabled: Boolean = false, val dateQueries: Boolean? = null, val lastError: String? = null)

/** A league of `GET /api/sports/leagues`. Only enabled leagues with a working schedule show. */
@Serializable
data class SportsLeague(
    val id: String,
    val enabled: Boolean = false,
    val name: String? = null,
    val sport: String? = null,
    /** The Expo catalog's sport family ("american-football", "ice-hockey", "rugby-union"…). */
    val applicationSport: String? = null,
    val college: Boolean? = null,
    val adapter: String? = null,
    val eventKind: String? = null,
    val supportsTeams: Boolean? = null,
    val coverageNote: String? = null,
    val implementationStatus: String? = null,
    val capabilities: List<String>? = null,
) {
    val label: String get() = name ?: id.uppercase()

    /** As the Expo catalog: enabled, validated or limited, and able to list a schedule; older servers send neither field. */
    val isUsable: Boolean
        get() = enabled &&
            (implementationStatus == null || implementationStatus == "validated" || implementationStatus == "limited") &&
            (capabilities == null || "schedule" in capabilities)

    /** The id a followed competition is saved under, shared with the Expo app (`sports-follows.ts`). */
    val followId: String get() = followAliases[id] ?: id

    companion object {
        val followAliases = mapOf(
            "soccer-eng.1" to "premier-league", "soccer-esp.1" to "la-liga", "soccer-ger.1" to "bundesliga",
            "soccer-ita.1" to "serie-a", "soccer-fra.1" to "ligue-1", "soccer-usa.1" to "mls", "soccer-usa.nwsl" to "nwsl",
            "soccer-uefa.champions" to "uefa-champions-league", "college-football" to "ncaa-football",
            "mens-college-basketball" to "ncaa-mens-basketball", "womens-college-basketball" to "ncaa-womens-basketball",
            "f1" to "formula-one", "nascar" to "nascar-cup",
        )
    }
}

/**
 * A game, match, bout card or tournament day. Equal only when every field is, its [detail]
 * included, so a refresh that brings new plays redraws the page.
 */
@Serializable
data class SportsGame(
    val id: String,
    val league: String,
    val sport: String? = null,
    val title: String? = null,
    val eventKind: String? = null,
    /** The tournament, draw and round of a tennis match ("US Open · Men's Singles · …"). */
    val eventContext: String? = null,
    val courtName: String? = null,
    /** Sets in a tennis match (3 or 5). */
    val bestOf: Int? = null,
    val neutralSite: Boolean? = null,
    val startTime: String,
    val endTime: String? = null,
    val status: String = "scheduled",
    val statusDetail: String? = null,
    val period: String? = null,
    val clock: String? = null,
    val venue: String? = null,
    val broadcasts: List<String>? = null,
    val homeTeam: SportsTeam = SportsTeam(),
    val awayTeam: SportsTeam = SportsTeam(),
    /** A fight card and its bouts (UFC, boxing); read leniently. */
    @Serializable(with = SportsCombatSerializer::class) val combat: SportsCombat? = null,
    /** The scoreboard's listed players or fighters in its order (golf's field), nameless ones dropped. */
    @Serializable(with = SportsParticipantsSerializer::class) val participants: List<SportsParticipant>? = null,
    /** NFL and college football's possession, down and distance; read only while live ([liveFootballSituation]). */
    @Serializable(with = SportsFootballSituationSerializer::class) val footballSituation: SportsFootballSituation? = null,
    /** MLB's at-bat on the scoreboard; read only while live ([liveBaseballSituation]). */
    @Serializable(with = SportsBaseballSituationSerializer::class) val liveSituation: SportsBaseballSituation? = null,
    /** The game page's data ([SportsGameDetail]), read leniently; the scoreboard sends none. */
    @Serializable(with = SportsGameDetailSerializer::class) val detail: SportsGameDetail? = null,
) {
    val start: Instant get() = parseInstant(startTime) ?: Instant.EPOCH
    /** Multi-day events (golf, cricket, a fight card) carry an end; Go's zero time means none. */
    val end: Instant? get() = endTime?.let(::parseInstant)?.takeIf { it.isAfter(start) }

    val lifecycle: SportsLifecycle get() = SportsLifecycle.of(status, statusDetail)

    /** A matchup of two named sides; other events (golf, a fight card) show their title. */
    val isMatchup: Boolean get() = homeTeam.name.isNotEmpty() && awayTeam.name.isNotEmpty() && combat == null

    val displayTitle: String
        get() = combat?.cardName?.ifEmpty { null } ?: title?.ifEmpty { null } ?: "${awayTeam.name} at ${homeTeam.name}"

    /** Scores only once the game is under way, as the Expo normaliser keeps them. */
    val showsScore: Boolean
        get() = isMatchup && lifecycle != SportsLifecycle.SCHEDULED && (homeTeam.score != null || awayTeam.score != null)
}

@Serializable
data class SportsTeam(
    val id: String = "",
    val name: String = "",
    val location: String? = null,
    /** "Patriots": the end zones and the possession field name a side by it. */
    val nickname: String? = null,
    val abbreviation: String? = null,
    val logoUrl: String? = null,
    /** Hex without '#'. */
    val color: String? = null,
    val alternateColor: String? = null,
    val score: String? = null,
    /** Penalty shootout goals (soccer). */
    val shootoutScore: Int? = null,
    val winner: Boolean? = null,
    val record: String? = null,
    val conferenceRecord: String? = null,
    val standingSummary: String? = null,
    val rank: Int? = null,
    val seed: Int? = null,
) {
    val logo: String? get() = logoUrl?.takeIf { it.startsWith("https://") }
    val shortName: String get() = abbreviation?.ifEmpty { null } ?: name

    /** A poll ranking worth showing (Expo keeps 1–25). */
    val displayRank: Int? get() = rank?.takeIf { it in 1..25 }

    /** A tournament seed (Expo keeps 1–128). */
    val displaySeed: Int? get() = seed?.takeIf { it in 1..128 }
}

/**
 * `GET /api/sports/scoreboard`: the day's games and how fresh they are. A board that says when
 * it was built ([updatedAt]) and is [stale] serves older data for every league on it; otherwise
 * [leagues] flags each league the server kept serving its last good answer for.
 */
data class SportsScoreboard(
    val games: List<SportsGame> = emptyList(),
    val updatedAt: String? = null,
    val stale: Boolean = false,
    val leagues: List<SportsLeagueFreshness> = emptyList(),
) {
    /** Leagues whose cards say "Updates delayed". */
    val staleLeagues: Set<String>
        get() = if (updatedAt != null && stale) games.map { it.league }.toSet() else leagues.filter { it.stale }.map { it.league }.toSet()
}

/** A league's freshness on the scoreboard (Go `BackendLeagueAvailability`): stale when the server kept its last good answer, unavailable when it has none. */
@Serializable
data class SportsLeagueFreshness(val league: String, val updatedAt: String? = null, val stale: Boolean = false, val unavailable: Boolean = false)

/** The hub's lifecycle for a game; delays and postponements come from the status text. */
enum class SportsLifecycle(val pill: String) {
    SCHEDULED("UPCOMING"), LIVE("LIVE"), FINAL("FINAL"), DELAYED("DELAYED"), POSTPONED("POSTPONED"), CANCELED("CANCELED");

    val isScheduleChange: Boolean get() = this == DELAYED || this == POSTPONED || this == CANCELED

    companion object {
        fun of(status: String, detail: String?): SportsLifecycle {
            val text = detail.orEmpty().lowercase()
            return when {
                "postpon" in text -> POSTPONED
                "cancel" in text -> CANCELED
                "delay" in text || "suspend" in text -> DELAYED
                status == "live" -> LIVE
                status == "final" -> FINAL
                else -> SCHEDULED
            }
        }
    }
}

// Streams

@Serializable
data class SportsStreamGroup(
    val confidenceTier: String = "",
    val primary: SportsStreamMatch,
    val alternatives: List<SportsStreamMatch>? = null,
)

/** A live channel that looks like it carries the game. */
@Serializable
data class SportsStreamMatch(
    val channelId: String,
    val channelName: String = "",
    val channelUrl: String = "",
    val channelLogo: String? = null,
    val channelTvgId: String? = null,
    val sourceId: String? = null,
    val sourceName: String? = null,
    val programTitle: String? = null,
    val confidence: Double? = null,
    val confidenceTier: String? = null,
    val matchReason: String? = null,
) {
    val key: String get() = "$channelId|$channelUrl"
    val isStrong: Boolean get() = confidenceTier == "strong"

    /** The channel as Live TV tunes it. */
    val channel: LiveChannel
        get() = LiveChannel(id = channelId, name = channelName, url = channelUrl, logo = channelLogo,
                            tvgId = channelTvgId, sourceId = sourceId, sourceName = sourceName)
}

// Preferences

/**
 * `GET/PUT /api/users/{id}/sports/preferences`, shared with the Expo app. The PUT sends the
 * revision it read; the server answers 409 when another device saved in between.
 */
@Serializable
data class SportsPreferencesEnvelope(val revision: Int = 0, val preferences: SportsPreferences = SportsPreferences())

@Serializable
data class SportsPreferences(
    val version: Int = 1,
    val pinnedSportIds: List<String> = emptyList(),
    val competitionIds: List<String> = emptyList(),
    val teamIds: List<String> = emptyList(),
    val athleteIds: List<String> = emptyList(),
    /** Kept as sent: the TV apps don't edit school follows. */
    val schools: List<SportsSchoolFollow> = emptyList(),
)

@Serializable
data class SportsSchoolFollow(val schoolId: String, val scope: String, val programIds: List<String>? = null)

/** RFC 3339 with or without fractions; Go's zero time (and anything before 1970) is none. */
internal fun parseInstant(text: String): Instant? =
    runCatching { java.time.OffsetDateTime.parse(text).toInstant() }.getOrNull()?.takeIf { it.isAfter(Instant.EPOCH) }

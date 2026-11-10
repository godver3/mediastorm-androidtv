package com.strmr.tv.sports

import com.strmr.tv.api.LossyListSerializer
import kotlinx.serialization.Serializable

/**
 * `GET /api/sports/cycling` (Expo `CyclingFeed`, a `ModuleState<CyclingRace[]>`): the organizers'
 * race calendars with each race's stages, and every organizer's feed state in [coverage].
 * [state] is `available`, `stale`, `unavailable`, `pending` or `unsupported`; only the first two
 * carry races.
 */
@Serializable
data class SportsCyclingFeed(
    val state: String = "unavailable",
    val source: SportsCyclingEvidence? = null,
    @Serializable(with = LossyListSerializer::class) val data: List<SportsCyclingRace> = emptyList(),
    val reason: String? = null,
    @Serializable(with = LossyListSerializer::class) val coverage: List<SportsCyclingCoverage> = emptyList(),
) {
    /** The races, when the feed has any to show. */
    val races: List<SportsCyclingRace> get() = if (state == "available" || state == "stale") data else emptyList()

    /** Updates are late or missing (Expo's "Cycling updates delayed or unavailable"). */
    val delayed: Boolean get() = state == "stale" || state == "unavailable"
}

/** Where a cycling answer came from and when it was read (Expo `SourceEvidence`). */
@Serializable
data class SportsCyclingEvidence(val provider: String = "", val observedAt: String? = null, val updatedAt: String? = null)

/** One organizer's feed state. */
@Serializable
data class SportsCyclingCoverage(val id: String = "", val name: String = "", val state: String = "available")

/**
 * A race of an organizer's calendar (Expo `CyclingRace`): a stage race, a one-day race or a
 * championship, with its [stages]. Dates are `YYYY-MM-DD` with no time of day. [scheduleOnly]
 * races have dates only (no results to load); [calendarOnly] ones come from a calendar listing.
 */
@Serializable
data class SportsCyclingRace(
    val id: String,
    val name: String = "",
    val category: String = "unknown",
    val raceKind: String? = null,
    val calendarOnly: Boolean = false,
    val scheduleOnly: Boolean = false,
    val country: String? = null,
    val classification: String? = null,
    val restDays: List<String>? = null,
    val startDate: String? = null,
    val endDate: String? = null,
    @Serializable(with = LossyListSerializer::class) val stages: List<SportsCyclingStage> = emptyList(),
    val source: SportsCyclingEvidence = SportsCyclingEvidence(),
    val sourceUrl: String? = null,
    /** Recorded results rather than a live organizer feed. */
    val captured: Boolean = false,
) {
    val oneDay: Boolean get() = raceKind == "one-day"
    val championship: Boolean get() = raceKind == "championship"

    /** The format Expo filters by: a missing kind counts as a stage race. */
    val format: String get() = raceKind ?: "stage-race"

    /** The id a followed race is saved under, the same across editions (Expo `cyclingFollowId`). */
    val followId: String get() = SportsCycling.followId(id)

    /** The last day of racing (a one-day race's start). */
    val lastDate: String? get() = endDate?.ifEmpty { null } ?: startDate
}

/**
 * A stage (or a one-day race) of a [SportsCyclingRace] (Expo `CyclingStage`). The feed's copy
 * carries results only once a stage's own answer ([sportsCyclingStage]) has been asked for.
 */
@Serializable
data class SportsCyclingStage(
    val id: String,
    val name: String = "",
    val date: String? = null,
    val departure: String? = null,
    val arrival: String? = null,
    val distance: String? = null,
    val terrain: String? = null,
    /** `scheduled`, `cancelled` or `unknown` (a held stage reports unknown). */
    val status: String = "unknown",
    val results: SportsCyclingResults = SportsCyclingResults(),
    val generalClassification: SportsCyclingResults = SportsCyclingResults(),
    val generalClassificationLabel: String? = null,
    /** A team time trial's classification by team. */
    val teamResults: SportsCyclingResults? = null,
    val routeGuide: SportsCyclingRouteGuide? = null,
) {
    val cancelled: Boolean get() = status == "cancelled"

    /** "Thoiry → Paris Champs-Élysées". */
    val route: String get() = listOfNotNull(departure?.ifEmpty { null }, arrival?.ifEmpty { null }).joinToString(" → ")

    /** Published finishers: the hub shows the stage as a result once they're in. */
    val completed: Boolean get() = results.rows.isNotEmpty()
}

/**
 * A classification of a stage (Expo `ModuleState<CyclingResult[]>`): its [state] and, when
 * `available` or `stale`, its [data].
 */
@Serializable
data class SportsCyclingResults(
    val state: String = "pending",
    val source: SportsCyclingEvidence? = null,
    @Serializable(with = LossyListSerializer::class) val data: List<SportsCyclingResult> = emptyList(),
    val reason: String? = null,
) {
    val stale: Boolean get() = state == "stale"

    /** The entries, when the state carries any. */
    val rows: List<SportsCyclingResult> get() = if (state == "available" || state == "stale") data else emptyList()
}

/**
 * A rider's (or, in a team classification, a team's) line: position, time and gap as the
 * organizer reported them, or a DNF / DNS / DSQ [result].
 */
@Serializable
data class SportsCyclingResult(
    val id: String,
    val name: String = "",
    val team: String? = null,
    val teamId: String? = null,
    val bib: Int? = null,
    val nationality: String? = null,
    val rank: Int? = null,
    val time: String? = null,
    val gap: String? = null,
    val result: String? = null,
)

/** The organizer's stage guide: route map and profile images. */
@Serializable
data class SportsCyclingRouteGuide(
    val state: String = "unavailable",
    val sourceUrl: String? = null,
    val mapUrl: String? = null,
    val profileUrl: String? = null,
    val reason: String? = null,
)

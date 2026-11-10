package com.strmr.tv.sports

import com.strmr.tv.api.LossyListSerializer
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonObject
import java.time.Instant
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos

/**
 * `GET /api/sports/races`: the race weekends in the providers' window, each with its sessions,
 * and every series' feed state (Expo `SportsRaceBoard`).
 */
@Serializable
data class SportsRaceBoard(
    @Serializable(with = LossyListSerializer::class) val events: List<SportsRaceEvent> = emptyList(),
    @Serializable(with = LossyListSerializer::class) val leagues: List<SportsRaceFeed> = emptyList(),
) {
    /** A series is stale or unavailable: the hub says racing updates are delayed. */
    val delayed: Boolean get() = leagues.any { it.stale || it.unavailable }

    /**
     * The weekend [id] names or holds as a session, with the session to open on (null: the
     * weekend's default). Expo `resolveRaceSelection`.
     */
    fun resolve(id: String): Pair<SportsRaceEvent, String?>? {
        for (event in events) {
            if (event.id == id) return event to event.id.takeIf { event.isSession }
            event.subEvents.firstOrNull { it.id == id }?.let { return event to it.id }
        }
        return null
    }
}

/** One series' feed state on the race board. */
@Serializable
data class SportsRaceFeed(
    val league: String = "",
    val stale: Boolean = false,
    val unavailable: Boolean = false,
    val updatedAt: String? = null,
)

/**
 * A race weekend (`eventKind` `race`, its sessions in [subEvents]) or one session of it
 * (`race-session`): Expo `SportsRaceEvent`, the backend's generalized `SportsEvent`. A weekend
 * has no [participants]; a session's are its entries or its classification.
 */
@Serializable
data class SportsRaceEvent(
    val id: String,
    val providerEventId: String = "",
    val sessionId: String? = null,
    val sessionType: String? = null,
    val title: String = "",
    val league: String = "",
    val eventKind: String = "race",
    val startTime: String = "",
    val status: String = "scheduled",
    val statusDetail: String? = null,
    val venue: String? = null,
    val broadcasts: List<String>? = null,
    @Serializable(with = LossyListSerializer::class) val participants: List<SportsRaceDriver> = emptyList(),
    @Serializable(with = LossyListSerializer::class) val subEvents: List<SportsRaceEvent> = emptyList(),
    val updatedAt: String? = null,
    val stale: Boolean = false,
    val circuit: SportsCircuit? = null,
    /** An F1 timing archive a recorded capture embeds; the server sends it from its own endpoint. */
    val f1Archive: SportsF1Archive? = null,
) {
    val start: Instant? get() = parseInstant(startTime)
    /** Go's zero time (no update yet) is none. */
    val updated: Instant? get() = updatedAt?.let(::parseInstant)

    val lifecycle: SportsLifecycle get() = SportsLifecycle.of(status, statusDetail)
    val isLive: Boolean get() = status == "live"
    val isFinal: Boolean get() = status == "final"

    /** A single session rather than a weekend. */
    val isSession: Boolean get() = eventKind == "race-session" && subEvents.isEmpty()

    /** MotoGP lists riders; the other series drivers. */
    val riders: Boolean get() = league == "motogp"

    /** A weekend's sessions in start order (Expo's session picker); a lone session is its own. */
    val sessions: List<SportsRaceEvent>
        get() = if (isSession) listOf(this) else subEvents.sortedBy { it.start ?: Instant.MAX }

    /**
     * The session a weekend opens on: the live one, else the last race, else the last session
     * (Expo `defaultRaceSession`).
     */
    val defaultSession: SportsRaceEvent?
        get() = if (isSession) this
        else subEvents.firstOrNull { it.isLive }
            ?: subEvents.lastOrNull { RACE.matches(it.sessionType.orEmpty()) }
            ?: subEvents.lastOrNull()

    /** Where it runs: the session's venue, the weekend's, or the circuit's name. */
    fun place(weekend: SportsRaceEvent?): String? =
        venue?.trim()?.ifEmpty { null } ?: weekend?.venue?.trim()?.ifEmpty { null } ?: weekend?.circuit?.name?.ifEmpty { null }

    // Not private: the serialization plugin's serializer() lives on the companion.
    companion object {
        private val RACE = Regex("""^race\s*\d*$""", RegexOption.IGNORE_CASE)
    }
}

/** An entry of a session, with its position and timing as the provider reported them (Expo `RaceDriver`). */
@Serializable
data class SportsRaceDriver(
    val id: String = "",
    val name: String = "",
    val abbreviation: String? = null,
    val position: Int? = null,
    val winner: Boolean = false,
    val number: String? = null,
    val team: String? = null,
    /** "Classified", "Retired", "DNF"…; "Classified" goes unsaid. */
    val result: String? = null,
    @Serializable(with = LossyListSerializer::class) val statistics: List<SportsRaceStatistic> = emptyList(),
) {
    /** A statistic's value, trimmed; null when missing or blank. */
    fun stat(name: String): String? = statistics.firstOrNull { it.name == name }?.value?.trim()?.ifEmpty { null }

    /** The result worth showing: anything but "Classified". */
    val outcome: String? get() = result?.trim()?.ifEmpty { null }?.takeIf { it != "Classified" }
}

/** A labelled figure of an entry (`lapsCompleted`, `behindTime`, `fastestLap`, `qual1TimeMS`…). */
@Serializable
data class SportsRaceStatistic(val name: String = "", val label: String = "", val value: String = "")

/**
 * Community reference geometry for a circuit as `[longitude, latitude]` points, never live
 * tracking (Expo `SportsCircuit`).
 */
@Serializable
data class SportsCircuit(
    val id: String = "",
    val name: String = "",
    val location: String = "",
    val referenceLengthMeters: Int = 0,
    val coordinates: List<List<Double>> = emptyList(),
    val sourceUrl: String = "",
    val attribution: String = "",
) {
    /**
     * The outline projected flat around its own latitude (east right, north up), shifted to
     * start at 0,0; null for too few, too many or out-of-range points (Expo `projectCircuit`).
     */
    fun outline(): SportsCircuitOutline? {
        if (coordinates.size !in 3..2000) return null
        if (coordinates.any { p -> p.size != 2 || !p.all(Double::isFinite) || abs(p[0]) > 180 || abs(p[1]) > 90 }) return null
        val latitude = coordinates.sumOf { it[1] } / coordinates.size
        val stretch = cos(latitude * PI / 180)
        val flat = coordinates.map { (longitude, lat) -> longitude * stretch to -lat }
        val minX = flat.minOf { it.first }
        val minY = flat.minOf { it.second }
        val width = flat.maxOf { it.first } - minX
        val height = flat.maxOf { it.second } - minY
        if (width <= 0 || height <= 0) return null
        return SportsCircuitOutline(flat.map { (x, y) -> (x - minX) to (y - minY) }, width, height)
    }
}

/** A circuit's projected outline: points within [width] × [height], y growing south. */
data class SportsCircuitOutline(val points: List<Pair<Double, Double>>, val width: Double, val height: Double)

/**
 * `GET /api/sports/motogp/standings` and `/motogp/circuit/{providerEventId}`: the organizer's
 * riders' championship or circuit facts (Expo `MotoGPStandings`, `MotoGPCircuitGuide`; the
 * backend's `MotoGPEnrichment`).
 */
@Serializable
data class SportsMotoGPEnrichment(
    val captured: Boolean = false,
    /** `available`, `stale` or `unavailable`. */
    val state: String = "unavailable",
    val season: Int? = null,
    val source: String = "",
    val sourceUrl: String = "",
    val updatedAt: String? = null,
    val reason: String? = null,
    @Serializable(with = LossyListSerializer::class) val standings: List<SportsMotoGPRider> = emptyList(),
    val circuit: SportsMotoGPCircuit? = null,
) {
    val stale: Boolean get() = state == "stale"
    val unavailable: Boolean get() = state == "unavailable"
}

/** A rider's championship line; MotoGP awards half points, so [points] can be fractional. */
@Serializable
data class SportsMotoGPRider(
    val riderId: String = "",
    val name: String = "",
    val number: Int? = null,
    val team: String? = null,
    @SerialName("constructor") val manufacturer: String? = null,
    val position: Int? = null,
    val points: Double? = null,
    val wins: Int? = null,
    val podiums: Int? = null,
)

/** The organizer's facts about a MotoGP circuit and its published diagram. */
@Serializable
data class SportsMotoGPCircuit(
    val id: String = "",
    val name: String = "",
    val mapUrl: String? = null,
    val lengthMeters: Int? = null,
    val leftCorners: Int? = null,
    val rightCorners: Int? = null,
    val longestStraightMeters: Int? = null,
)

/**
 * `GET /api/sports/f1/archive/{providerEventId}/{sessionId}`: F1's timing feed of a finished
 * session as captured channels (`DriverList`, `TimingData`, `TimingAppData`,
 * `RaceControlMessages`, `WeatherData`…), read by [SportsF1Timing]; never a live feed (Expo
 * `F1Archive`).
 */
@Serializable
data class SportsF1Archive(
    val state: String = "unavailable",
    /** `archive` (the final snapshot) or `recorded` (frozen at [capturedElapsed]). */
    val mode: String = "archive",
    val eventId: String = "",
    val sessionName: String? = null,
    val sourceUrl: String? = null,
    val checkedAt: String? = null,
    val capturedElapsed: String? = null,
    val reason: String? = null,
    val channels: JsonObject? = null,
) {
    val available: Boolean get() = state == "available"
    val recorded: Boolean get() = mode == "recorded"
}

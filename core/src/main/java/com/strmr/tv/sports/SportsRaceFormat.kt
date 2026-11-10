package com.strmr.tv.sports

import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import kotlin.math.abs

/**
 * Race texts shared by the hub card and the race page: timing labels (Expo `SportsRaceTable`),
 * session states and snapshot lines (`SportsRaceDetailTV`), card lines (`SportsHubShowcase`),
 * card titles (`sports-event-card-title.ts`) and dates (`raceDate`).
 */
object SportsRaceFormat {
    /** Series names for a catalog without them (Expo `RACING_LEAGUES`). */
    val seriesNames = mapOf("f1" to "Formula 1", "nascar" to "NASCAR Cup", "indycar" to "IndyCar", "motogp" to "MotoGP")

    /** The gap to the leader, else laps behind, else session time or best lap (Expo `raceTimingLabel`). */
    fun timing(driver: SportsRaceDriver): String? {
        driver.stat("behindTime")?.let { return it }
        lapsBehind(driver)?.let { return if (it == 1) "1 lap behind" else "$it laps behind" }
        return driver.stat("totalTime") ?: driver.stat("fastestLap")
    }

    /** What [timing] shows: "Gap", "Time" or "Best lap" (Expo `raceTimingKind`). */
    fun timingKind(driver: SportsRaceDriver): String? = when {
        driver.stat("behindTime") != null || lapsBehind(driver) != null -> "Gap"
        driver.stat("totalTime") != null -> "Time"
        driver.stat("fastestLap") != null -> "Best lap"
        else -> null
    }

    /** A card's figure for an entry: its timing, else a result other than "Classified". */
    fun cardValue(driver: SportsRaceDriver): String? = timing(driver) ?: driver.outcome

    private fun lapsBehind(driver: SportsRaceDriver): Int? =
        driver.stat("behindLaps")?.takeIf { LAPS.matches(it) }?.toIntOrNull()?.let(::abs)?.takeIf { it > 0 }

    /** Qualifying shows best laps and the Q1–Q3 times. */
    fun isQualifying(session: SportsRaceEvent?): Boolean = session?.sessionType.orEmpty().contains("qual", ignoreCase = true)

    /**
     * A weekend's title on a card: "… Grand Prix of X" shortened to "Grand Prix of X", and that
     * or a shouting title title-cased (Expo `sportsEventCardTitle`). Pages keep the full title.
     */
    fun cardTitle(title: String): String {
        val grandPrix = GRAND_PRIX.find(title)
        val short = grandPrix?.let { "Grand Prix of ${it.groupValues[1]}" } ?: title
        if (grandPrix == null && short != short.uppercase()) return short.trim()
        return WORD.replace(short.lowercase()) { match ->
            val word = match.value
            when {
                word in SMALL_WORDS && match.range.first > 0 -> word
                word in ACRONYMS -> word.uppercase()
                else -> word.replaceFirstChar { it.uppercase() }
            }
        }.trim()
    }

    /** "Results", "Live", "Scheduled", "Cancelled", "Postponed" or "Recorded snapshot" (Expo `sessionState`). */
    fun sessionState(session: SportsRaceEvent): String {
        val detail = session.statusDetail.orEmpty()
        return when {
            session.f1Archive?.recorded == true -> "Recorded snapshot"
            CANCELLED.containsMatchIn(detail) -> "Cancelled"
            POSTPONED.containsMatchIn(detail) -> "Postponed"
            session.isFinal -> "Results"
            session.isLive -> "Live"
            else -> "Scheduled"
        }
    }

    /**
     * A card's progress line: the local start time; a delay or postponement before the start;
     * then the provider's status, or "Results hidden" under Hide Scores.
     */
    fun progress(session: SportsRaceEvent, spoilers: Boolean): String = when {
        session.lifecycle == SportsLifecycle.SCHEDULED -> time(session.start)
        !session.isLive && !session.isFinal -> session.statusDetail?.trim()?.ifEmpty { null } ?: time(session.start)
        spoilers -> "Results hidden"
        else -> session.statusDetail?.trim()?.ifEmpty { null } ?: if (session.isFinal) "Session complete" else "Session in progress"
    }

    /** What a card says where its leaders go when there are none to show. */
    fun cardEmpty(session: SportsRaceEvent, spoilers: Boolean): String = when {
        !session.isLive && !session.isFinal -> "Scheduled start"
        spoilers -> "Hide Scores is on"
        session.isFinal -> "Results unavailable"
        else -> "Standings unavailable"
    }

    /** The page's status line: the session, its state, where and when (Expo's context status). */
    fun contextLine(session: SportsRaceEvent, weekend: SportsRaceEvent): String =
        listOfNotNull(session.sessionType, sessionState(session), session.place(weekend), date(session.start)).joinToString(" · ")

    /** A session in the session picker: "FP1 · Results · Sep 4, 10:30 AM". */
    fun sessionOption(session: SportsRaceEvent): String =
        "${session.sessionType ?: "Session"} · ${sessionState(session)} · ${date(session.start)}"

    /** What the stream picker searches for and the player shows (Expo `raceWatchTarget` title). */
    fun watchTitle(weekend: SportsRaceEvent, session: SportsRaceEvent): String =
        "${weekend.title.trim()} · ${session.sessionType ?: session.title}"

    /**
     * The line over a session's results: what the snapshot is, whether updates are late and
     * when it was taken, never implying tick-by-tick telemetry (Expo `raceUpdateLabel`).
     */
    fun updateLabel(session: SportsRaceEvent, delayed: Boolean): String = listOfNotNull(
        when {
            session.f1Archive?.recorded == true -> "Recorded snapshot"
            session.isLive -> "Live session · Periodic updates"
            else -> sessionState(session)
        },
        "Updates delayed".takeIf { delayed },
        session.updated?.let { "Snapshot updated ${date(it)}" } ?: "Update time unavailable",
    ).joinToString(" · ")

    /** Where a session's figures come from and when they were checked. */
    fun sourceLine(session: SportsRaceEvent): String {
        val source = when {
            session.f1Archive != null -> "F1 timing archive"
            session.riders -> "MotoGP official results"
            else -> "ESPN"
        }
        return "$source · ${session.updated?.let { "Checked ${date(it)}" } ?: "Update time unavailable"}"
    }

    /** What a session with no entries says, as title and message (Expo `emptySessionNotice`). */
    fun emptyNotice(session: SportsRaceEvent): Pair<String, String> {
        val label = session.sessionType ?: "Session"
        return when (sessionState(session)) {
            "Cancelled" -> "$label cancelled" to
                "The provider reports this session as cancelled. No classification has been supplied."
            "Postponed" -> "$label postponed" to
                "The provider reports this session as postponed. The listed time may change; no classification has been supplied."
            "Scheduled" -> "$label scheduled" to
                "Session entries have not been supplied yet. Results will appear here when reported by the provider."
            "Results" -> "$label results unavailable" to
                "The session is marked complete, but the provider has not supplied a classification."
            else -> "$label classification unavailable" to
                "The session is marked live, but the provider has not supplied positions or entries. Live timing is unavailable."
        }
    }

    /** "Sep 6, 1:00 PM" (Expo `raceDate`). */
    fun date(instant: Instant?, zone: ZoneId = ZoneId.systemDefault()): String =
        instant?.atZone(zone)?.let { "${DAY.format(it)}, ${TIME.format(it)}" } ?: "Date unavailable"

    /** "1:00 PM". */
    fun time(instant: Instant?, zone: ZoneId = ZoneId.systemDefault()): String =
        instant?.atZone(zone)?.let(TIME::format) ?: "Time TBD"

    private val LAPS = Regex("""^-?\d+$""")
    private val GRAND_PRIX = Regex("""\bgrand prix of\s+(.+)$""", RegexOption.IGNORE_CASE)
    private val WORD = Regex("""[\p{L}\p{N}]+""")
    private val SMALL_WORDS = setOf("of", "the", "and", "de", "del")
    private val ACRONYMS = setOf("usa", "uk", "tt", "gp", "f1")
    private val CANCELLED = Regex("""\bcancell?ed\b""", RegexOption.IGNORE_CASE)
    private val POSTPONED = Regex("""\bpostponed\b""", RegexOption.IGNORE_CASE)
    private val DAY = DateTimeFormatter.ofPattern("MMM d")
    private val TIME = DateTimeFormatter.ofLocalizedTime(FormatStyle.SHORT)
}

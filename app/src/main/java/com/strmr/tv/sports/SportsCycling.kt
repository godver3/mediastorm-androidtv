package com.strmr.tv.sports

import com.strmr.tv.api.ApiClient
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter

/** The organizers' race calendars (Expo `getSportsCycling`). */
suspend fun ApiClient.sportsCycling(): SportsCyclingFeed = send("GET", "/api/sports/cycling", timeoutMs = 60_000)

/**
 * A stage's own answer, with its classifications and route guide: `/sports/cycling/{race}/{year}/{n}`
 * from the stage id `aso:tour:2026:21` (Expo `getSportsCyclingStage`).
 */
suspend fun ApiClient.sportsCyclingStage(stage: SportsCyclingStage): SportsCyclingStage {
    val (race, year, number) = SportsCycling.stagePath(stage.id) ?: throw IllegalArgumentException("Unknown stage ${stage.id}")
    return send("GET", "/api/sports/cycling/${segment(race)}/${segment(year)}/${segment(number)}", timeoutMs = 60_000)
}

/** The calendar's place for a race relative to today (Expo `CyclingCalendarPeriod`). */
enum class SportsCyclingPeriod(val eyebrow: String) { CURRENT("CURRENT DATES · "), UPCOMING("UPCOMING · "), PAST("PAST DATES · "), UNDATED("") }

/** A race on the Sports hub's day: one stage, or the race itself on a rest day (Expo `HubCard` of kind `cycling`). */
data class SportsCyclingListing(val race: SportsCyclingRace, val stage: SportsCyclingStage?, val restDay: Boolean, val day: String) {
    /** A stage with published finishers is a result; a cancelled one a schedule change; the rest upcoming. */
    val lifecycle: SportsLifecycle
        get() = when {
            stage?.completed == true -> SportsLifecycle.FINAL
            stage?.cancelled == true -> SportsLifecycle.CANCELED
            else -> SportsLifecycle.SCHEDULED
        }

    val key: String get() = "cycling:${stage?.id ?: race.id}"

    /** The stage's day (no time of day), else the hub's day: the start of that local day. */
    val start: Instant get() = SportsCycling.dayStart(stage?.date ?: day) ?: Instant.EPOCH

    /** The next stage after a rest day. */
    val nextStage: SportsCyclingStage? get() = if (restDay) race.stages.firstOrNull { (it.date ?: "") > day } else null
}

/**
 * Road cycling's rules (Expo `sports-cycling.ts` and the hub's cycling cards): dates, the stage
 * a race opens on, rest days, the calendar's order and the hub's cards.
 */
object SportsCycling {
    const val SPORT = "cycling"

    private val DATE_ONLY = Regex("""^\d{4}-\d{2}-\d{2}$""")
    private val EDITION = Regex(""":\d{4}$""")
    private val SHORT_DATE = DateTimeFormatter.ofPattern("MMM d, yyyy")

    /** A race followed across editions: `aso:tour:2026` → `aso:tour`. */
    fun followId(raceId: String): String = raceId.replace(EDITION, "")

    /** The stage endpoint's segments from `org:race:year:number`. */
    fun stagePath(stageId: String): Triple<String, String, String>? {
        val parts = stageId.split(":")
        if (parts.size < 4 || parts.drop(1).take(3).any { it.isEmpty() }) return null
        return Triple(parts[1], parts[2], parts[3])
    }

    /** Today in the device's zone, as cycling dates are written. */
    fun today(zone: ZoneId = ZoneId.systemDefault()): String = LocalDate.now(zone).toString()

    /** "Jul 26, 2026"; a date without a time is never turned into midnight; unknown → "Date to be confirmed". */
    fun date(text: String?, zone: ZoneId = ZoneId.systemDefault()): String {
        if (text.isNullOrEmpty()) return "Date to be confirmed"
        val day = if (DATE_ONLY.matches(text)) runCatching { LocalDate.parse(text) }.getOrNull()
        else parseInstant(text)?.atZone(zone)?.toLocalDate()
        return day?.let(SHORT_DATE::format) ?: "Date to be confirmed"
    }

    /** "Jul 4, 2026 – Jul 26, 2026", one date for a one-day race. */
    fun dateRange(race: SportsCyclingRace): String =
        date(race.startDate) + if (race.endDate != null && race.endDate != race.startDate) " – ${date(race.endDate)}" else ""

    fun dayStart(day: String, zone: ZoneId = ZoneId.systemDefault()): Instant? =
        runCatching { LocalDate.parse(day).atStartOfDay(zone).toInstant() }.getOrNull()

    /** The latest dated stage on or before [today] (finished or not), else the first (Expo `defaultCyclingStage`). */
    fun defaultStage(race: SportsCyclingRace, today: String = today()): SportsCyclingStage? =
        race.stages.lastOrNull { it.date != null && it.date <= today } ?: race.stages.firstOrNull()

    private fun valid(text: String?) = text != null && DATE_ONLY.matches(text) && runCatching { LocalDate.parse(text) }.isSuccess

    /** Where a race sits in the calendar: under way, still to come, over, or without usable dates. */
    fun period(race: SportsCyclingRace, today: String = today()): SportsCyclingPeriod {
        val start = race.startDate
        val end = race.endDate?.ifEmpty { null }
        if (!valid(start) || (end != null && (!valid(end) || end < start!!))) return SportsCyclingPeriod.UNDATED
        if (start!! > today) return SportsCyclingPeriod.UPCOMING
        return if ((end ?: start) < today) SportsCyclingPeriod.PAST else SportsCyclingPeriod.CURRENT
    }

    /**
     * The calendar's order (Expo `compareCyclingCalendar`): current races, upcoming, past (most
     * recent first), undated; then by start date, then name.
     */
    fun calendarOrder(today: String = today()): Comparator<SportsCyclingRace> = Comparator { a, b ->
        val pa = period(a, today)
        val pb = period(b, today)
        if (pa != pb) return@Comparator pa.ordinal - pb.ordinal
        val dates = if (pa == SportsCyclingPeriod.PAST) (b.lastDate ?: "").compareTo(a.lastDate ?: "")
        else (a.startDate ?: "").compareTo(b.startDate ?: "")
        if (dates != 0) dates else a.name.compareTo(b.name)
    }

    /**
     * A race's entries for [day] (Expo `cyclingDayEntries`): a calendar listing within its dates,
     * else every stage on the day, else one entry without a stage, a rest day when the race says so.
     */
    fun dayEntries(race: SportsCyclingRace, day: String): List<Pair<SportsCyclingStage?, Boolean>> {
        val start = race.startDate
        if (race.calendarOnly && start != null && day >= start && day <= (race.lastDate ?: start)) {
            return listOf(race.stages.firstOrNull() to false)
        }
        val stages = race.stages.filter { it.date == day }
        if (stages.isNotEmpty()) return stages.map { it to false }
        return listOf(null to (race.restDays?.contains(day) == true))
    }

    /** The hub's cycling cards for [day]: every race running that day that [include] keeps. */
    fun listings(races: List<SportsCyclingRace>, day: String, include: (SportsCyclingRace) -> Boolean): List<SportsCyclingListing> =
        races.filter { race ->
            val start = race.startDate?.ifEmpty { null }
            start != null && start <= day && (race.lastDate ?: start) >= day && include(race)
        }.flatMap { race -> dayEntries(race, day).map { (stage, rest) -> SportsCyclingListing(race, stage, rest, day) } }

    /**
     * Whether Find Streams can search for a stage (Expo `cyclingWatchTarget.canSearch`): not a
     * recorded capture, not cancelled, dated, and today or later (a calendar listing until it ends).
     */
    fun canSearch(race: SportsCyclingRace, stage: SportsCyclingStage?, today: String = today()): Boolean {
        val date = stage?.date ?: return false
        if (race.captured || stage.cancelled) return false
        return date >= today || (race.calendarOnly && (race.endDate ?: "") >= today)
    }

    /** Following shows a race followed by itself or through cycling as a pinned sport. */
    fun isFollowed(race: SportsCyclingRace, competitionIds: List<String>, pinnedSportIds: List<String>): Boolean =
        race.followId in competitionIds || SPORT in pinnedSportIds

    /** Where a cycling card sorts in its row: followed races in follow order, then cycling as a pinned sport. */
    fun followRank(race: SportsCyclingRace, competitionIds: List<String>, pinnedSportIds: List<String>): Int {
        val followed = competitionIds.indexOf(race.followId)
        if (followed >= 0) return followed
        val pinned = pinnedSportIds.indexOf(SPORT)
        return if (pinned >= 0) competitionIds.size + pinned else Int.MAX_VALUE
    }
}

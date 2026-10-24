package com.strmr.tv.sports

import java.time.Instant

/** A race session listed on the Sports hub, with its weekend (Expo `HubCard` of kind `race`). */
data class SportsRaceListing(val session: SportsRaceEvent, val weekend: SportsRaceEvent) {
    val lifecycle: SportsLifecycle get() = session.lifecycle

    /** One card per weekend in each lifecycle, so the key holds both. */
    val key: String get() = "race:${weekend.id}:${lifecycle.name.lowercase()}"

    /** The entries a card lists by position: the top two, three once final. */
    val leaders: List<SportsRaceDriver>
        get() = session.participants.filter { it.position != null }.sortedBy { it.position }.take(if (session.isFinal) 3 else 2)
}

/**
 * The Sports hub's racing rules: which sessions get a card on a day, their follow order, and
 * how often the race board reloads (Expo `SportsHubTV` race items, `useSportsHubRaces`).
 */
object SportsRaceHub {
    const val MOTORSPORT = "motorsport"

    /** Seconds to the next board load after a failed one. */
    const val RETRY_SECONDS = 30L

    /**
     * The sessions starting within [from] until [until] (the hub's local day) of the series
     * [include] accepts, as one card per weekend in each lifecycle: the earliest session not yet
     * under way, or the latest live or finished one. A weekend whose sessions aren't listed
     * stands for itself; listings keep the board's order.
     */
    fun listings(board: SportsRaceBoard, from: Instant, until: Instant, include: (String) -> Boolean): List<SportsRaceListing> {
        val chosen = LinkedHashMap<String, SportsRaceListing>()
        for (weekend in board.events) {
            if (!include(weekend.league)) continue
            for (session in weekend.subEvents.ifEmpty { listOf(weekend) }) {
                val start = session.start ?: continue
                if (start.isBefore(from) || !start.isBefore(until)) continue
                val listing = SportsRaceListing(session, weekend)
                val previous = chosen[listing.key]?.session?.start
                val earlier = previous != null && start.isBefore(previous)
                val upcoming = !session.isLive && !session.isFinal
                if (previous == null || (if (upcoming) earlier else !earlier)) chosen[listing.key] = listing
            }
        }
        return chosen.values.toList()
    }

    /**
     * Seconds until the board reloads: 30 while a session is live, a series is stale or
     * unavailable, or nothing came back; otherwise 60.
     */
    fun refreshSeconds(board: SportsRaceBoard): Long {
        val live = board.events.any { event -> event.isLive || event.subEvents.any { it.isLive } }
        return if (live || board.delayed || board.events.isEmpty()) 30 else 60
    }

    /** The id a followed series is saved under, shared with the Expo app (`racingFollowId`). */
    fun followId(league: String): String = SportsLeague.followAliases[league] ?: league

    /** Following shows a series followed by itself or through motorsport as a pinned sport. */
    fun isFollowed(league: String, competitionIds: List<String>, pinnedSportIds: List<String>): Boolean =
        followId(league) in competitionIds || MOTORSPORT in pinnedSportIds

    /**
     * Where a race card sorts within its row (Expo `priority`): followed series first, in
     * follow order, then motorsport as a pinned sport, then everything else.
     */
    fun followRank(league: String, competitionIds: List<String>, pinnedSportIds: List<String>): Int {
        val followed = competitionIds.indexOf(followId(league))
        if (followed >= 0) return followed
        val pinned = pinnedSportIds.indexOf(MOTORSPORT)
        return if (pinned >= 0) competitionIds.size + pinned else Int.MAX_VALUE
    }
}

/** A racing series of the catalog: Formula 1, NASCAR Cup, IndyCar, MotoGP and ESPN's other series. */
val SportsLeague.isRacing: Boolean
    get() = sport == "racing" || id in SportsRaceFormat.seriesNames || id.startsWith("espn:racing:")

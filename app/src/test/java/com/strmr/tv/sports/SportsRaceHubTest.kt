package com.strmr.tv.sports

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant

/**
 * Which race sessions the Sports hub lists on a day and in what order, and how often it reloads
 * the race board: Expo `SportsHubTV`'s race items (one card per weekend in each lifecycle) and
 * `useSportsHubRaces` polling.
 */
class SportsRaceHubTest {
    private val day = Instant.parse("2026-10-23T06:00:00Z") to Instant.parse("2026-10-24T06:00:00Z")

    private fun session(id: String, type: String, start: String, status: String, league: String = "motogp") = SportsRaceEvent(
        id = "$league:w:$id", providerEventId = "w", sessionId = id, sessionType = type, title = type, league = league,
        eventKind = "race-session", startTime = start, status = status,
        participants = listOf(
            SportsRaceDriver(id = "c", name = "Third", position = 3),
            SportsRaceDriver(id = "u", name = "Unranked"),
            SportsRaceDriver(id = "a", name = "First", position = 1),
            SportsRaceDriver(id = "b", name = "Second", position = 2),
        ),
    )

    private fun weekend(league: String, vararg sessions: SportsRaceEvent) = SportsRaceEvent(
        id = "$league:w", providerEventId = "w", title = "$league weekend", league = league, eventKind = "race",
        startTime = sessions.first().startTime, status = "scheduled", subEvents = sessions.toList(),
    )

    private val aragon = weekend(
        "motogp",
        session("fp1", "FP1", "2026-10-23T07:00:00Z", "final"),
        session("sprint", "Sprint", "2026-10-23T09:00:00Z", "final"),
        session("q2", "Q2", "2026-10-23T08:00:00Z", "final"),
        session("warmup", "Warm-up", "2026-10-23T15:00:00Z", "live"),
        session("race", "Race", "2026-10-23T18:00:00Z", "scheduled"),
        session("late", "Late", "2026-10-23T20:00:00Z", "scheduled"),
        session("tomorrow", "Tomorrow", "2026-10-24T07:00:00Z", "scheduled"),
    )

    private fun listings(board: SportsRaceBoard, include: (String) -> Boolean = { true }) =
        SportsRaceHub.listings(board, day.first, day.second, include)

    @Test fun oneCardPerWeekendInEachLifecycle() {
        val cards = listings(SportsRaceBoard(listOf(aragon)))
        assertEquals(3, cards.size)
        val byLifecycle = cards.associate { it.lifecycle to it.session.sessionType }
        assertEquals("the latest finished session", "Sprint", byLifecycle[SportsLifecycle.FINAL])
        assertEquals("Warm-up", byLifecycle[SportsLifecycle.LIVE])
        assertEquals("the earliest session still to run", "Race", byLifecycle[SportsLifecycle.SCHEDULED])
        assertTrue("the weekend comes along", cards.all { it.weekend === aragon })
        assertEquals(3, cards.map { it.key }.toSet().size)
    }

    @Test fun onlyTheDaysSessionsOfEnabledSeries() {
        val f1 = weekend("f1", session("race", "Race", "2026-10-22T13:00:00Z", "final", league = "f1"))
        val board = SportsRaceBoard(listOf(aragon, f1))
        assertTrue("yesterday's race isn't today's", listings(board).none { it.session.league == "f1" })
        assertTrue(listings(board) { it != "motogp" }.isEmpty())
        val tomorrow = SportsRaceHub.listings(board, day.second, day.second.plusSeconds(86_400)) { true }
        assertEquals(listOf("Tomorrow"), tomorrow.map { it.session.sessionType })
    }

    @Test fun aWeekendWithoutSessionsStandsForItself() {
        val bare = SportsRaceEvent(id = "indycar:w", title = "Grand Prix", league = "indycar", startTime = "2026-10-23T19:00:00Z")
        val cards = listings(SportsRaceBoard(listOf(bare)))
        assertEquals(listOf(bare), cards.map { it.session })
        assertEquals(bare, cards.single().weekend)
    }

    @Test fun cardLeadersByPosition() {
        val cards = listings(SportsRaceBoard(listOf(aragon))).associateBy { it.lifecycle }
        assertEquals("three once final", listOf("a", "b", "c"), cards.getValue(SportsLifecycle.FINAL).leaders.map { it.id })
        assertEquals("two while live", listOf("a", "b"), cards.getValue(SportsLifecycle.LIVE).leaders.map { it.id })
    }

    @Test fun boardReloadCadence() {
        assertEquals("nothing came back", 30, SportsRaceHub.refreshSeconds(SportsRaceBoard()))
        assertEquals("a live session", 30, SportsRaceHub.refreshSeconds(SportsRaceBoard(listOf(aragon))))
        val quiet = weekend("f1", session("race", "Race", "2026-10-23T13:00:00Z", "final", league = "f1"))
        assertEquals(60, SportsRaceHub.refreshSeconds(SportsRaceBoard(listOf(quiet), listOf(SportsRaceFeed("f1")))))
        val stale = SportsRaceBoard(listOf(quiet), listOf(SportsRaceFeed("f1", stale = true)))
        assertEquals("a series is late", 30, SportsRaceHub.refreshSeconds(stale))
        assertEquals(30, SportsRaceHub.refreshSeconds(SportsRaceBoard(listOf(quiet), listOf(SportsRaceFeed("f1", unavailable = true)))))
    }

    @Test fun followedSeriesSortFirstWithExpoFollowIds() {
        assertEquals("formula-one", SportsRaceHub.followId("f1"))
        assertEquals("nascar-cup", SportsRaceHub.followId("nascar"))
        assertEquals("indycar", SportsRaceHub.followId("indycar"))
        assertEquals("motogp", SportsRaceHub.followId("motogp"))
        val follows = listOf("nba", "motogp", "formula-one")
        assertEquals(2, SportsRaceHub.followRank("f1", follows, emptyList()))
        assertEquals(1, SportsRaceHub.followRank("motogp", follows, emptyList()))
        assertEquals(Int.MAX_VALUE, SportsRaceHub.followRank("nascar", follows, emptyList()))
        assertEquals("a pinned motorsport comes after the follows", 4, SportsRaceHub.followRank("nascar", follows, listOf("nfl", "motorsport")))
        assertTrue(SportsRaceHub.isFollowed("nascar", emptyList(), listOf("motorsport")))
        assertTrue(SportsRaceHub.isFollowed("indycar", listOf("indycar"), emptyList()))
        val ordered = listOf("nascar", "f1", "motogp").sortedBy { SportsRaceHub.followRank(it, follows, emptyList()) }
        assertEquals(listOf("motogp", "f1", "nascar"), ordered)
    }

    @Test fun racingSeriesOfTheCatalog() {
        assertTrue(SportsLeague("f1", enabled = true, sport = "racing").isRacing)
        assertTrue(SportsLeague("espn:racing:nascar-truck", enabled = true).isRacing)
        assertTrue(!SportsLeague("nba", enabled = true, sport = "basketball").isRacing)
    }
}

package com.strmr.tv.sports

import com.strmr.tv.api.ApiJson
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * League standings (Expo `SportsLeagueStandings`, `participantWithStanding`) and the hub's Sport
 * and League filters (Expo `SportsHubTV`), from trimmed excerpts of real answers.
 */
class SportsStandingsFiltersTest {
    private val nfl: SportsLeagueStandings = ApiJson.decodeFromString(
        """{"league":"nfl","state":"available","source":"ESPN","sourceUrl":"https://site.api.espn.com/x","updatedAt":"2026-10-23T14:21:27Z",
            "groups":[{"id":"8:0","title":"American Football Conference","season":2026,"seasonLabel":"2026",
              "columns":[{"key":"playoffSeed","label":"Seed"},{"key":"overall","label":"Record"},{"key":"wins","label":"Wins"},
                         {"key":"losses","label":"Losses"},{"key":"ties","label":"Draws"},{"key":"winPercent","label":"Win %"},
                         {"key":"gamesBehind","label":"GB"},{"key":"vs. Conf.","label":"Conference"},{"key":"streak","label":"Streak"}],
              "rows":[{"id":"12","name":"Kansas City Chiefs","abbreviation":"KC","kind":"team",
                       "values":{"overall":"5-0","playoffSeed":"1","wins":"5","losses":"0","ties":"0","winPercent":"1.000","gamesBehind":"-"}},
                      {"id":"7","name":"Denver Broncos","kind":"team","values":{"wins":"3","losses":"2","ties":"1","playoffSeed":"0"}},
                      {"id":"9","name":"No Record","kind":"team","values":{"wins":"3","losses":"x"}},
                      {"name":"no id"}]}]}""",
    )
    private val table get() = nfl.groups.single()

    @Test fun decodesAndPicksColumns() {
        assertTrue(nfl.available)
        assertEquals("a row without an id is skipped", 3, table.rows.size)
        assertEquals(listOf("Seed", "Record", "Win %", "GB"), SportsStandings.columns("nfl", table).map { it.label })
        assertEquals(listOf("overall", "vs. Conf.", "playoffSeed", "winPercent"), SportsStandings.columns("college-football", table).map { it.key })
        assertEquals("a seed of 0 is none", "–", SportsStandings.value(table.rows[1], "playoffSeed"))
        assertEquals("–", SportsStandings.value(table.rows[1], "streak"))
        assertEquals("Kansas City Chiefs", SportsStandings.leader(table)?.name)
        assertEquals("LEADING ENTRY · SEED 1", SportsStandings.leaderEyebrow(table.rows[0]))
        assertEquals("5-0 record", SportsStandings.leaderSummary(table.rows[0], table))
        assertEquals("Conference", SportsStandings.groupLabel("mens-college-basketball"))
        val unavailable: SportsLeagueStandings = ApiJson.decodeFromString(
            """{"league":"ufc","state":"unavailable","source":"ESPN","sourceUrl":"","reason":"Standings are not available for this competition"}""",
        )
        assertTrue(unavailable.delayed)
        assertTrue(unavailable.groups.isEmpty())
    }

    @Test fun recordsForCardsThatLackThem() {
        val tables = nfl.groups
        assertEquals("overall first", "5-0", SportsStandings.record(SportsTeam(id = "12", name = "KC"), tables))
        assertEquals("wins–losses–ties", "3–2–1", SportsStandings.record(SportsTeam(id = "espn:7", name = "DEN"), tables))
        assertNull("non-numeric losses", SportsStandings.record(SportsTeam(id = "9", name = "X"), tables))
        assertNull("a record already sent wins", SportsStandings.record(SportsTeam(id = "12", name = "KC", record = "4-1"), tables))
        assertNull("no row", SportsStandings.record(SportsTeam(id = "99", name = "?"), tables))
        val game = SportsGame(id = "g", league = "nfl", startTime = "2026-10-23T17:00:00Z",
                              awayTeam = SportsTeam(id = "12", name = "KC"), homeTeam = SportsTeam(id = "7", name = "DEN", record = "2-2"))
        val filled = SportsStandings.withRecords(game, tables)
        assertEquals("5-0", filled.awayTeam.record)
        assertEquals("2-2", filled.homeTeam.record)
    }

    private val catalog = listOf(
        SportsLeague("nfl", enabled = true, name = "NFL", sport = "football", applicationSport = "american-football"),
        SportsLeague("nba", enabled = true, name = "NBA", sport = "basketball"),
        SportsLeague("mens-college-basketball", enabled = true, name = "NCAAM", sport = "basketball", college = true),
        SportsLeague("nhl", enabled = true, name = "NHL", sport = "hockey"),
        SportsLeague("soccer-eng.1", enabled = false, name = "Premier League", sport = "soccer"),
        SportsLeague("f1", enabled = true, name = "Formula 1", sport = "racing"),
        SportsLeague("aso:tour", enabled = true, name = "Tour de France", sport = "cycling", applicationSport = "cycling",
                     implementationStatus = "limited", capabilities = listOf("schedule")),
    )

    @Test fun sportChoicesFollowPins() {
        val ids = SportsHubFilters.sportOptions(catalog, pinned = listOf("cycling", "ice-hockey")).map { it.id }
        assertEquals(listOf("cycling", "ice-hockey", "american-football", "basketball", "motorsport", "college"), ids)
        assertEquals("Hockey", SportsHubFilters.sportLabel("ice-hockey"))
        assertEquals("All Sports", SportsHubFilters.sportLabel("all"))
        assertEquals("american-football", SportsHubFilters.familyOf("football"))
    }

    @Test fun leagueChoicesFollowFollows() {
        val tour = SportsCyclingRace(id = "aso:tour:2026", name = "Tour de France")
        val all = SportsHubFilters.leagueOptions("all", catalog, listOf(tour, tour.copy(id = "aso:tour:2025")), followed = listOf("nhl"), pinned = listOf("basketball"))
        assertEquals(listOf("nhl", "nba", "mens-college-basketball", "nfl", "f1", "aso:tour"), all.map { it.id })
        assertEquals(listOf("nba", "mens-college-basketball"), SportsHubFilters.leagueOptions("basketball", catalog, emptyList(), emptyList(), emptyList()).map { it.id })
        assertEquals(listOf("mens-college-basketball"), SportsHubFilters.leagueOptions("college", catalog, emptyList(), emptyList(), emptyList()).map { it.id })
        assertEquals(listOf("f1"), SportsHubFilters.leagueOptions("motorsport", catalog, listOf(tour), emptyList(), emptyList()).map { it.id })
        assertEquals("Tour de France", SportsHubFilters.leagueOptions("cycling", catalog, listOf(tour), emptyList(), emptyList()).single().label)
    }

    @Test fun filterKeepsTheChosenSportAndLeague() {
        val nba = SportsFilterSubject("basketball", "nba")
        val college = SportsFilterSubject("basketball", "mens-college-basketball", college = true)
        assertTrue(SportsHubFilters.matches(nba, "all", "all"))
        assertTrue(SportsHubFilters.matches(nba, "basketball", "nba"))
        assertFalse(SportsHubFilters.matches(nba, "basketball", "mens-college-basketball"))
        assertFalse(SportsHubFilters.matches(nba, "college", "all"))
        assertTrue(SportsHubFilters.matches(college, "college", "all"))
        assertFalse(SportsHubFilters.matches(SportsFilterSubject("motorsport", "f1"), "cycling", "all"))
        val leagues = catalog.associateBy { it.id }
        assertTrue("a team competition has standings", SportsHubFilters.hasDetails("all", "nba", leagues))
        assertTrue(SportsHubFilters.hasDetails("motorsport", "all", leagues))
        assertTrue(SportsHubFilters.hasDetails("cycling", "all", leagues))
        assertFalse("a sport alone", SportsHubFilters.hasDetails("basketball", "all", leagues))
        assertEquals(0, SportsHubFilters.rank(catalog[3], listOf("nhl"), emptyList()))
        assertEquals(1, SportsHubFilters.rank(catalog[1], listOf("nhl"), listOf("basketball")))
    }
}

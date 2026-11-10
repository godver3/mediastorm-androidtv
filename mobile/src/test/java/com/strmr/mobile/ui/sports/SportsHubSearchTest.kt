package com.strmr.mobile.ui.sports

import com.strmr.tv.sports.SportsGame
import com.strmr.tv.sports.SportsTeam
import com.strmr.tv.ui.sports.SportsHubItem
import com.strmr.tv.ui.sports.SportsSection
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SportsHubSearchTest {
    private fun game(id: String, home: String, away: String, abbr: String? = null) = SportsGame(
        id = id, league = "nba", startTime = "2026-11-10T00:00:00Z",
        homeTeam = SportsTeam(id = "$id-h", name = home, abbreviation = abbr), awayTeam = SportsTeam(id = "$id-a", name = away),
    )
    private val league = { id: String -> if (id == "nba") "NBA" else id }
    private val sections = listOf(
        SportsSection("live", "Live Now", listOf(SportsHubItem.Match(game("1", "Boston Celtics", "Miami Heat", "BOS")))),
        SportsSection("upcoming", "Upcoming", listOf(SportsHubItem.Match(game("2", "Utah Jazz", "Denver Nuggets")))),
    )

    @Test fun emptyQueryKeepsEverything() = assertEquals(sections, SportsHubSearch.filter(sections, "  ", league))

    @Test fun matchesNamesAbbreviationsAndLeagueIgnoringCase() {
        assertEquals(listOf("live"), SportsHubSearch.filter(sections, "celtics", league).map { it.key })
        assertEquals(listOf("live"), SportsHubSearch.filter(sections, "bos", league).map { it.key })
        assertEquals(2, SportsHubSearch.filter(sections, "nba", league).size)
        assertTrue(SportsHubSearch.filter(sections, "lakers", league).isEmpty())
    }
}

package com.strmr.tv.sports

import com.strmr.tv.api.LossyList
import com.strmr.tv.sports.SportsGameFixtures.NFL_DETAIL
import com.strmr.tv.sports.SportsGameFixtures.league
import com.strmr.tv.sports.SportsGameFixtures.nflDetail
import com.strmr.tv.sports.SportsGameFixtures.page
import com.strmr.tv.ui.sports.game.SportsGameTab
import com.strmr.tv.ui.sports.game.SportsGameTab.DRIVES
import com.strmr.tv.ui.sports.game.SportsGameTab.LINEUPS
import com.strmr.tv.ui.sports.game.SportsGameTab.OVERVIEW
import com.strmr.tv.ui.sports.game.SportsGameTab.PLAYS
import com.strmr.tv.ui.sports.game.SportsGameTab.STANDINGS
import com.strmr.tv.ui.sports.game.SportsGameTab.STATS
import com.strmr.tv.ui.sports.game.SportsKind
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The game page's tabs follow the Expo TV page (`SportsGameDetailTV` `availableTab`): Drives for
 * NFL / college football with drives, Lineups for soccer with lineups, stats and plays by the
 * detail's capabilities (no stats for golf tournaments or cricket), Standings when a table came
 * along, and no tabs at all with scores hidden. The same cases as the tvOS `SportsGameTabTests`.
 */
class SportsGameTabTest {
    init {
        LossyList.report = {}
    }

    @Test fun footballFinalTabsAndLabels() {
        val page = page(detail = NFL_DETAIL)
        assertEquals(listOf(OVERVIEW, DRIVES, STATS, PLAYS), SportsGameTab.available(page, spoilers = false))
        assertEquals("Box Score", STATS.label(page.kind))
        assertEquals("Play-by-Play", PLAYS.label(page.kind))
    }

    @Test fun spoilersHideEveryTab() {
        assertEquals(emptyList<SportsGameTab>(), SportsGameTab.available(page(detail = NFL_DETAIL), spoilers = true))
    }

    @Test fun drivesOnlyForNflAndCollegeFootball() {
        assertFalse("no drives sent", DRIVES.isAvailable(page(detail = nflDetail(""""footballDrives":[]"""))))
        val other = page(""""league":"espn:football:ufl"""", NFL_DETAIL)
        assertEquals(SportsKind.FOOTBALL, other.kind)
        assertFalse(DRIVES.isAvailable(other))
        assertTrue(DRIVES.isAvailable(page(""""league":"college-football"""", NFL_DETAIL)))
    }

    @Test fun soccerTabsAndLabels() {
        val detail = """{"capabilities":{"plays":true,"stats":true},
            "lineups":[{"teamId":"3","formation":"4-3-3","players":[{"id":"1","name":"A Keeper","starter":true}]}],
            "standings":[{"title":"English Premier League Standings","rows":[{"teamId":"3","name":"Chicago","rank":"1"}]}]}"""
        val soccer = page(""""league":"soccer-eng.1","sport":"soccer"""", detail)
        assertEquals(listOf(OVERVIEW, LINEUPS, STATS, PLAYS, STANDINGS), SportsGameTab.available(soccer, spoilers = false))
        assertEquals("Match Stats", STATS.label(soccer.kind))
        assertEquals("Commentary", PLAYS.label(soccer.kind))
        // Rugby lineups belong to its Overview (Expo's rugby overview), not a tab.
        val rugby = page(""""league":"rugby-180659","sport":"rugby"""", detail)
        assertEquals(SportsKind.RUGBY_UNION, rugby.kind)
        assertFalse(LINEUPS.isAvailable(rugby))
    }

    @Test fun noStatsTabForGolfTournamentsAndCricket() {
        val detail = """{"capabilities":{"plays":false,"stats":true}}"""
        val pga = league("""{"id":"pga","enabled":true,"applicationSport":"golf","adapter":"tournament","eventKind":"tournament","supportsTeams":false}""")
        val golf = page(""""league":"pga","sport":"golf"""", detail, pga)
        assertTrue(golf.isIndividualGolf)
        assertEquals(listOf(OVERVIEW), SportsGameTab.available(golf, spoilers = false))
        val cricket = page(""""league":"cricket-8048","sport":"cricket"""", detail)
        assertEquals(listOf(OVERVIEW), SportsGameTab.available(cricket, spoilers = false))
        val teamGolf = league("""{"id":"cup","enabled":true,"applicationSport":"golf","adapter":"team-golf","eventKind":"matchup"}""")
        assertTrue(STATS.isAvailable(page(""""league":"cup"""", detail, teamGolf)))
    }

    @Test fun scheduledGameShowsOverviewAndAnyTable() {
        val scheduled = page(
            """"status":"scheduled","statusDetail":"Sun, October 25th at 9:30 AM EDT"""",
            """{"capabilities":{"plays":false,"stats":false,"winProbability":false},"standings":[{"title":"Table","rows":[]}]}""",
        )
        assertEquals(listOf(OVERVIEW, STANDINGS), SportsGameTab.available(scheduled, spoilers = false))
    }

    @Test fun pickedTabIsKeptButShownOnlyWithData() {
        assertEquals(PLAYS, SportsGameTab.shown(PLAYS, listOf(OVERVIEW, STATS, PLAYS)))
        assertEquals(OVERVIEW, SportsGameTab.shown(PLAYS, listOf(OVERVIEW, STATS)))
        assertEquals(OVERVIEW, SportsGameTab.shown(STANDINGS, emptyList()))
    }

    @Test fun sportFamily() {
        val catalog = league("""{"id":"nhl","enabled":true,"applicationSport":"ice-hockey"}""")
        assertEquals(SportsKind.HOCKEY, page(""""league":"nhl","sport":"hockey"""", league = catalog).kind)
        assertEquals("the feed's own sport name", SportsKind.HOCKEY, page(""""league":"nhl","sport":"hockey"""").kind)
        assertEquals("the league id when the feed has none", SportsKind.BASKETBALL, page(""""league":"nba","sport":null""").kind)
        assertEquals(SportsKind.RUGBY_LEAGUE, page(""""league":"rugby-league-3","sport":null""").kind)
        assertEquals(SportsKind.SOCCER, page(""""league":"soccer-usa.nwsl","sport":null""").kind)
    }

    @Test fun displayOrderPutsHomeFirstForTeamSports() {
        assertEquals(listOf("CHI", "NE"), page().teams.map { it.shortName })
        assertEquals("away first for tennis", listOf("NE", "CHI"), page(""""league":"atp","sport":"tennis"""").teams.map { it.shortName })
    }
}

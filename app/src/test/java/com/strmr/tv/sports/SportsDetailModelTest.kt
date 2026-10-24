package com.strmr.tv.sports

import com.strmr.tv.api.LossyList
import com.strmr.tv.sports.SportsGameFixtures.NFL_DETAIL
import com.strmr.tv.sports.SportsGameFixtures.detail
import com.strmr.tv.sports.SportsGameFixtures.page
import com.strmr.tv.ui.sports.game.SportsBoxScoreTable
import com.strmr.tv.ui.sports.game.spoilerSafe
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant

/**
 * The game page's reading of a game's `detail` (Go `models.SportsGameDetail`, Expo
 * `sports-backend-source.ts` / `sports-model.ts`): lenient lists, the box score's tables and the
 * pre-game context kept with scores hidden (`sportsSpoilerContext`). The tvOS
 * `SportsDetailModelTests` cases, with the page's wording in [SportsGameFormatTest].
 */
class SportsDetailModelTest {
    /** Items the lenient reads skipped (reported here rather than to the device log). */
    private val skipped = mutableListOf<String>()

    init {
        LossyList.report = { skipped += it }
    }

    @Test fun decodesTheDetailLeniently() {
        val detail = page(detail = NFL_DETAIL).detail
        assertEquals(SportsCapabilities(plays = true, stats = true), detail.capabilities)
        assertEquals("a number reads as text", listOf("0", "14"), detail.periods.map { it.away })
        assertEquals("the play without an id is skipped", listOf("1", "2", "3"), detail.plays.map { it.id })
        assertEquals(listOf("skipped plays #3: missing id"), skipped)
        assertFalse("no scoring flag is not scoring", detail.plays[2].scoring)
        assertEquals("17", detail.plays[1].teamId)
        assertEquals(5, detail.playerStats.size)
        assertTrue("null stats are empty", detail.playerStats[4].stats.isEmpty())
        assertEquals("Kickoff", detail.footballDrives.first().plays.first().type)
        assertNull(detail.footballDrives.last().playCount)
        assertEquals(listOf("17", "3"), detail.pregame?.teams?.map { it.teamId })
    }

    @Test fun emptyAndNullDetail() {
        assertEquals(SportsGameDetail(), detail("""{"periods":null,"plays":null,"comparisons":null,"capabilities":{}}"""))
        assertEquals("wrong types read as empty", SportsGameDetail(), detail("""{"plays":"none","stale":"yes","pregame":[1]}"""))
        assertEquals("a game without detail", SportsGameDetail(), page().detail)
        assertFalse("no detail, nothing stale", page().isStale())
        assertEquals("a detail that isn't an object", SportsGameDetail(), page(detail = "\"broken\"").detail)
    }

    @Test fun aRefreshWithNewPlaysIsANewGame() {
        val before = page(detail = NFL_DETAIL).game
        assertEquals(before, page(detail = NFL_DETAIL).game)
        assertNotEquals(before, page(detail = SportsGameFixtures.nflDetail(""""plays":[{"id":"9","title":"Touchdown"}]""")).game)
    }

    @Test fun standingsLineupsAndPregameDecode() {
        val detail = detail(
            """{"standings":[{"title":"English Premier League Standings","rows":[
                 {"teamId":"382","name":"Manchester City","rank":"1","played":"5","points":15,"goalDifference":"+8"},
                 {"name":"No team id"},{"teamId":"359"}]}],
               "lineups":[{"teamId":"379","formation":"5-4-1","players":[
                 {"id":"126703","name":"Martin Dúbravka","number":"1","position":"Goalkeeper","starter":true},
                 {"id":"207146","name":"Josh Cullen","number":24,"starter":true,"subbedOut":true},
                 {"id":"x"}]}],
               "pregame":{"seasonLabel":"2026 · Postseason","updatedAt":"2026-10-23T08:21:39-06:00",
                 "teams":[{"teamId":"30","record":"98-64","rank":3,"standingStats":[{"label":"Streak","value":"L1"}],
                   "recent":[{"id":"401908028","date":"2026-10-20T00:00:00Z","opponent":"Cleveland Guardians","result":"W","score":"7-3"}],
                   "probables":[{"name":"Nick Martinez","label":"Probable starting pitcher"}]}],
                 "comparisons":[{"label":"ERA","away":"3.70","home":"3.51"}],
                 "previousMeeting":{"date":"2026-04-01T17:40:00Z","homeTeamId":"8","awayTeamId":"30","homeScore":"8","awayScore":2}}}""",
        )
        val table = detail.standings.first()
        assertEquals(listOf("Manchester City", "No team id"), table.rows.map { it.name })
        assertEquals("15", table.rows.first().points)
        val lineup = detail.lineups.first()
        assertEquals(listOf("Martin Dúbravka", "Josh Cullen"), lineup.players.map { it.name })
        assertEquals("24", lineup.players.last().number)
        assertTrue(lineup.players.last().subbedOut)
        val team = detail.pregame?.team("30")
        assertEquals(3, team?.rank)
        assertEquals("Cleveland Guardians", team?.recent?.first()?.opponent)
        assertNull(team?.probables?.first()?.value)
        assertEquals("2", detail.pregame?.previousMeeting?.awayScore)
    }

    @Test fun spoilerSafePregame() {
        val pregame = SportsPregame(
            seasonLabel = "2026", updatedAt = "2026-10-22T12:00:00Z",
            teams = listOf(SportsPregameTeam(teamId = "3", record = "5-2", recent = listOf(
                SportsRecentResult("old", "2026-10-15T00:00:00Z", "A", "W", "1-0"),
                SportsRecentResult("game", "2026-10-22T23:00:00Z", "B", "L", "0-1"),
                SportsRecentResult("later", "2026-10-30T00:00:00Z", "C", "W"),
            ))),
            comparisons = listOf(SportsComparison("Points per game", "20", "24")),
        )
        val start = Instant.parse("2026-10-23T00:15:00Z")
        val before = pregame.spoilerSafe(start, "game")
        assertEquals("a snapshot from before the start keeps its records", "5-2", before.teams.first().record)
        assertEquals("never this game or later ones", listOf("old"), before.teams.first().recent.map { it.id })
        assertEquals(1, before.comparisons.size)
        val later = pregame.copy(
            updatedAt = "2026-10-23T08:00:00Z",
            previousMeeting = SportsPreviousMeeting("2026-10-23T03:00:00Z", homeTeamId = "3", awayTeamId = "17", homeScore = "30", awayScore = "24"),
        ).spoilerSafe(start, "game")
        assertNull("a later snapshot keeps only earlier results", later.teams.first().record)
        assertEquals(listOf("old"), later.teams.first().recent.map { it.id })
        assertTrue(later.comparisons.isEmpty())
        assertNull(later.previousMeeting)
    }

    @Test fun boxScoreTablesPerSideAndCategory() {
        val stats = page(detail = NFL_DETAIL).detail.playerStats
        val tables = SportsBoxScoreTable.tables("17", stats)
        assertEquals(listOf("Passing", "Rushing"), tables.map { it.category })
        assertEquals("every column a category's rows use, in order", listOf("CAR", "YDS", "TD"), tables[1].columns)
        assertEquals(listOf("Drake Maye", "Rhamondre Stevenson"), tables[1].rows.map { it.name })
        assertEquals("rows without stats are left out", listOf("Passing"), SportsBoxScoreTable.tables("3", stats).map { it.category })
        assertEquals("SACK / YDS", SportsBoxScoreTable.heading("SACK-YDS"))
    }
}

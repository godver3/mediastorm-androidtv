package com.strmr.tv.sports

import com.strmr.tv.api.LossyList
import com.strmr.tv.sports.SportsGameFixtures.NFL_DETAIL
import com.strmr.tv.sports.SportsGameFixtures.game
import com.strmr.tv.sports.SportsGameFixtures.nflDetail
import com.strmr.tv.sports.SportsGameFixtures.page
import com.strmr.tv.ui.sports.game.SportsGameFormat
import com.strmr.tv.ui.sports.game.SportsTeamColors
import com.strmr.tv.ui.sports.game.SportsTeamStats
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The game page's wording and rules (Expo `formatDetailStatus`, `formatGameResult`,
 * `getSportsPregameState`, the detail's freshness, `overviewComparisons` and the matchup
 * colours): the rest of the tvOS `SportsDetailModelTests` cases.
 */
class SportsGameFormatTest {
    init {
        LossyList.report = {}
    }

    private val hockey = """"league":"nhl","sport":"hockey""""
    private val soccer = """"league":"soccer-eng.1","sport":"soccer","statusDetail":"FT","period":"FT""""

    @Test fun statusWording() {
        assertEquals("Final", SportsGameFormat.detailStatus(page()))
        assertEquals("Q3 · 5:21", SportsGameFormat.detailStatus(page(""""status":"live","statusDetail":"Q3 5:21","period":"Q3","clock":"5:21"""")))
        assertEquals("Final · Overtime", SportsGameFormat.detailStatus(page("""$hockey,"statusDetail":"Final/OT","period":"OT"""")))
        assertEquals("2nd period · 12:00", SportsGameFormat.detailStatus(page("""$hockey,"status":"live","period":"P2","clock":"12:00"""")))
        assertEquals("Full time", SportsGameFormat.detailStatus(page(soccer)))
        assertEquals("Full time · After extra time", SportsGameFormat.detailStatus(page("""$soccer,"statusDetail":"AET"""")))
        val shootout = """$soccer,"homeTeam":{"id":"1","name":"A","score":"3","shootoutScore":4},"awayTeam":{"id":"2","name":"B","score":"3","shootoutScore":2}"""
        assertEquals("Full time · Penalties", SportsGameFormat.detailStatus(page(shootout)))
        assertEquals("Halftime", SportsGameFormat.detailStatus(page("""$soccer,"status":"live","period":"HT","clock":"45'"""")))
        assertEquals("Second half · 67'", SportsGameFormat.detailStatus(page("""$soccer,"status":"live","period":"H2","clock":"67'"""")))
        assertEquals("Postponed", SportsGameFormat.detailStatus(page(""""status":"scheduled","statusDetail":"Postponed"""")))
    }

    @Test fun resultLine() {
        assertEquals("Chicago Bears win", SportsGameFormat.result(page()))
        val draw = page(""""league":"soccer-eng.1","sport":"soccer","homeTeam":{"id":"1","name":"Burnley","score":"1"},"awayTeam":{"id":"2","name":"Liverpool","score":"1"}""")
        assertEquals("Draw", SportsGameFormat.result(draw))
        val pens = page(
            """"league":"soccer-fifa.world","sport":"soccer","homeTeam":{"id":"1","name":"Argentina","score":"3","shootoutScore":4,"winner":true},
               "awayTeam":{"id":"2","name":"France","score":"3","shootoutScore":2}""",
        )
        assertEquals("Argentina win 4–2 on penalties", SportsGameFormat.result(pens))
        assertNull(SportsGameFormat.result(page(""""status":"live"""")))
    }

    @Test fun pregameCountdown() {
        val scheduled = game(""""status":"scheduled","startTime":"2026-10-23T20:00:00Z"""")
        val start = scheduled.start
        assertNull(SportsGameFormat.pregameCountdown(scheduled, start.minusSeconds(16 * 60)))
        assertEquals("Starts in 12:04", SportsGameFormat.pregameCountdown(scheduled, start.minusSeconds(12 * 60 + 4)))
        assertEquals("Starting soon", SportsGameFormat.pregameCountdown(scheduled, start.plusSeconds(30)))
    }

    @Test fun readsGoTimestamps() {
        assertNotNull(parseInstant("2026-10-23T08:21:38.788755-06:00"))
        assertNotNull("nanoseconds", parseInstant("2026-09-22T19:34:36.018138282-04:00"))
        assertNotNull(parseInstant("2026-10-23T00:15:00Z"))
        assertNull("Go's zero time is none", parseInstant("0001-01-01T00:00:00Z"))
    }

    @Test fun staleness() {
        val updated = parseInstant("2026-10-23T08:21:38.788755-06:00")!!
        val final = page(detail = NFL_DETAIL)
        assertFalse(final.isStale(updated.plusSeconds(300)))
        assertTrue("10 minutes when not live", final.isStale(updated.plusSeconds(700)))
        assertTrue("2 minutes while live", page(""""status":"live"""", NFL_DETAIL).isStale(updated.plusSeconds(150)))
        assertTrue(page(detail = nflDetail(""""stale":true""")).isStale(updated))
        assertEquals("Scores may be delayed", SportsGameFormat.freshness(final, stale = true))
        assertTrue(SportsGameFormat.freshness(final, stale = false).startsWith("Feed checked "))
    }

    @Test fun teamComparisonRules() {
        assertNotNull(SportsTeamStats.numbers("38%", "62"))
        assertNull("records and ratios get no bars", SportsTeamStats.numbers("4-12", "2-7"))
        val soccer = page(
            """"league":"soccer-eng.1","sport":"soccer"""",
            """{"comparisons":[{"label":"Fouls","away":"10","home":"12"},{"label":"Corners","away":"3","home":"5"},
                {"label":"Possession %","away":"40","home":"60"}]}""",
        )
        assertEquals(listOf("Possession %", "Corners", "Fouls"), SportsTeamStats.priorityRows(soccer).map { it.label })
        val rugby = page(""""league":"rugby-180659","sport":"rugby"""")
        assertEquals("fractions read as percent", "56%", SportsTeamStats.display(rugby, "0.56", "Possession"))
    }

    @Test fun matchupColours() {
        assertEquals(
            "near-black gives way to the alternate",
            SportsTeamColors.HexPair(away = "#ffc72c", home = "#ce1141"),
            SportsTeamColors.hexPair(away = "000000", home = "ce1141", awayAlternate = "ffc72c"),
        )
        val pair = SportsTeamColors.hexPair(away = "c8102e", home = "ce1141")
        assertEquals("#c8102e", pair.away)
        assertTrue("two reds become two colours", SportsTeamColors.distinct(pair.away, pair.home))
    }
}

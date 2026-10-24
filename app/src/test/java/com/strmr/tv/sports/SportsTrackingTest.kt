package com.strmr.tv.sports

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import com.strmr.tv.api.ApiJson
import com.strmr.tv.api.LossyList
import com.strmr.tv.sports.SportsSituationFixtures.merge
import com.strmr.tv.ui.sports.game.SportsGamePage
import com.strmr.tv.ui.sports.game.SportsTeamColors
import com.strmr.tv.ui.sports.game.basketball.SportsCourtGeometry
import com.strmr.tv.ui.sports.game.charts.SportsGameClock
import com.strmr.tv.ui.sports.game.charts.SportsLeadSeries
import com.strmr.tv.ui.sports.game.charts.SportsWinProbabilitySeries
import com.strmr.tv.ui.sports.game.charts.chartPair
import com.strmr.tv.ui.sports.game.hockey.SportsHockeyEvent
import com.strmr.tv.ui.sports.game.hockey.SportsHockeyGoalBadge
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Basketball and hockey tracking data (Go `SportsBasketballShot`, `SportsHockeyShot`,
 * `SportsWinProbabilityPoint`, `SportsScoreHistory`), the court geometry (Expo `basketball-court.ts`,
 * `basketball-shot-card.ts`), the charts' series (Expo `basketballElapsed`, `basketballLeadGeometry`)
 * and the basketball and hockey situations (Expo `SportsSituation` kinds `nba` / `nhl`); the tvOS
 * `SportsTrackingTests`.
 */
class SportsTrackingTest {
    init {
        LossyList.report = {}
    }

    private val detail = """"source":"espn","updatedAt":"2026-10-23T08:21:38Z",
        "basketballShots":[
          {"id":"1","teamId":"5","x":29,"y":2,"made":true,"points":2,"period":1,"periodLabel":"Q1","clock":"11:41",
           "description":"Jarrett Allen makes 4-foot two point shot","playerName":"Jarrett Allen","jersey":"31"},
          {"id":"2","teamId":"20","x":17,"y":26,"made":false,"points":3,"period":1,"periodLabel":"Q1","clock":"10:58"},
          {"id":"3","teamId":"5","x":"bad","y":1}],
        "winProbability":[
          {"playId":"a","period":1,"periodLabel":"Q1","clock":"12:00","home":0.708},
          {"playId":"b","period":1,"periodLabel":"Q1","clock":"11:41","home":0.683},
          {"playId":"c","period":4,"periodLabel":"Q4","clock":"0.0","home":1}],
        "scoreHistory":{"eventId":"401909093","awayTeamId":"5","homeTeamId":"20","complete":true,"points":[
          {"id":"1","period":1,"periodLabel":"1st Quarter","clock":"12:00","away":0,"home":0},
          {"id":"2","period":1,"periodLabel":"1st Quarter","clock":"11:41","away":2,"home":0},
          {"id":"3","period":2,"periodLabel":"2nd Quarter","clock":"6:00","away":50,"home":40},
          {"id":"4","period":4,"periodLabel":"4th Quarter","clock":"16.9","away":106,"home":114},
          {"id":"5","period":4,"periodLabel":"4th Quarter","clock":"0.0","away":106,"home":114}]}"""

    /** The NBA final 401909093 cut down: CLE (5) at PHI (20), 106–114. */
    private fun nba(overrides: String = "", detail: String = ""): SportsGamePage {
        val members = merge(
            """"id":"401909093","league":"nba","sport":"basketball","startTime":"2026-10-22T23:00:00Z","status":"final",
            "homeTeam":{"id":"20","name":"Philadelphia 76ers","abbreviation":"PHI","color":"1d428a","alternateColor":"e01234","score":"114"},
            "awayTeam":{"id":"5","name":"Cleveland Cavaliers","abbreviation":"CLE","color":"860038","alternateColor":"bc945c","score":"106"},
            "detail":{${merge(this.detail, detail)}}""",
            overrides,
        )
        return SportsGamePage(ApiJson.decodeFromString(SportsGame.serializer(), "{$members}"), null)
    }

    @Test fun decodesTrackingData() {
        val detail = nba().detail
        assertEquals("a shot without numeric coordinates is skipped", 2, detail.basketballShots.size)
        assertEquals("Jarrett Allen", detail.basketballShots[0].playerName)
        assertEquals("31", detail.basketballShots[0].jersey)
        assertFalse(detail.basketballShots[1].made)
        assertEquals(listOf(0.708, 0.683, 1.0), detail.winProbability.map { it.home })
        assertEquals(5, detail.scoreHistory?.points?.size)
        assertTrue(detail.scoreHistory?.complete == true)
        val hockey = SportsGameFixtures.detail(
            """{"shots":[{"id":"s","teamId":"1","x":58,"y":-31,"kind":"Goal","period":"1st","clock":"0:13"},{"id":"t"}],
               "sport":{"kind":"nhl","strength":" Even strength ","shotsAway":24,"shotsHome":-1}}""",
        )
        assertEquals(1, hockey.shots.size)
        assertTrue("kinds compare in lower case", hockey.shots[0].isGoal)
        assertEquals(SportsHockeySituation("Even strength", 24, null), hockey.hockeySituation)
        assertNull("only a situation of its own kind", hockey.basketballSituation)
        val basketball = SportsGameFixtures.detail("""{"sport":{"kind":"nba","possession":"PHI","bonus":""}}""")
        assertEquals(SportsBasketballSituation("PHI", null), basketball.basketballSituation)
        assertNull(basketball.hockeySituation)
    }

    @Test fun situationsOnlyWhileLive() {
        val sport = """"sport":{"kind":"nba","possession":"cle"}"""
        assertNull("a final has none", nba(detail = sport).game.liveBasketballSituation)
        val live = nba(""""status":"live"""", sport).game
        assertNotNull(live.liveBasketballSituation)
        assertEquals("5", live.side("cle")?.id)
        assertEquals("20", live.side("Philadelphia 76ers")?.id)
        assertNull(live.side(""))
    }

    @Test fun courtGeometry() {
        val home = ApiJson.decodeFromString(SportsTeam.serializer(), """{"id":"2","name":"Boston Celtics","color":"007A33"}""")
        val boston = SportsCourtGeometry.of("nba", home)
        assertEquals(SportsCourtGeometry.Floor.PARQUET, boston.floor)
        assertEquals(80.0, boston.laneHalf, 0.0)
        assertEquals(237.5, boston.threeRadius, 0.0)
        assertEquals("#007a33", boston.paint)
        val women = SportsCourtGeometry.of("womens-college-basketball", SportsTeam(id = "1", name = "A"))
        assertEquals(60.0, women.laneHalf, 0.0)
        assertFalse(women.restricted)
        assertEquals((21 + 7.375 / 12) * 10, women.cornerDistance, 0.001)
        assertEquals("neutral paint without a colour", "#545e65", women.paint)
        assertEquals(SportsCourtGeometry.Floor.WOOD, women.floor)
        val game = nba().game
        val shots = nba().detail.basketballShots
        assertEquals("away attacks the left basket", Offset(112.5f, 330f), SportsCourtGeometry.position(shots[0], game))
        assertEquals("home attacks the right", Offset(667.5f, 370f), SportsCourtGeometry.position(shots[1], game))
        assertNull(SportsCourtGeometry.position(SportsBasketballShot("1", "5", 51.0, 2.0), game))
        assertNull(SportsCourtGeometry.position(SportsBasketballShot("x", "99", 2.0, 2.0), game))
    }

    @Test fun shotCardPlacement() {
        val size = Size(900f, 480f)
        val card = Size(360f, 112f)
        assertEquals("the far corner, low for a high marker", Offset(528f, 356f), SportsCourtGeometry.cardPlacement(size, card, Offset(100f, 100f), 40f, 12f))
        assertEquals(Offset(12f, 12f), SportsCourtGeometry.cardPlacement(size, card, Offset(800f, 400f), 40f, 12f))
        assertNull("a card wider than half the court goes under it", SportsCourtGeometry.cardPlacement(size, Size(440f, 112f), Offset.Zero, 40f, 12f))
    }

    @Test fun gameClock() {
        val nba = SportsGameClock.of("nba")
        assertEquals(0.0, nba.elapsed(1, "12:00")!!, 0.0)
        assertEquals(2880 - 16.9, nba.elapsed(4, "16.9")!!, 0.001)
        assertEquals("overtimes are five minutes", 2880.0, nba.elapsed(5, "5:00")!!, 0.0)
        assertNull(nba.elapsed(1, "13:00"))
        assertNull(nba.elapsed(0, "1:00"))
        assertNull(nba.elapsed(1, "1:75"))
        val college = SportsGameClock.of("mens-college-basketball")
        assertEquals(1200.0, college.elapsed(2, "20:00")!!, 0.0)
        assertEquals("H2", college.label(2))
        assertEquals("OT1", college.label(3))
        assertEquals(600.0, SportsGameClock.of("wnba").seconds, 0.0)
        assertNull(SportsGameClock.basketball("nfl"))
    }

    @Test fun winProbabilitySeries() {
        val series = SportsWinProbabilitySeries.of(nba())!!
        assertEquals(listOf(0.0, 19.0, 2880.0), series.points.map { it.elapsed })
        assertEquals(1.0, series.latest.home, 0.0)
        assertEquals(listOf("Q1", "Q2", "Q3", "Q4"), series.periods.map { it.label })
        val backwards = """"winProbability":[{"period":2,"clock":"1:00","home":0.5},{"period":1,"clock":"1:00","home":0.5}]"""
        assertNull("out of order", SportsWinProbabilitySeries.of(nba(detail = backwards)))
        val range = """"winProbability":[{"period":1,"clock":"1:00","home":0.5},{"period":1,"clock":"0:30","home":1.2}]"""
        assertNull(SportsWinProbabilitySeries.of(nba(detail = range)))
        assertNull(SportsWinProbabilitySeries.of(nba(detail = """"winProbability":[{"period":1,"clock":"1:00","home":0.5}]""")))
    }

    @Test fun leadSeries() {
        val series = SportsLeadSeries.of(nba())!!
        assertEquals("first, last and every change", listOf(0.0, 2.0, 10.0, -8.0, -8.0), series.points.map { it.lead })
        assertEquals(10.0, series.extent, 0.0)
        assertEquals(-8.0, series.latest, 0.0)
        val crossings = series.crossings
        assertEquals("a zero point where the lead changes sides", 6, crossings.size)
        assertEquals(0.0, crossings[3].lead, 0.0)
        assertEquals(listOf(true, false), series.runs.map { it.away })
        assertNull("the history must end on the scoreboard's score",
                   SportsLeadSeries.of(nba(""""homeTeam":{"id":"20","name":"Philadelphia 76ers","score":"115"}""")))
        assertNull("another game's history", SportsLeadSeries.of(nba(""""id":"other"""")))
        assertNull(SportsLeadSeries.of(nba(""""league":"nfl"""")))
        assertNull(SportsLeadSeries.of(nba(""""status":"scheduled"""")))
    }

    @Test fun chartColoursReadOnTheDarkChart() {
        val away = SportsTeam(id = "1", name = "A", color = "0c2340", alternateColor = "a2aaad")
        val home = SportsTeam(id = "2", name = "B", color = "000000", alternateColor = "000000")
        val pair = SportsTeamColors.chartPair(away, home)
        assertEquals("navy is too dark: its alternate", "#a2aaad", pair.away)
        assertNotEquals("#000000", pair.home)
    }

    @Test fun hockeyEventsAndBadges() {
        fun play(title: String, scoring: Boolean = false, shootout: Boolean = false) = SportsPlay(id = title, title = title, scoring = scoring, shootout = shootout)
        assertEquals(SportsHockeyEvent.FACEOFF, SportsHockeyEvent.of(play("Face Off")))
        assertEquals(SportsHockeyEvent.BLOCKED, SportsHockeyEvent.of(play("Blocked")))
        assertEquals(SportsHockeyEvent.GOAL, SportsHockeyEvent.of(play("Goal", true)))
        assertEquals(SportsHockeyEvent.OTHER, SportsHockeyEvent.of(play("Tripping")))
        assertEquals("PP · Power-play goal", SportsHockeyGoalBadge.of(play("Power-Play Goal", true))?.text)
        assertEquals("SH", SportsHockeyGoalBadge.of(play("Shorthanded Goal", true))?.short)
        assertEquals("EN", SportsHockeyGoalBadge.of(play("Empty Net Goal", true))?.short)
        assertNull(SportsHockeyGoalBadge.of(play("Goal", true)))
        assertNull(SportsHockeyGoalBadge.of(play("Power Play Goal", shootout = true)))
    }
}

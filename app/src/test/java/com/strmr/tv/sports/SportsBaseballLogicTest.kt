package com.strmr.tv.sports

import com.strmr.tv.api.ApiJson
import com.strmr.tv.api.LossyList
import com.strmr.tv.sports.SportsSituationFixtures.mlbLive
import com.strmr.tv.sports.SportsSituationFixtures.mlbMembers
import com.strmr.tv.sports.SportsSituationFixtures.nflLive
import com.strmr.tv.ui.sports.game.SportsGamePage
import com.strmr.tv.ui.sports.game.SportsHeaderSituation
import com.strmr.tv.ui.sports.game.baseball.SportsBaseballLineScore
import com.strmr.tv.ui.sports.game.baseball.SportsBaseballState
import com.strmr.tv.ui.sports.game.baseball.SportsCountRow
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * MLB's at-bat and line score (Expo `sports-baseball-state.ts`, `SportsPeriodBreakdown` for MLB)
 * and the scoreboard's live situation for football and baseball (Expo `SportsEditorialScoreboard`,
 * `BaseballSituationLine`), never shown with scores hidden; the tvOS `SportsBaseballLogicTests`.
 */
class SportsBaseballLogicTest {
    init {
        LossyList.report = {}
    }

    /** The live MLB game with its at-bat (scoreboard and detail alike) changed by [situation]. */
    private fun live(situation: String, overrides: String = ""): SportsGamePage {
        val full = ApiJson.parseToJsonElement(SportsSituationFixtures.situation(situation))
        val json = ApiJson.parseToJsonElement("{${mlbMembers(overrides)}}").jsonObject.toMutableMap()
        json["liveSituation"] = full
        json["detail"] = JsonObject(json["detail"]!!.jsonObject + ("sport" to full))
        return SportsGamePage(SportsSituationFixtures.mlbGame(JsonObject(json).toString().removeSurrounding("{", "}")), null)
    }

    @Test fun activeAtBat() {
        val state = SportsBaseballState.of(mlbLive().game)
        assertTrue(state.active)
        assertEquals(7, state.inning)
        assertEquals("the away side bats in the top", "15", state.batting?.id)
        assertEquals("22", state.pitching?.id)
        assertEquals("Count 3–2", state.count)
        assertEquals("2 outs", state.outs)
        assertEquals("2 outs · Count 3–2", state.scoreboardLine(stale = false))
        assertEquals("Last reported · 2 outs · Count 3–2", state.scoreboardLine(stale = true))
        val bottom = SportsBaseballState.of(live(""""inning":"Bot 8th","outs":1""", """"period":"Bot 8th"""").game)
        assertEquals("22", bottom.batting?.id)
        assertEquals("1 out", bottom.outs)
    }

    @Test fun noAtBatBetweenHalfInnings() {
        val middle = SportsBaseballState.of(live(""""inning":"Middle 7th"""", """"statusDetail":"Middle 7th","period":"Middle 7th"""").game)
        assertFalse(middle.active)
        assertEquals("Middle 7th", middle.status)
        assertNull(middle.batting)
        assertNull(middle.count)
        assertNull(middle.scoreboardLine(stale = false))
        val third = SportsBaseballState.of(live(""""outs":3""").game)
        assertFalse(third.active)
        assertEquals("Half-inning complete", third.status)
        val delayed = SportsBaseballState.of(live(""""inning":"Delayed, Top 1st"""", """"statusDetail":"Delayed, Top 1st"""").game)
        assertFalse("a delay isn't live", delayed.active)
        assertNull("no situation is read while delayed", delayed.situation)
        assertEquals("Delayed, Top 1st", delayed.status)
        val change = SportsBaseballState.of(live(""""inning":"Top 7th"""", """"statusDetail":"Pitching Change"""").game)
        assertFalse(change.active)
        assertEquals("Pitching Change", change.status)
        assertFalse(SportsBaseballState.of(mlbLive(""""status":"final","statusDetail":"Final"""").game).active)
    }

    @Test fun oddCountsAreLeftOut() {
        val state = SportsBaseballState.of(live(""""balls":4,"strikes":2,"outs":5""").game)
        assertTrue(state.active)
        assertNull("four balls isn't a count", state.count)
        assertNull(state.outs)
        assertEquals(listOf("Strikes"), SportsCountRow.of(state.situation).map { it.label })
    }

    @Test fun lineScore() {
        val score = SportsBaseballLineScore.of(mlbLive())
        assertEquals("at least nine", 9, score.innings.size)
        assertEquals(SportsBaseballLineScore.Inning(7, "0", "–"), score.innings[6])
        assertEquals(SportsBaseballLineScore.Inning(9, "–", "–"), score.innings[8])
        assertEquals(7, score.current)
        assertEquals(
            listOf(
                SportsBaseballLineScore.Total("R", "0", "0"), SportsBaseballLineScore.Total("H", "2", "3"),
                SportsBaseballLineScore.Total("E", "0", "1"),
            ),
            score.totals,
        )
        val periods = (1..11).joinToString(",") { """{"label":"$it","away":"0","home":"${if (it == 11) 1 else 0}"}""" }
        val json = ApiJson.parseToJsonElement("{${mlbMembers(""""status":"final","statusDetail":"Final","liveSituation":null""")}}").jsonObject.toMutableMap()
        json["detail"] = JsonObject(
            json["detail"]!!.jsonObject + ("periods" to ApiJson.parseToJsonElement("[$periods]")) + ("comparisons" to ApiJson.parseToJsonElement("[]")),
        )
        val extras = SportsBaseballLineScore.of(SportsGamePage(SportsSituationFixtures.mlbGame(JsonObject(json).toString().removeSurrounding("{", "}")), null))
        assertEquals("extra innings", 11, extras.innings.size)
        assertNull(extras.current)
        assertEquals(SportsBaseballLineScore.Total("E", "–", "–"), extras.totals.last())
    }

    @Test fun headerSituation() {
        val football = SportsHeaderSituation.of(nflLive(), spoilers = false)
        assertEquals(listOf("3rd & 4", "Ball at CHI 12"), football.lines)
        assertTrue(football.hasBall(nflLive().game.awayTeam))
        assertFalse(football.hasBall(nflLive().game.homeTeam))
        val baseball = SportsHeaderSituation.of(mlbLive(), spoilers = false)
        assertEquals(listOf("2 outs · Count 3–2"), baseball.lines)
        assertEquals(listOf(true, false, true), baseball.baseball?.situation?.occupiedBases)
        for (page in listOf(nflLive(), mlbLive())) {
            val hidden = SportsHeaderSituation.of(page, spoilers = true)
            assertNull("nothing of the live situation with scores hidden", hidden.football)
            assertNull(hidden.baseball)
            assertNull(hidden.offense)
            assertEquals(emptyList<String>(), hidden.lines)
        }
        assertEquals("not live", emptyList<String>(), SportsHeaderSituation.of(SportsGameFixtures.page(detail = SportsGameFixtures.NFL_DETAIL), false).lines)
        assertEquals(
            "no at-bat under way", emptyList<String>(),
            SportsHeaderSituation.of(live(""""inning":"Middle 7th"""", """"period":"Middle 7th""""), false).lines,
        )
    }
}

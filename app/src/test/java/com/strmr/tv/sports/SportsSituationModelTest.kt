package com.strmr.tv.sports

import com.strmr.tv.api.ApiJson
import com.strmr.tv.api.LossyList
import com.strmr.tv.sports.SportsSituationFixtures.mlbLive
import com.strmr.tv.sports.SportsSituationFixtures.nflLive
import com.strmr.tv.ui.sports.game.SportsGameFormat
import com.strmr.tv.ui.sports.game.football.knownEnd
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * The live situation a game carries (Go `SportsFootballSituation` / `SportsMLBSituation`, Expo
 * `SportsSituation` and `sports-backend-source.ts`; the tvOS `SportsSituationModelTests`):
 * football's possession, down and distance on NFL and college games, MLB's at-bat on the game and
 * in its detail, read only while live.
 */
class SportsSituationModelTest {
    init {
        LossyList.report = {}
    }

    @Test fun decodesTheFootballSituation() {
        val game = nflLive().game
        val situation = game.footballSituation!!
        assertEquals("trimmed", "NE", situation.possession)
        assertEquals("3rd & 4", situation.downDistance)
        assertEquals("CHI 12", situation.fieldPosition)
        assertEquals("Ball at CHI 12", situation.ballAt)
        assertEquals("17", situation.offense(game)?.id)
        assertEquals(0, situation.timeouts(game.awayTeam, game))
        assertEquals(1, situation.timeouts(game.homeTeam, game))
        assertEquals(situation, game.liveFootballSituation)
    }

    @Test fun footballSituationOnlyWhileLiveForFootballLeagues() {
        assertNull(nflLive(""""status":"final","statusDetail":"Final"""").game.liveFootballSituation)
        assertNull(nflLive(""""league":"espn:football:ufl"""").game.liveFootballSituation)
        assertNotNull(nflLive(""""league":"college-football"""").game.liveFootballSituation)
        assertNull("not an object", nflLive(""""footballSituation":"NE ball"""").game.footballSituation)
    }

    @Test fun offenseMatchesIdAbbreviationOrName() {
        val game = nflLive().game
        for (possession in listOf("3", "chi", "Chicago Bears")) {
            assertEquals(possession, "3", SportsFootballSituation(possession = possession).offense(game)?.id)
        }
        assertNull(SportsFootballSituation(possession = "").offense(game))
        assertNull(SportsFootballSituation(possession = "DAL").offense(game))
        assertNull("out of range", SportsFootballSituation(awayTimeouts = 5).timeouts(game.awayTeam, game))
    }

    @Test fun decodesTheBaseballSituationLeniently() {
        val game = mlbLive().game
        assertEquals("Top 7th", game.liveSituation?.inning)
        assertNull("the scoreboard's carries no ids", game.liveSituation?.batterId)
        val detail = game.detail?.sport
        assertEquals("33783", detail?.batterId)
        assertEquals("a number reads as text", "39667", detail?.pitcherId)
        assertEquals(94, detail?.pitcherPitchCount)
        assertEquals("the detail's, with ids, comes first", detail, game.liveBaseballSituation)
        val noDetail = SportsSituationFixtures.mlbGame(SportsSituationFixtures.mlbMembers(""""detail":null"""))
        assertEquals("Ozzie Albies", noDetail.liveBaseballSituation?.batter)
        val odd = ApiJson.decodeFromString(
            SportsGame.serializer(),
            """{"id":"1","league":"mlb","startTime":"2026-10-23T17:05:00Z",
               "liveSituation":{"inning":"Top 1st","balls":"three","bases":"","batter":null}}""",
        ).liveSituation!!
        assertNull(odd.balls)
        assertNull(odd.bases)
        assertNull(odd.batter)
    }

    @Test fun baseballSituationOnlyWhileLive() {
        assertNull(mlbLive(""""status":"final","statusDetail":"Final"""").game.liveBaseballSituation)
        assertNull("a delay isn't live", mlbLive(""""statusDetail":"Delayed, Top 1st"""").game.liveBaseballSituation)
    }

    @Test fun periodLabel() {
        assertEquals("live MLB: the at-bat's half-inning", "Top 7th", mlbLive(""""period":"Top 7"""").game.periodLabel)
        assertEquals("Q4", nflLive().game.periodLabel)
        assertNull(nflLive(""""status":"scheduled","statusDetail":"Scheduled"""").game.periodLabel)
        assertEquals("Top 7th", SportsGameFormat.detailStatus(mlbLive(""""period":"Top 7"""")))
    }

    @Test fun occupiedBases() {
        fun bases(label: String?) = SportsBaseballSituation(bases = label).occupiedBases
        assertEquals(listOf(false, false, false), bases("Bases empty"))
        assertEquals(listOf(true, false, true), bases("Runners on 1st & 3rd"))
        assertEquals(listOf(false, true, false), bases("Runner on 2nd"))
        assertEquals(listOf(true, true, true), bases("On 1st & 2nd & 3rd"))
        assertEquals(listOf(false, true, false), bases("2nd"))
        assertNull("only the plain labels", bases("Bases loaded"))
        assertNull(bases(null))
    }

    @Test fun driveKnownEnds() {
        val detail = SportsGameFixtures.detail(
            """{"footballDrives":[{"id":"1","teamId":"17","start":31,"end":55,"startKnown":true,"endKnown":false},
                                 {"id":"2","teamId":3,"start":"x"}]}""",
        )
        assertEquals(true, detail.footballDrives.first().startKnown)
        assertEquals(false, detail.footballDrives.first().endKnown)
        assertNull("the feed says it couldn't place the end", detail.footballDrives.first().knownEnd)
        assertEquals("3", detail.footballDrives.last().teamId)
        assertNull(detail.footballDrives.last().start)
    }
}

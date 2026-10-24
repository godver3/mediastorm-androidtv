package com.strmr.tv.sports

import com.strmr.tv.api.LossyList
import com.strmr.tv.sports.SportsSituationFixtures.mlbLive
import com.strmr.tv.sports.SportsSituationFixtures.nflLive
import com.strmr.tv.ui.sports.game.SportsGamePage
import com.strmr.tv.ui.sports.game.football.SportsDriveExplorer
import com.strmr.tv.ui.sports.game.football.SportsDriveResult
import com.strmr.tv.ui.sports.game.football.SportsDriveSummary
import com.strmr.tv.ui.sports.game.football.SportsFootballGeometry
import com.strmr.tv.ui.sports.game.football.SportsFootballPause
import com.strmr.tv.ui.sports.game.football.SportsFootballPlayInfo
import com.strmr.tv.ui.sports.game.football.SportsFootballPossession
import com.strmr.tv.ui.sports.game.football.SportsFootballYards
import com.strmr.tv.ui.sports.game.football.SportsRibbonLabel
import com.strmr.tv.ui.sports.game.football.hasReliablePath
import com.strmr.tv.ui.sports.game.football.knownStart
import com.strmr.tv.ui.sports.game.football.statsLine
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * American football's field logic (Expo `football-presentation.ts`, `sports-detail-geometry.ts`,
 * `sports-drive-validity.ts` and the replay's play classification; the tvOS
 * `SportsFootballLogicTests`): spots turned to the offense's own goal, drive paths, summaries and
 * results, the live field's state, how a drive's plays read, and the drive explorer's choice.
 */
class SportsFootballLogicTest {
    init {
        LossyList.report = {}
    }

    private fun drive(
        id: String, team: String = "17", start: Double? = 25.0, end: Double? = 60.0, result: String = "PUNT",
        current: Boolean = false, description: String = "",
    ) = SportsFootballDrive(id, team, "Q4", result, description = description, current = current, start = start, end = end)

    private fun play(
        id: String, type: String = "Rush", typeId: String? = null, down: Int? = 1, distance: Double? = 10.0, yards: Double? = null,
        team: String? = "17", start: Double? = 25.0, end: Double? = 32.0, description: String = "",
    ) = SportsFootballPlay(
        id, type, description, clock = "1:00", period = "Q4", typeId = typeId, down = down, distance = distance, yards = yards,
        possessionTeamId = team, start = start, end = end, scoring = false,
    )

    private val drives = """"footballDrives":[{"id":"9","teamId":"17","start":25,"end":88,"current":true,"result":""}]"""

    @Test fun yards() {
        val game = nflLive().game
        assertEquals(88.0, SportsFootballYards.own(88.0, game.awayTeam, game), 0.0)
        assertEquals(12.0, SportsFootballYards.own(88.0, game.homeTeam, game), 0.0)
        assertEquals("NE 27", SportsFootballYards.label(27.0, game))
        assertEquals("CHI 12", SportsFootballYards.label(88.0, game))
        assertEquals("50", SportsFootballYards.label(50.0, game))
        assertFalse(SportsFootballYards.isValid(101.0))
        assertFalse(SportsFootballYards.isValid(Double.NaN))
        assertFalse(SportsFootballYards.isValid(null))
    }

    @Test fun reliableDrivePath() {
        assertTrue(drive("1").hasReliablePath)
        assertFalse("equal ends aren't movement", drive("2", start = 47.0, end = 47.0).hasReliablePath)
        assertFalse(drive("3", start = null).hasReliablePath)
        assertFalse(drive("4", end = 120.0).hasReliablePath)
        val unknown = drive("5").copy(startKnown = false)
        assertFalse("the feed says it couldn't place the start", unknown.hasReliablePath)
        assertNull(unknown.knownStart)
    }

    @Test fun driveSummaryAndFigures() {
        val summary = SportsDriveSummary.of(" 1 play, -1 yard, 0:37 ")
        assertEquals(SportsDriveSummary(1, -1, "0:37"), summary)
        assertNull(SportsDriveSummary.of("Drive ended on downs"))
        val withFields = drive("1", description = "6 plays, 23 yards, 3:30")
        assertEquals("from the summary", "6 plays · 23 yards · 3:30", withFields.statsLine)
        assertEquals("the feed's own figures first", "7 plays · 1 yard · 4:01", withFields.copy(playCount = 7, yards = 1.0, elapsed = "4:01").statsLine)
        assertNull(drive("2").statsLine)
    }

    @Test fun driveResults() {
        fun result(raw: String) = SportsDriveResult.of(drive("1", result = raw), null).let { "${it.text}|${it.scoring}|${it.current}" }
        assertEquals("Touchdown|true|false", result("TD"))
        assertEquals("Field goal|true|false", result("FG"))
        assertEquals("Safety|true|false", result("Safety"))
        assertEquals("PUNT|false|false", result("PUNT"))
        assertEquals("Result unavailable|false|false", result(" "))
        val live = nflLive(detail = drives)
        val current = SportsDriveResult.of(live.detail.footballDrives[0], SportsFootballPossession.of(live))
        assertEquals(SportsDriveResult("In progress", scoring = false, current = true), current)
        val stale = nflLive(detail = drives, stale = true)
        assertFalse("old data never shows a drive in progress", SportsDriveResult.of(stale.detail.footballDrives[0], SportsFootballPossession.of(stale)).current)
        val other = nflLive(detail = drives.replace(""""teamId":"17"""", """"teamId":"3""""))
        assertFalse("only the side with the ball", SportsDriveResult.of(other.detail.footballDrives[0], SportsFootballPossession.of(other)).current)
        val final = SportsGameFixtures.page(detail = SportsGameFixtures.NFL_DETAIL)
        assertEquals("Touchdown", SportsDriveResult.of(final.detail.footballDrives[1], SportsFootballPossession.of(final)).text)
    }

    @Test fun geometryFromTheSituation() {
        val game = nflLive().game
        fun geometry(possession: String, down: String = "", spot: String) =
            SportsFootballGeometry.of(SportsFootballSituation(possession, down, spot), game)
        val away = geometry("NE", "3rd & 4", "CHI 12")!!
        assertEquals(88.0, away.ball, 0.0)
        assertEquals(1.0, away.direction, 0.0)
        assertEquals(92.0, away.target!!, 0.0)
        val home = geometry("CHI", "1st and 10", "Ball on CHI 25")!!
        assertEquals(75.0, home.ball, 0.0)
        assertEquals("the home side attacks the away goal", 65.0, home.target!!, 0.0)
        assertEquals(100.0, geometry("NE", "1st & Goal", "CHI 5")!!.target!!, 0.0)
        assertEquals(50.0, geometry("NE", spot = "Midfield")!!.ball, 0.0)
        assertNull(geometry("NE", spot = "DAL 20"))
        assertNull(geometry("NE", spot = "CHI 60"))
        assertNull(geometry("XYZ", spot = "CHI 12"))
    }

    @Test fun possessionWhileLive() {
        val plays = """[{"id":"p1","type":"Rush","description":"Run","clock":"0:47","period":"Q4","start":82,"end":86},
                        {"id":"p2","type":"Rush","description":"Run","clock":"0:43","period":"Q4","start":86,"end":88}]"""
        val page = nflLive(detail = """"footballDrives":[{"id":"8","teamId":"3","current":false},
            {"id":"9","teamId":"17","start":25,"end":88,"current":true,"plays":$plays}]""")
        val possession = SportsFootballPossession.of(page)!!
        assertNull(possession.pause)
        assertTrue(possession.isLive)
        assertEquals("17", possession.offense.id)
        assertEquals("3", possession.opponent.id)
        assertEquals(88.0, possession.ball!!, 0.0)
        assertEquals(92.0, possession.target!!, 0.0)
        assertEquals("9", possession.currentDrive?.id)
        assertEquals(listOf("p1", "p2"), possession.plays.map { it.id })
        val home = SportsFootballPossession.of(nflLive(""""footballSituation":{"possession":"CHI","downDistance":"2nd & 6","fieldPosition":"CHI 30"}"""))!!
        assertEquals("turned to the home side's own goal", 30.0, home.ball!!, 0.0)
        assertEquals(36.0, home.target!!, 0.0)
    }

    @Test fun possessionPauses() {
        fun pause(page: SportsGamePage) = SportsFootballPossession.of(page)?.pause
        assertEquals("a final says so, old data or not", "Final", pause(SportsGameFixtures.page(detail = SportsGameFixtures.NFL_DETAIL))?.title)
        assertEquals("Kickoff upcoming", pause(nflLive(""""status":"scheduled","statusDetail":"Scheduled""""))?.title)
        assertEquals(
            SportsFootballPause(SportsFootballPause.Kind.WAITING, "Updates delayed", "Waiting for the latest field position"),
            pause(nflLive(stale = true)),
        )
        val noSpot = SportsFootballPossession.of(nflLive(""""footballSituation":null"""))!!
        assertEquals("Waiting for field position", noSpot.pause?.title)
        assertEquals("the away side, so both end zones still show", "17", noSpot.offense.id)
        assertEquals(
            SportsFootballPause(SportsFootballPause.Kind.HALFTIME, "Halftime"),
            pause(nflLive(""""statusDetail":"Halftime","period":"Q2","clock":"0:00"""")),
        )
        assertEquals("12:00 remaining", pause(nflLive(""""statusDetail":"Halftime · 12:00 remaining""""))?.subtitle)
        assertEquals("Timeout · New England Patriots", pause(nflLive(""""statusDetail":"Timeout by NE""""))?.title)
        assertEquals("no side named", "Timeout", pause(nflLive(""""statusDetail":"Timeout""""))?.title)
        assertEquals("Official timeout", pause(nflLive(""""statusDetail":"Official timeout""""))?.title)
        val timeoutPlay = nflLive(detail = """"plays":[{"id":"t","periodLabel":"Q4","clock":"0:41","title":"Timeout",
            "description":"Timeout #2 by CHI at 00:41."}]""")
        assertEquals("Timeout · Chicago Bears", pause(timeoutPlay)?.title)
        val touchdown = nflLive(detail = """"plays":[{"id":"s","periodLabel":"Q4","clock":"0:41","title":"Passing Touchdown",
            "teamId":"17","scoring":true}]""")
        assertEquals(SportsFootballPause(SportsFootballPause.Kind.SCORE, "Touchdown", "New England Patriots · 6 points"), pause(touchdown))
        assertNull("not a football league", SportsFootballPossession.of(mlbLive()))
    }

    @Test fun playInfo() {
        val game = nflLive().game
        val offense = game.awayTeam
        val pass = SportsFootballPlayInfo.of(play("1", "Pass Reception", "24", 2, 7.0, 14.0, start = 28.0, end = 42.0), offense, game)
        assertEquals(SportsFootballPlayInfo.Kind.PASS, pass.kind)
        assertEquals("2nd & 7", pass.down)
        assertEquals("NE 28", pass.spot)
        assertEquals("14-yard completion · First down", pass.result)
        assertEquals("Pass", pass.type)
        assertEquals("+14 YD", pass.movement)
        val goalToGo = SportsFootballPlayInfo.of(play("2", down = 1, distance = 8.0, yards = 2.0, start = 92.0, end = 94.0), offense, game)
        assertEquals("1st & Goal", goalToGo.down)
        assertEquals("2-yard run", goalToGo.result)
        val homeLoss = SportsFootballPlayInfo.of(play("3", "Sack", down = 3, distance = 9.0, team = "3", start = 70.0, end = 75.0), game.homeTeam, game)
        assertEquals(SportsFootballPlayInfo.Kind.SACK, homeLoss.kind)
        assertEquals("measured toward the home side's goal", "−5 YD", homeLoss.movement)
        assertEquals("Incomplete pass", SportsFootballPlayInfo.of(play("4", "Pass Incompletion", "3"), offense, game).result)
        val kickoff = SportsFootballPlayInfo.of(play("5", "Kickoff", "53", 1, 10.0, start = null, end = null), offense, game)
        assertNull("a kickoff has no down, whatever the feed says", kickoff.down)
        assertEquals("Kickoff", kickoff.result)
        assertEquals("—", kickoff.movement)
        assertEquals(SportsFootballPlayInfo.Kind.PENALTY, SportsFootballPlayInfo.kind(play("6", "Pass Reception", description = "… PENALTY on CHI - No Play.")))
        assertEquals(SportsFootballPlayInfo.Kind.TURNOVER, SportsFootballPlayInfo.kind(play("7", "Fumble Recovery (Opponent)")))
        assertEquals(SportsFootballPlayInfo.Kind.KICK, SportsFootballPlayInfo.kind(play("8", "Field Goal Good")))
        assertFalse(SportsFootballPlayInfo.isFieldPlay(play("9", "Kickoff")))
        assertFalse(SportsFootballPlayInfo.isFieldPlay(play("10", "Official Timeout")))
        assertTrue(SportsFootballPlayInfo.isFieldPlay(play("11", "Penalty")))
    }

    @Test fun ribbonLabelPlacement() {
        assertTrue(SportsRibbonLabel.of(20.0, 60.0, "+40 YD").inside)
        val short = SportsRibbonLabel.of(40.0, 44.0, "+4 YD")
        assertFalse(short.inside)
        assertEquals("beside the tail, toward the offense's own goal", SportsRibbonLabel.Anchor.TRAILING, short.anchor)
        assertTrue("no room behind the tail", SportsRibbonLabel.of(1.0, 4.0, "+3 YD").above)
        assertTrue(SportsRibbonLabel.of(null, 4.0, "—").above)
    }

    @Test fun driveExplorerChoice() {
        val drives = listOf(drive("1"), drive("2", team = "3"), drive("3"), drive("4", team = "3", current = true))
        var explorer = SportsDriveExplorer(drives)
        assertEquals("newest first", listOf("4", "3", "2", "1"), explorer.listed.map { it.id })
        assertEquals("the newest without a choice", "4", explorer.selected?.id)
        explorer = explorer.copy(currentId = "3")
        assertEquals("the drive in progress before the newest", "3", explorer.selected?.id)
        explorer = explorer.copy(chosenId = "2")
        assertEquals("2", explorer.selected?.id)
        explorer = explorer.copy(teamId = "17")
        assertEquals(listOf("3", "1"), explorer.listed.map { it.id })
        assertEquals("the choice isn't listed for this side", "3", explorer.selected?.id)
        explorer = explorer.copy(teamId = null)
        assertEquals("and comes back with both sides", "2", explorer.selected?.id)
        assertEquals("numbered in game order", 3, explorer.number(drives[2]))
        assertNull(explorer.copy(teamId = "99").selected)
    }

    @Test fun driveLanes() {
        val plays = listOf(play("k", "Kickoff", start = 65.0, end = 25.0), play("1"), play("2", start = null), play("t", "Timeout"), play("3", start = 32.0, end = 40.0))
        assertEquals(listOf("1", "3"), SportsDriveExplorer.lanes(plays).map { it.id })
        assertEquals("the latest", listOf("3"), SportsDriveExplorer.lanes(plays, limit = 1).map { it.id })
    }
}

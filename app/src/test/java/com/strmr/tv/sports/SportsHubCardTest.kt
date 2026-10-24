package com.strmr.tv.sports

import com.strmr.tv.api.ApiJson
import com.strmr.tv.api.LossyList
import com.strmr.tv.sports.SportsSituationFixtures.mlbGame
import com.strmr.tv.sports.SportsSituationFixtures.mlbMembers
import com.strmr.tv.sports.SportsSituationFixtures.nflLive
import com.strmr.tv.ui.sports.SportsCardFooter
import com.strmr.tv.ui.sports.SportsFormat
import com.strmr.tv.ui.sports.SportsGlyph
import com.strmr.tv.ui.sports.SportsHubItem
import com.strmr.tv.ui.sports.SportsHubOrdering
import com.strmr.tv.ui.sports.SportsRow
import com.strmr.tv.ui.sports.SportsScoreChange
import com.strmr.tv.ui.sports.countdown
import com.strmr.tv.ui.sports.game.SportsKind
import kotlinx.serialization.json.jsonObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant

/**
 * The hub's cards and rows (Expo `SportsHubShowcase`, `sports-card-status.ts`,
 * `useSportsScoreChange`, `sports-team-order.ts`; the tvOS `SportsHubCardTests`): countdowns, live
 * context lines, the score-change cue, stale leagues and an order that holds across refreshes.
 */
class SportsHubCardTest {
    init {
        LossyList.report = {}
    }

    private fun game(id: String, start: String = "2026-10-23T19:00:00Z", status: String = "final"): SportsHubItem =
        SportsHubItem.Match(SportsGameFixtures.game(""""id":"$id","startTime":"$start","status":"$status","statusDetail":"${if (status == "final") "Final" else ""}""""))

    @Test fun countdown() {
        val start = Instant.ofEpochSecond(100_000)
        fun before(minutes: Double) = start.minusMillis((minutes * 60_000).toLong())
        assertEquals("minutes round up", "Starts in 25 min", SportsFormat.countdown(start, before(24.5)))
        assertEquals("Starts in 2h 5m", SportsFormat.countdown(start, before(125.0)))
        assertEquals("Starts in 2h", SportsFormat.countdown(start, before(120.0)))
        assertEquals("Starts in 1 day", SportsFormat.countdown(start, before(1440.0)))
        assertEquals("Starts in 2 days", SportsFormat.countdown(start, before(1441.0)))
        assertEquals("never live on the clock alone", "Awaiting start", SportsFormat.countdown(start, start))
        assertEquals("Upcoming", SportsFormat.countdown(null, start))
        val scheduled = (game("s", status = "scheduled") as SportsHubItem.Match).game
        val line = SportsFormat.statusLine(scheduled, false, scheduled.start.minusSeconds(25 * 60))
        assertTrue(line, line.endsWith(" · Starts in 25 min"))
    }

    @Test fun baseballFooter() {
        val live = mlbGame(mlbMembers())
        val footer = SportsCardFooter.of(live, SportsKind.BASEBALL, false)
        assertEquals("3–2 count · 2 outs", footer?.text)
        assertEquals(listOf(true, false, true), footer?.bases)
        assertNull("hidden with scores", SportsCardFooter.of(live, SportsKind.BASEBALL, true))
        assertNull(SportsCardFooter.of(mlbGame(mlbMembers(""""statusDetail":"Middle 7th"""")), SportsKind.BASEBALL, false))
    }

    @Test fun footballAndReportedFooters() {
        val football = SportsCardFooter.of(nflLive().game, SportsKind.FOOTBALL, false)
        assertEquals("NE · 3rd & 4 · Ball at CHI 12", football?.text)
        assertEquals(SportsGlyph.FOOTBALL, football?.glyph)
        val hockey = SportsGameFixtures.game(""""league":"nhl","status":"live","statusDetail":"Power play - BOS","period":"P2","clock":"4:00"""")
        assertEquals(SportsCardFooter(SportsGlyph.HOCKEY, text = "Power play - BOS"), SportsCardFooter.of(hockey, SportsKind.HOCKEY, false))
        val clock = SportsGameFixtures.game(""""league":"nba","status":"live","statusDetail":"7:21 - 3rd Quarter"""")
        assertNull("a plain clock isn't context", SportsCardFooter.of(clock, SportsKind.BASKETBALL, false))
        assertNull("not after the game", SportsCardFooter.of(SportsGameFixtures.game(), SportsKind.FOOTBALL, false))
    }

    @Test fun scoreChange() {
        val live = SportsGameFixtures.game(""""status":"live","statusDetail":"Q4"""")
        assertEquals("24|30", SportsScoreChange.key(live, false))
        assertNull(SportsScoreChange.key(live, true))
        assertNull("finals don't light up", SportsScoreChange.key(SportsGameFixtures.game(), false))
        assertTrue(SportsScoreChange.changed("24|30", "24|33"))
        assertFalse("never on the first load", SportsScoreChange.changed(null, "24|30"))
        assertFalse("nor when scores are hidden", SportsScoreChange.changed("24|30", null))
        assertFalse(SportsScoreChange.changed("24|30", "24|30"))
    }

    @Test fun orderHoldsAcrossRefreshes() {
        val ordering = SportsHubOrdering()
        val a = game("b-game")
        val b = game("a-game")
        val c = game("c-game", start = "2026-10-23T18:00:00Z")
        ordering.record(listOf(a, b, c))
        val rank = { _: SportsHubItem -> 0 }
        fun ids(items: List<SportsHubItem>, row: SportsRow, by: (SportsHubItem) -> Int = rank) = ordering.ordered(items, row, by).map { it.key }
        assertEquals("by start, then id", listOf("c-game", "a-game", "b-game"), ids(listOf(a, b, c), SportsRow.LIVE))
        assertEquals("the feed's order doesn't matter", listOf("c-game", "a-game", "b-game"), ids(listOf(c, b, a), SportsRow.LIVE))
        assertEquals("latest first", listOf("b-game", "a-game", "c-game"), ids(listOf(a, b, c), SportsRow.RESULTS))
        // A game whose start moves keeps its place in live and results, and moves among upcoming games.
        val moved = game("c-game", start = "2026-10-23T20:00:00Z")
        ordering.record(listOf(moved))
        assertEquals(listOf("c-game", "a-game", "b-game"), ids(listOf(a, b, moved), SportsRow.LIVE))
        assertEquals(listOf("a-game", "b-game", "c-game"), ids(listOf(a, b, moved), SportsRow.UPCOMING))
        val late = game("0-new", start = "2026-10-23T17:00:00Z")
        ordering.record(listOf(late, a, b, moved))
        assertEquals("a new game goes last", "0-new", ids(listOf(late, a, b, moved), SportsRow.LIVE).last())
        assertEquals("followed competitions lead", "b-game", ids(listOf(a, b, late), SportsRow.LIVE) { if (it.key == "b-game") 0 else 1 }.first())
        ordering.reset()
        ordering.record(listOf(late, a))
        assertEquals("another day starts over", listOf("0-new", "b-game"), ids(listOf(a, late), SportsRow.LIVE))
    }

    @Test fun sideOrder() {
        assertFalse("home first for team sports", SportsKind.ofLeague("nba").awayFirst)
        assertTrue(SportsKind.ofLeague("atp").awayFirst)
        assertTrue(SportsKind.ofLeague("ufc").awayFirst)
        assertTrue(SportsKind.ofLeague("pga").awayFirst)
    }

    @Test fun staleLeagues() {
        val game = """{"id":"1","league":"nhl","startTime":"2026-10-23T19:00:00Z","status":"live","homeTeam":{"id":"h","name":"H"},"awayTeam":{"id":"a","name":"A"}}"""
        fun board(members: String) = readScoreboard(ApiJson, ApiJson.parseToJsonElement("{$members}").jsonObject)
        val split = board(""""games":[$game],"updatedAt":"2026-10-23T19:00:00Z","stale":false,
            "leagues":[{"league":"nhl","stale":true,"unavailable":false},{"league":"nba","stale":false},{"bad":1}]""")
        assertEquals(2, split.leagues.size)
        assertEquals(setOf("nhl"), split.staleLeagues)
        assertEquals("a stale board flags every league on it", setOf("nhl"), board(""""games":[$game],"updatedAt":"2026-10-23T19:00:00Z","stale":true,"leagues":[]""").staleLeagues)
        assertEquals("only a board that says when it was built", emptySet<String>(), board(""""games":[$game],"stale":true""").staleLeagues)
        assertTrue(board(""""games":null""").games.isEmpty())
    }
}

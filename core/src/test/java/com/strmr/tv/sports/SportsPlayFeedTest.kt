package com.strmr.tv.sports

import com.strmr.tv.ui.sports.game.SportsEventKind
import com.strmr.tv.ui.sports.game.SportsKind
import com.strmr.tv.ui.sports.game.SportsPlayFeed
import com.strmr.tv.ui.sports.game.SportsPlayFeed.Filter
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * The Play-by-Play tab's filters and pages (Expo `PlaysPanel`): newest first as sent, 16 to a
 * page; scoring plays, or goals for soccer and hockey by the event's title; soccer cards and
 * substitutions, hockey penalties and shots; one period at a time. The same cases as the tvOS
 * `SportsPlayFeedTests`.
 */
class SportsPlayFeedTest {
    private fun plays(count: Int, period: (Int) -> String = { if (it < 20) "Q4" else "Q3" }) =
        (0 until count).map { SportsPlay(id = "$it", periodLabel = period(it), title = "Play $it", scoring = it % 5 == 0) }

    @Test fun pagesOfSixteenNewestFirst() {
        val feed = SportsPlayFeed(plays(40), SportsKind.FOOTBALL)
        assertEquals(3, feed.pageCount)
        assertEquals("0", feed.pagePlays.first().id)
        assertEquals("Newest first · 1–16 of 40", feed.summary)
        val last = feed.copy(page = 2)
        assertEquals((32 until 40).map { "$it" }, last.pagePlays.map { it.id })
        assertEquals("Newest first · 33–40 of 40", last.summary)
        assertEquals("a page past the end shows the last", 2, feed.copy(page = 9).currentPage)
    }

    @Test fun onePage() {
        assertEquals("Newest first · 5 plays", SportsPlayFeed(plays(5), SportsKind.BASKETBALL).summary)
        assertEquals("Newest first · 5 key events", SportsPlayFeed(plays(5), SportsKind.SOCCER).summary)
        assertEquals(1, SportsPlayFeed(emptyList(), SportsKind.SOCCER).pageCount)
    }

    @Test fun scoringFilter() {
        val feed = SportsPlayFeed(plays(40), SportsKind.FOOTBALL, Filter.SCORING)
        assertEquals(listOf("0", "5", "10", "15", "20", "25", "30", "35"), feed.visible.map { it.id })
        assertEquals("Scoring Plays", feed.label(Filter.SCORING))
        assertEquals("No scoring plays in the loaded feed.", feed.emptyText)
        assertEquals("No plays in the loaded feed.", SportsPlayFeed(emptyList(), SportsKind.FOOTBALL).emptyText)
    }

    @Test fun soccerGoalsAndCardsComeFromTheTitle() {
        val events = listOf(
            SportsPlay(id = "1", periodLabel = "H2", title = "Penalty - Scored"),
            SportsPlay(id = "2", periodLabel = "H2", title = "Yellow Card"),
            SportsPlay(id = "3", periodLabel = "H1", title = "Substitution"),
            SportsPlay(id = "4", periodLabel = "H1", title = "Red Card"),
            SportsPlay(id = "5", periodLabel = "H1", title = "Goal - Header", description = "Goal!", scoring = false),
        )
        val feed = SportsPlayFeed(events, SportsKind.SOCCER)
        assertEquals(listOf(Filter.ALL, Filter.SCORING, Filter.CARDS, Filter.SUBSTITUTIONS), feed.filters)
        assertEquals("All Key Events", feed.label(Filter.ALL))
        assertEquals("Goals", feed.label(Filter.SCORING))
        assertEquals(listOf("1", "5"), feed.copy(filter = Filter.SCORING).visible.map { it.id })
        assertEquals(listOf("2", "4"), feed.copy(filter = Filter.CARDS).visible.map { it.id })
        assertEquals(listOf("3"), feed.copy(filter = Filter.SUBSTITUTIONS).visible.map { it.id })
    }

    @Test fun hockeyPenaltiesAndShots() {
        val events = listOf(
            SportsPlay(id = "1", title = "Shot on Goal"), SportsPlay(id = "2", title = "Minor Penalty"),
            SportsPlay(id = "3", title = "Goal", scoring = true), SportsPlay(id = "4", title = "Faceoff"),
            SportsPlay(id = "5", title = "Blocked Shot"),
        )
        val feed = SportsPlayFeed(events, SportsKind.HOCKEY)
        assertEquals(listOf(Filter.ALL, Filter.SCORING, Filter.PENALTIES, Filter.SHOTS), feed.filters)
        assertEquals(listOf("2"), feed.copy(filter = Filter.PENALTIES).visible.map { it.id })
        assertEquals(listOf("1", "3", "5"), feed.copy(filter = Filter.SHOTS).visible.map { it.id })
        assertEquals(listOf(Filter.ALL, Filter.SCORING), SportsPlayFeed(events, SportsKind.BASKETBALL).filters)
    }

    @Test fun periods() {
        val feed = SportsPlayFeed(plays(30) + SportsPlay(id = "x", title = "No period"), SportsKind.FOOTBALL)
        assertEquals("in feed order, empty labels left out", listOf("Q4", "Q3"), feed.periods)
        val third = feed.copy(period = "Q3")
        assertEquals(10, third.visible.size)
        assertEquals(1, third.pageCount)
        val gone = feed.copy(period = "Q1")
        assertNull("a period the feed no longer has shows them all", gone.activePeriod)
        assertEquals(31, gone.visible.size)
    }

    @Test fun eventKinds() {
        assertEquals(SportsEventKind.GOAL, SportsEventKind.of("Own Goal"))
        assertEquals(SportsEventKind.GOAL, SportsEventKind.of("power_play goal"))
        assertEquals(SportsEventKind.RED_CARD, SportsEventKind.of("Second Yellow Card"))
        assertEquals(SportsEventKind.PENALTY, SportsEventKind.of("Penalty Saved"))
        assertEquals(SportsEventKind.OTHER, SportsEventKind.of("Shot"))
    }
}

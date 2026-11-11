package com.strmr.tv.api

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

class BulkWatchConfirmationTest {
    @Test fun wholeShowAlwaysAsksAndNamesCountAndShow() {
        val p = BulkWatchConfirmation.allEpisodes(true, 11247, "Coronation Street")
        assertEquals("Mark 11,247 episodes watched?", p.title)
        assertEquals("Every aired episode of Coronation Street will be marked watched. Episodes you haven't played won't appear in watch history.", p.message)
        assertEquals("Mark Watched", p.confirm)
        assertEquals("Mark 1 episode watched?", BulkWatchConfirmation.allEpisodes(true, 1, "Show").title)
    }

    @Test fun exceptSeasonAndUnwatchedWording() {
        val p = BulkWatchConfirmation.allEpisodes(false, 300, "Show", exceptSeason = 67)
        assertEquals("Mark 300 episodes unwatched?", p.title)
        assertEquals("This clears the watched state of every episode of Show except Season 67.", p.message)
        assertEquals("Mark Unwatched", p.confirm)
    }

    @Test fun seasonAsksOnlyFromThreshold() {
        assertNull(BulkWatchConfirmation.season(true, BulkWatchConfirmation.SEASON_THRESHOLD - 1, "Show", "Season 1"))
        assertNotNull(BulkWatchConfirmation.season(true, BulkWatchConfirmation.SEASON_THRESHOLD, "Show", "Season 1"))
    }
}

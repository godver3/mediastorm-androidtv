package com.strmr.tv.home

import com.strmr.tv.api.ContinueWatchingItem
import com.strmr.tv.api.EpisodeRef
import java.time.Instant
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** Which Continue Watching items stay in the row, with upcoming shows hidden or shown (7-day window). */
class ContinueWatchingUpcomingTest {
    private val now = Instant.parse("2026-11-18T12:00:00Z").toEpochMilli()
    private val day = 24 * 60 * 60 * 1000L

    private fun show(airsAt: Long?) = ContinueWatchingItem(
        seriesId = "tmdb:tv:1", seriesTitle = "Show",
        nextEpisode = EpisodeRef(1, 2, airDateTimeUTC = airsAt?.let { Instant.ofEpochMilli(it).toString() }),
    )

    private val movie = ContinueWatchingItem(seriesId = "tmdb:movie:1", seriesTitle = "Movie")

    @Test fun windowIsSevenDays() {
        assertEquals(7 * day, COMING_SOON_WINDOW_MS)
    }

    @Test fun airedAndUnknownAlwaysStay() {
        for (exclude in listOf(true, false)) {
            assertTrue(movie.keptInContinueWatching(exclude, now))
            assertTrue(show(now - day).keptInContinueWatching(exclude, now))
            assertTrue(show(null).keptInContinueWatching(exclude, now))
        }
    }

    @Test fun hiddenDropsEveryUpcomingShow() {
        assertFalse(show(now + 60_000).keptInContinueWatching(excludeUpcoming = true, now = now))
        assertFalse(show(now + 3 * day).keptInContinueWatching(excludeUpcoming = true, now = now))
    }

    @Test fun shownKeepsOnlyThoseAiringWithinAWeek() {
        assertTrue(show(now + 60_000).keptInContinueWatching(excludeUpcoming = false, now = now))
        assertTrue(show(now + 7 * day).keptInContinueWatching(excludeUpcoming = false, now = now))
        assertFalse(show(now + 7 * day + 1).keptInContinueWatching(excludeUpcoming = false, now = now))
        assertFalse(show(now + 30 * day).keptInContinueWatching(excludeUpcoming = false, now = now))
    }
}

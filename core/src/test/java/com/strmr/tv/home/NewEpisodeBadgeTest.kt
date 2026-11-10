package com.strmr.tv.home

import java.time.Instant
import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * The Expo "New" badge rule (`hasRecentlyAiredNextEpisode` / `getRecentEpisodeBadge` in
 * features/home/watch-state-cards.ts): aired, and no more than 72 hours ago. The same cases
 * as the tvOS `NewEpisodeBadgeTests`.
 */
class NewEpisodeBadgeTest {
    private val now = Instant.parse("2026-10-21T12:00:00Z").toEpochMilli()

    private fun badge(airDate: String? = null, utc: String? = null, status: String? = null) =
        NewEpisodeBadge.from(airDate, utc, status, now)

    @Test fun exactAirTimeWithinWindow() {
        assertNotNull(badge(utc = "2026-10-21T02:00:00Z"))
        assertNotNull("exactly 72 hours ago still counts", badge(utc = "2026-10-18T12:00:00Z"))
    }

    @Test fun outsideWindowOrNotAired() {
        assertNull("just over 72 hours", badge(utc = "2026-10-18T11:59:59Z"))
        assertNull("not out yet", badge(utc = "2026-10-21T12:00:01Z"))
        assertNull("no air date", badge())
        assertNull(badge("garbage"))
    }

    @Test fun exactTimeWinsOverAirDay() {
        assertNull(badge("2026-10-20", utc = "2026-10-22T01:00:00Z"))
    }

    @Test fun airDayCountsFromLocalMidnight() {
        val day = { offset: Long -> LocalDate.now().plusDays(offset).toString() }
        assertNotNull(NewEpisodeBadge.from(day(0), null, null))
        assertNotNull(NewEpisodeBadge.from(day(-2), null, null))
        assertNull(NewEpisodeBadge.from(day(-4), null, null))
        assertNull(NewEpisodeBadge.from(day(1), null, null))
    }

    @Test fun readyColourFollowsPrequeue() {
        assertEquals(true, badge(utc = "2026-10-21T02:00:00Z", status = "ready")?.ready)
        assertEquals(false, badge(utc = "2026-10-21T02:00:00Z", status = "queued")?.ready)
        assertEquals(false, badge(utc = "2026-10-21T02:00:00Z")?.ready)
    }
}

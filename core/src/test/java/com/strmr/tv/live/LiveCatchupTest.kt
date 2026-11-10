package com.strmr.tv.live

import java.time.Instant
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class LiveCatchupTest {
    private val now = Instant.parse("2026-11-02T22:00:00Z").toEpochMilli()
    private val archived = LiveChannel(id = "a", name = "A", url = "http://h/a.ts", catchup = LiveCatchup(days = 2, mode = "xc"))
    private val plain = LiveChannel(id = "p", name = "P", url = "http://h/p.ts")

    private fun program(start: String, stop: String) = EpgProgram(title = "Show", start = start, stop = stop)

    @Test fun pastProgrammeInTheArchiveReplaysOnSelect() {
        val p = program("2026-11-02T19:00:00Z", "2026-11-02T20:00:00Z")
        assertTrue(LiveCatchups.canReplay(archived, p, now))
        assertTrue(LiveCatchups.replaysOnSelect(archived, p, now))
        assertFalse(LiveCatchups.canReplay(plain, p, now))
    }

    @Test fun airingProgrammeCanStartOverButSelectTunesLive() {
        val p = program("2026-11-02T21:30:00Z", "2026-11-02T22:30:00Z")
        assertTrue(LiveCatchups.canReplay(archived, p, now))
        assertFalse(LiveCatchups.replaysOnSelect(archived, p, now))
    }

    @Test fun futureAndExpiredProgrammesCannotReplay() {
        assertFalse(LiveCatchups.canReplay(archived, program("2026-11-02T23:00:00Z", "2026-11-03T00:00:00Z"), now))
        assertFalse(LiveCatchups.canReplay(archived, program("2026-10-30T19:00:00Z", "2026-10-30T20:00:00Z"), now))
    }

    @Test fun guideHistoryFollowsTheLongestArchive() {
        assertEquals(30 * 60_000L, LiveCatchups.guideHistory(listOf(plain)))
        assertEquals(2 * 86_400_000L, LiveCatchups.guideHistory(listOf(plain, archived)))
        val long = archived.copy(catchup = LiveCatchup(days = 30))
        assertEquals(7 * 86_400_000L, LiveCatchups.guideHistory(listOf(long)))
    }
}

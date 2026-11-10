package com.strmr.tv.live

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class LiveRecordingsTest {
    private fun rec(id: String, status: String, start: String, end: String) =
        Recording(id = id, status = status, channelId = "c1", startAt = start, endAt = end)

    private val program = EpgProgram(title = "News", start = "2026-11-02T19:00:00Z", stop = "2026-11-02T20:00:00Z")

    @Test fun matchesOverlappingActiveRecording() {
        val r = rec("a", "pending", "2026-11-02T18:58:00Z", "2026-11-02T20:00:00Z")
        assertEquals("a", LiveRecordings.matching(listOf(r), program)?.id)
    }

    @Test fun ignoresFinishedAndAdjacentRecordings() {
        val done = rec("a", "completed", "2026-11-02T19:00:00Z", "2026-11-02T20:00:00Z")
        val before = rec("b", "pending", "2026-11-02T18:00:00Z", "2026-11-02T19:00:00Z")
        assertNull(LiveRecordings.matching(listOf(done, before), program))
    }

    @Test fun prefersRunningRecording() {
        val pending = rec("a", "pending", "2026-11-02T19:00:00Z", "2026-11-02T20:00:00Z")
        val running = rec("b", "running", "2026-11-02T19:30:00Z", "2026-11-02T21:30:00Z")
        assertEquals("b", LiveRecordings.matching(listOf(pending, running), program)?.id)
    }

    @Test fun withoutProgramMatchesWhatRecordsNow() {
        val r = rec("a", "running", "2026-11-02T19:00:00Z", "2026-11-02T21:00:00Z")
        val now = java.time.Instant.parse("2026-11-02T20:00:00Z").toEpochMilli()
        assertEquals("a", LiveRecordings.matching(listOf(r), null, now)?.id)
        assertNull(LiveRecordings.matching(listOf(r), null, now + 2 * 3_600_000L))
    }
}

package com.strmr.tv.live

import java.time.ZoneId
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class RecordingRulesTest {
    private fun rec(status: String, title: String = "News at Nine", channel: String = "CNN", path: String? = "/r/a.ts", rule: String? = null) =
        Recording(id = "1", status = status, ruleId = rule, channelName = channel, title = title, outputPath = path,
                  startAt = "2026-10-22T19:00:00Z", endAt = "2026-10-22T20:30:00Z")

    @Test fun filtersMatchExpo() {
        assertTrue(RecordingFilter.UPCOMING.matches(rec("pending")))
        assertTrue(RecordingFilter.RECORDING.matches(rec("starting")))
        assertTrue(RecordingFilter.RECORDING.matches(rec("running")))
        assertTrue(RecordingFilter.COMPLETED.matches(rec("completed")))
        assertTrue(RecordingFilter.HISTORY.matches(rec("failed")))
        assertTrue(RecordingFilter.HISTORY.matches(rec("cancelled")))
        assertFalse(RecordingFilter.COMPLETED.matches(rec("running")))
    }

    @Test fun actions() {
        assertTrue(RecordingRules.canPlay(rec("completed")))
        assertTrue(RecordingRules.canPlay(rec("running")))
        assertFalse(RecordingRules.canPlay(rec("pending")))
        assertFalse(RecordingRules.canPlay(rec("completed", path = null)))
        assertTrue(RecordingRules.canCancel(rec("pending")))
        assertTrue(RecordingRules.canCancel(rec("running")))
        assertFalse(RecordingRules.canCancel(rec("completed")))
        assertFalse(RecordingRules.canDelete(rec("pending", rule = "r1")))
        assertTrue(RecordingRules.canDelete(rec("pending")))
    }

    @Test fun titleFallsBackToFileName() {
        assertEquals("News at Nine", RecordingRules.title(rec("completed")))
        assertEquals("The Late Show", RecordingRules.title(rec("completed", title = "CNN", path = "/r/CNN - 2026-10-22 1900-2000 - The+Late Show.ts")))
        assertEquals("CNN", RecordingRules.title(rec("completed", title = "", path = null)))
    }

    @Test fun formatting() {
        assertEquals("1h 30m", RecordingRules.duration("2026-10-22T19:00:00Z", "2026-10-22T20:30:00Z"))
        assertEquals("30m", RecordingRules.duration("2026-10-22T08:59:01.589732-06:00", "2026-10-22T09:29:01.589732-06:00"))
        assertEquals("2m", RecordingRules.duration("2026-10-22T19:00:00Z", "2026-10-22T19:02:00Z"))
        assertEquals("", RecordingRules.duration("x", "2026-10-22T19:02:00Z"))
        assertEquals("812 MB", RecordingRules.size(812_000_000))
        assertEquals("1.4 GB", RecordingRules.size(1_400_000_000))
        assertNull(RecordingRules.size(0))
        assertTrue(RecordingRules.dateTime("2026-10-22T19:05:00Z", ZoneId.of("UTC")).contains("7:05"))
        assertNull(RecordingRules.visibleError("recording rule paused"))
    }

    @Test fun confirmationsMatchExpo() {
        assertEquals("Stop recording?", RecordingRules.confirmation(rec("running"), delete = false).title)
        assertEquals("Cancel scheduled recording?", RecordingRules.confirmation(rec("pending"), delete = false).title)
        assertEquals("Delete recording?", RecordingRules.confirmation(rec("completed"), delete = true).title)
    }
}

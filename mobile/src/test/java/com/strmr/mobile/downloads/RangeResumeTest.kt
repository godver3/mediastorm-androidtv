package com.strmr.mobile.downloads

import com.strmr.mobile.downloads.RangeResume.Decision
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class RangeResumeTest {
    @Test fun header() {
        assertNull(RangeResume.header(0))
        assertEquals("bytes=1024-", RangeResume.header(1024))
    }

    @Test fun parsesContentRange() {
        assertEquals(Triple(100L, 999L, 1000L), RangeResume.parseContentRange("bytes 100-999/1000"))
        assertEquals(Triple(100L, 999L, null), RangeResume.parseContentRange("bytes 100-999/*"))
        assertEquals(Triple(-1L, -1L, 1000L), RangeResume.parseContentRange("bytes */1000"))
        assertNull(RangeResume.parseContentRange("garbage"))
    }

    @Test fun partialContentAtTheOffsetAppends() {
        assertEquals(Decision.Append(500, 1000), RangeResume.decide(500, 206, "bytes 500-999/1000", 500))
        // No total: offset plus the body.
        assertEquals(Decision.Append(500, 1000), RangeResume.decide(500, 206, "bytes 500-999/*", 500))
    }

    @Test fun wholeFileRestarts() {
        assertEquals(Decision.Restart(1000), RangeResume.decide(500, 200, null, 1000))
        assertEquals(Decision.Restart(1000), RangeResume.decide(0, 200, null, 1000))
        assertEquals(Decision.Restart(null), RangeResume.decide(0, 200, null, -1))
    }

    @Test fun misalignedRangeDiscards() {
        assertEquals(Decision.Discard, RangeResume.decide(500, 206, "bytes 400-999/1000", 600))
        assertEquals(Decision.Restart(1000), RangeResume.decide(500, 206, "bytes 0-999/1000", 1000))
    }

    @Test fun unsatisfiableRange() {
        assertEquals(Decision.Complete, RangeResume.decide(1000, 416, "bytes */1000", 0))
        assertEquals(Decision.Discard, RangeResume.decide(1200, 416, "bytes */1000", 0))
    }

    @Test fun errors() {
        assertEquals(Decision.Fail(404), RangeResume.decide(0, 404, null, 0))
        assertEquals(true, RangeResume.retryable(503))
        assertEquals(true, RangeResume.retryable(429))
        assertEquals(false, RangeResume.retryable(404))
    }
}

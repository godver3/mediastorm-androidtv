package com.strmr.tv.api

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** Episode card and details header runtimes. */
class FormatRuntimeTest {
    @Test fun formatsMinutesAndHours() {
        assertEquals("49m", formatRuntime(49))
        assertEquals("1h", formatRuntime(60))
        assertEquals("1h 5m", formatRuntime(65))
        assertNull(formatRuntime(0))
        assertNull(formatRuntime(null))
    }
}

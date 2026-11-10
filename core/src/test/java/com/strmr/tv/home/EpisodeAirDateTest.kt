package com.strmr.tv.home

import java.util.Locale
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class EpisodeAirDateTest {
    @Test fun formatsLikeExpo() {
        assertEquals("Oct 20, 2026", ReleaseBadge.airDateLabel("2026-10-20", Locale.US))
        assertEquals("Jan 3, 2031", ReleaseBadge.airDateLabel("2031-01-03", Locale.US))
        // A timestamp keeps only its date part, as Expo appends T00:00:00 to the date.
        assertEquals("Mar 9, 2024", ReleaseBadge.airDateLabel("2024-03-09T01:00:00Z", Locale.US))
    }

    @Test fun missingOrBadDatesShowNothing() {
        assertNull(ReleaseBadge.airDateLabel(null, Locale.US))
        assertNull(ReleaseBadge.airDateLabel("", Locale.US))
        assertNull(ReleaseBadge.airDateLabel("2024-13-40", Locale.US))
        assertNull(ReleaseBadge.airDateLabel("soon", Locale.US))
    }
}

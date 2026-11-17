package com.strmr.tv.ui.details

import com.strmr.tv.api.DetailsInfoSettings
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** The details page's optional FILENAME · RUNTIME · TRACKS line. */
class DetailsInfoLineTest {
    private val all = DetailsInfoSettings(showFilename = true, showRuntime = true, showTracks = true)
    private val tracks = "English · EAC3 · English Subtitles"

    @Test fun hiddenWhenAllOff() {
        assertNull(DetailsInfoLine.build(DetailsInfoSettings(), false, "Movie.2020.mkv", "1h 52m", tracks))
    }

    @Test fun joinsEnabledSegments() {
        assertEquals(DetailsInfoLine(false, "Movie.2020.mkv", "1h 52m · $tracks"), DetailsInfoLine.build(all, false, "Movie.2020.mkv", "1h 52m", tracks))
        assertEquals(DetailsInfoLine(false, null, "48m"), DetailsInfoLine.build(DetailsInfoSettings(showRuntime = true), false, "x", "48m", tracks))
        assertEquals(DetailsInfoLine(false, null, tracks), DetailsInfoLine.build(DetailsInfoSettings(showTracks = true), false, "x", "48m", tracks))
    }

    @Test fun resolvingShowsFindingInsteadOfFilenameAndHidesTracks() {
        assertEquals(DetailsInfoLine(true, null, "1h"), DetailsInfoLine.build(all, true, null, "1h", null))
        // Tracks alone while resolving: nothing to show yet.
        assertNull(DetailsInfoLine.build(DetailsInfoSettings(showTracks = true), true, null, "1h", null))
    }

    @Test fun hiddenWhenEnabledSegmentsAreEmpty() {
        // Prequeue unavailable / library copy: no filename or tracks, and no runtime known.
        assertNull(DetailsInfoLine.build(all, false, null, null, null))
        assertEquals(DetailsInfoLine(false, null, "2h"), DetailsInfoLine.build(all, false, null, "2h", null))
    }
}

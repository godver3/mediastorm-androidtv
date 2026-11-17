package com.strmr.tv.ui.details

import com.strmr.tv.api.DetailsInfoSettings
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** The details page's optional filename and tracks lines. */
class DetailsInfoLineTest {
    private val all = DetailsInfoSettings(showFilename = true, showTracks = true)
    private val tracks = SelectedTracks("English · EAC3", "English")

    @Test fun hiddenWhenAllOff() {
        assertNull(DetailsInfoLine.build(DetailsInfoSettings(), false, "Movie.2020.mkv", tracks))
    }

    @Test fun keepsEnabledParts() {
        assertEquals(DetailsInfoLine(false, "Movie.2020.mkv", tracks), DetailsInfoLine.build(all, false, "Movie.2020.mkv", tracks))
        assertEquals(DetailsInfoLine(false, "x", null), DetailsInfoLine.build(DetailsInfoSettings(showFilename = true), false, "x", tracks))
        assertEquals(DetailsInfoLine(false, null, tracks), DetailsInfoLine.build(DetailsInfoSettings(showTracks = true), false, "x", tracks))
    }

    @Test fun resolvingShowsFindingInsteadOfFilenameAndHidesTracks() {
        assertEquals(DetailsInfoLine(true, null, null), DetailsInfoLine.build(all, true, null, null))
        // Tracks alone while resolving: nothing to show yet.
        assertNull(DetailsInfoLine.build(DetailsInfoSettings(showTracks = true), true, null, null))
    }

    @Test fun hiddenWhenEnabledPartsAreEmpty() {
        // Prequeue unavailable / library copy: no filename or tracks.
        assertNull(DetailsInfoLine.build(all, false, null, null))
    }
}

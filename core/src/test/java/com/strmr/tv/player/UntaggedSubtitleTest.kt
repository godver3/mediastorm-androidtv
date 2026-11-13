package com.strmr.tv.player

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** Subtitles on with no track in the preferred language: an untagged track is picked, as the server. */
class UntaggedSubtitleTest {
    private fun sub(language: String?, title: String = "", forced: Boolean = false) =
        ClientTrackSelector.Subtitle(language, title, "subrip", forced)

    @Test fun untaggedTrackIsTheFallback() {
        for (language in listOf(null, "", "und", "unknown", "UNK")) {
            val tracks = listOf(sub(language, "Signs & Songs"), sub(language), sub("spa"))
            assertEquals("language $language", 1, ClientTrackSelector.findSubtitle(tracks, "eng", "on", "eng"))
        }
    }

    @Test fun taggedMatchBeatsUntagged() {
        assertEquals(1, ClientTrackSelector.findSubtitle(listOf(sub("und"), sub("eng")), "eng", "on", "eng"))
        assertEquals(1, ClientTrackSelector.findSubtitle(listOf(sub("und"), sub("eng", forced = true)), "eng", "on", "eng"))
    }

    @Test fun forcedOnlyUsesOnlyUntaggedForcedTracks() {
        assertEquals(1, ClientTrackSelector.findSubtitle(listOf(sub("und"), sub("und", forced = true)), "eng", "forced-only", "eng"))
        assertEquals(-1, ClientTrackSelector.findSubtitle(listOf(sub("und")), "eng", "forced-only", "eng"))
    }

    @Test fun untaggedSpellingsAreOneLanguage() {
        assertTrue(TrackPreferences.sameLanguage(null, "und"))
        assertTrue(TrackPreferences.sameLanguage("", "unknown"))
        assertFalse(TrackPreferences.sameLanguage(null, "eng"))
        assertTrue(TrackPreferences.sameLanguage("fre", "fr"))
    }
}

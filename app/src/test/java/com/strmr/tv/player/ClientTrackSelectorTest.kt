package com.strmr.tv.player

import com.strmr.tv.api.PlaybackSettings
import com.strmr.tv.api.TrackSelection
import com.strmr.tv.player.ClientTrackSelector.Audio
import com.strmr.tv.player.ClientTrackSelector.Subtitle
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ClientTrackSelectorTest {
    @Test fun languageMatching() {
        assertTrue(ClientTrackSelector.matchesLanguage("en", "", "eng"))
        assertTrue(ClientTrackSelector.matchesLanguage("ger", "", "deu"))
        assertTrue(ClientTrackSelector.matchesLanguage("und", "Polish 5.1", "pol"))
        assertTrue(ClientTrackSelector.matchesLanguage(null, "English SDH", "english"))
        assertFalse(ClientTrackSelector.matchesLanguage("fre", "", "eng"))
        assertFalse(ClientTrackSelector.matchesLanguage("und", "Engineering", "eng"))
    }

    @Test fun audioPrefersEac3ThenCompatibleAvoidingTrueHdAndCommentary() {
        val tracks = listOf(
            Audio("eng", "", "truehd"),
            Audio("eng", "Director's Commentary", "eac3"),
            Audio("eng", "", "dts"),
            Audio("eng", "", "ac3"),
            Audio("fra", "", "eac3"),
        )
        assertEquals(3, ClientTrackSelector.findAudio(tracks, "eng"))
        assertEquals(4, ClientTrackSelector.findAudio(tracks, "fre"))
        assertEquals(0, ClientTrackSelector.findAudio(listOf(Audio("eng", "", "truehd"), Audio("eng", "Commentary", "aac")), "eng"))
        assertEquals(-1, ClientTrackSelector.findAudio(tracks, "jpn"))
        assertEquals(-1, ClientTrackSelector.findAudio(tracks, null))
    }

    @Test fun allowedAudioFallsBackToFirstAllowedLanguage() {
        val tracks = listOf(Audio("jpn", "", "aac"), Audio("eng", "", "aac"))
        assertEquals(1, ClientTrackSelector.findAllowedAudio(tracks, listOf("eng", "jpn"), "eng"))
        // French isn't allowed: the first allowed language present.
        assertEquals(1, ClientTrackSelector.findAllowedAudio(tracks, listOf("eng", "jpn"), "fra"))
        assertEquals(0, ClientTrackSelector.findAllowedAudio(tracks, listOf("jpn", "eng"), "spa"))
        assertEquals(0, ClientTrackSelector.findAllowedAudio(tracks, emptyList(), "ja"))
    }

    @Test fun subtitlesByMode() {
        val tracks = listOf(
            Subtitle("eng", "Forced", "subrip", forced = false),
            Subtitle("eng", "Signs & Songs", "ass", forced = false),
            Subtitle("eng", "", "subrip", forced = false),
            Subtitle("eng", "SDH", "hdmv_pgs_subtitle", forced = false),
            Subtitle("fra", "", "subrip", forced = true),
            Subtitle("spa", "", "dvd_subtitle", forced = false),
        )
        assertEquals(3, ClientTrackSelector.findSubtitle(tracks, "eng", "on", "eng"))
        assertEquals(0, ClientTrackSelector.findSubtitle(tracks, "eng", "forced-only", "eng"))
        assertEquals(4, ClientTrackSelector.findSubtitle(tracks, "fra", "on", "eng")) // only a forced one
        assertEquals(-1, ClientTrackSelector.findSubtitle(tracks, "spa", "on", "eng")) // VobSub isn't selectable
        assertEquals(-1, ClientTrackSelector.findSubtitle(tracks, "eng", "off", "eng"))
        // Without SDH: plain beats signs.
        assertEquals(2, ClientTrackSelector.findSubtitle(tracks.take(3), "eng", "on", "jpn"))
        // Same-language audio prefers a dubtitle over plain; foreign audio prefers plain.
        val dub = listOf(Subtitle("eng", "", "subrip", false), Subtitle("eng", "Dubtitle", "subrip", false))
        assertEquals(1, ClientTrackSelector.findSubtitle(dub, "eng", "on", "eng"))
        assertEquals(0, ClientTrackSelector.findSubtitle(dub, "eng", "on", "jpn"))
    }

    @Test fun effectiveTracksOverrideOnlyWhenDifferent() {
        val sel = TrackSelection(audioLanguage = "eng", subtitleLanguage = "eng", subtitleMode = "on")
        val same = EffectiveTracks.resolve(sel, null, "en", "eng", "on")
        assertFalse(same.clientAudio)
        assertFalse(same.clientSubtitles)
        val none = EffectiveTracks.resolve(sel, null, null, null, null)
        assertEquals("eng", none.subtitleLanguage)
        assertFalse(none.clientSubtitles)
        val mode = EffectiveTracks.resolve(sel, null, null, null, "forced-only")
        assertTrue(mode.clientSubtitles)
        assertFalse(mode.clientAudio)
        assertEquals("forced-only", mode.subtitleMode)
        val audio = EffectiveTracks.resolve(sel, PlaybackSettings(allowedTrackLanguages = listOf("eng", "jpn")), "jpn", null, null)
        assertTrue(audio.clientAudio)
        assertTrue(audio.clientSubtitles) // subtitle priority follows the audio language
        assertEquals(listOf("eng", "jpn"), audio.allowedLanguages)
        // No server choice: the profile's settings are the default.
        val profile = EffectiveTracks.resolve(null, PlaybackSettings(preferredSubtitleLanguage = "fra", preferredSubtitleMode = "on"), null, null, null)
        assertEquals("fra", profile.subtitleLanguage)
        assertFalse(profile.clientSubtitles)
    }
}

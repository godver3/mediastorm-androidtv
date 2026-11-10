package com.strmr.tv.player

import com.strmr.tv.api.PlaybackSettings
import com.strmr.tv.api.ProbedAudioTrack
import com.strmr.tv.player.ClientTrackSelector.Audio
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class RegionalAudioTest {
    // Spider-Man.Brand.New.Day.2026.MULTi.VF2.2160p.WEB.H265-SUPPLY.
    private val spiderMan = listOf(
        Audio("fre", "French (France)", "eac3"),
        Audio("fre", "French (France) AD", "aac"),
        Audio("fre", "French (Canadien)", "eac3"),
        Audio("eng", "English", "eac3"),
    )
    private val terminalList = listOf(Audio("eng", "VO", "eac3"), Audio("fre", "VFF", "eac3"), Audio("fre", "VFQ", "eac3"))

    @Test fun regionalPreferencePicksTheDub() {
        assertEquals(1, ClientTrackSelector.findAudio(terminalList, "fre"))
        assertEquals(2, ClientTrackSelector.findAudio(terminalList, "fr-CA"))
        assertEquals(1, ClientTrackSelector.findAudio(terminalList, "fr-FR"))
        assertEquals(2, ClientTrackSelector.findAudio(spiderMan, "fr-CA"))
        assertEquals(0, ClientTrackSelector.findAudio(spiderMan, "fr-FR"))
        assertEquals(1, ClientTrackSelector.findAudio(listOf(Audio("fre", "VFF", "eac3"), Audio("fre", "FR-CA 5.1", "eac3")), "fr-CA"))
        assertEquals(1, ClientTrackSelector.findAudio(listOf(Audio("spa", "Castellano", "ac3"), Audio("spa", "Latino", "ac3")), "es-419"))
        assertEquals(1, ClientTrackSelector.findAudio(listOf(Audio("por", "Portugal", "aac"), Audio("por", "Brazilian", "aac")), "pt-BR"))
    }

    @Test fun regionalPreferenceFallsBackToTheLanguage() {
        assertEquals(1, ClientTrackSelector.findAudio(listOf(Audio("eng", "", "eac3"), Audio("fre", "VFF", "eac3")), "fr-CA"))
        // A regional commentary loses to the language's main dub.
        assertEquals(0, ClientTrackSelector.findAudio(listOf(Audio("fre", "VFF", "eac3"), Audio("fre", "VFQ Commentary", "eac3")), "fr-CA"))
    }

    @Test fun audioDescriptionRanksWithCommentary() {
        assertEquals(0, ClientTrackSelector.findAudio(listOf(Audio("fre", "French (France)", "truehd"), Audio("fre", "French (France) AD", "aac")), "fre"))
        assertTrue(ClientTrackSelector.isCommentary("Audio Description"))
        assertFalse(ClientTrackSelector.isCommentary("Adaptive 5.1"))
    }

    @Test fun manualPanelPreselectsTheDub() {
        val tracks = listOf(ProbedAudioTrack(1, "eng", "eac3", title = "VO"), ProbedAudioTrack(2, "fre", "eac3", title = "VFF"),
                            ProbedAudioTrack(3, "fre", "eac3", title = "VFQ"))
        assertEquals(3, ManualTracks.defaultAudio(tracks, "fr-ca"))
        assertEquals(2, ManualTracks.defaultAudio(tracks, "fre"))
        assertEquals(2, ManualTracks.defaultAudio(tracks.take(2), "fr-CA"))
    }

    @Test fun regionalOverrideDiffersFromPlainServerLanguage() {
        val playback = PlaybackSettings(preferredAudioLanguage = "fre")
        assertTrue(EffectiveTracks.resolve(null, playback, "fr-CA", null, null).clientAudio)
        assertFalse(EffectiveTracks.resolve(null, PlaybackSettings(preferredAudioLanguage = "fr-ca"), "fr-CA", null, null).clientAudio)
        assertTrue(EffectiveTracks.resolve(null, PlaybackSettings(preferredAudioLanguage = "fr-ca"), "fra", null, null).clientAudio)
        assertFalse(EffectiveTracks.resolve(null, playback, "fra", null, null).clientAudio)
    }

    @Test fun displayNames() {
        assertEquals("French (Canada)", SubtitleLanguages.displayName("fr-ca"))
        assertEquals("French", SubtitleLanguages.displayName("fre"))
    }
}

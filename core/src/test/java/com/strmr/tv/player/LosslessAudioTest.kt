package com.strmr.tv.player

import com.strmr.tv.api.PlaybackSettings
import com.strmr.tv.api.ProbedAudioTrack
import com.strmr.tv.player.ClientTrackSelector.Audio
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** Settings › Playback › Audio Format: Lossless puts TrueHD, then DTS / FLAC / PCM, first. */
class LosslessAudioTest {
    private val remux = listOf(
        Audio("eng", "English DD+ Atmos", "eac3"),
        Audio("eng", "English TrueHD Atmos 7.1", "truehd"),
        Audio("eng", "Commentary", "ac3"),
        Audio("fra", "", "eac3"),
        Audio("fra", "French DTS-HD MA", "dts"),
    )

    @Test fun compatibleKeepsTheServerOrder() {
        assertEquals(0, ClientTrackSelector.findAudio(remux, "eng"))
        assertEquals(3, ClientTrackSelector.findAudio(remux, "fra"))
    }

    @Test fun losslessPrefersTrueHdThenDts() {
        assertEquals(1, ClientTrackSelector.findAudio(remux, "eng", lossless = true))
        assertEquals(4, ClientTrackSelector.findAudio(remux, "fra", lossless = true))
        assertEquals(4, ClientTrackSelector.findAllowedAudio(remux, listOf("fra", "eng"), null, lossless = true))
    }

    @Test fun losslessFallsBackAndSkipsCommentary() {
        assertEquals(1, ClientTrackSelector.findAudio(listOf(Audio("eng", "", "aac"), Audio("eng", "", "eac3")), "eng", lossless = true))
        val commentary = listOf(Audio("eng", "", "eac3"), Audio("eng", "Director's Commentary", "truehd"))
        assertEquals(0, ClientTrackSelector.findAudio(commentary, "eng", lossless = true))
    }

    @Test fun losslessPicksAudioOnTheDeviceButLeavesSubtitles() {
        val playback = PlaybackSettings(preferredAudioLanguage = "eng")
        val e = EffectiveTracks.resolve(null, playback, null, null, null, losslessAudio = true)
        assertTrue(e.clientAudio)
        assertFalse(e.clientSubtitles)
        assertTrue(e.serverAudioFallback)
        assertFalse(EffectiveTracks.resolve(null, playback, "fra", null, null, losslessAudio = true).serverAudioFallback)
        assertFalse(EffectiveTracks.resolve(null, playback, null, null, null).clientAudio)
    }

    @Test fun sourceListDefaultsFollowThePreference() {
        val tracks = listOf(ProbedAudioTrack(1, "eng", "eac3"), ProbedAudioTrack(2, "eng", "truehd", title = "TrueHD Atmos 7.1"))
        assertEquals(1, ManualTracks.defaultAudio(tracks, "eng"))
        assertEquals(2, ManualTracks.defaultAudio(tracks, "eng", lossless = true))
    }
}

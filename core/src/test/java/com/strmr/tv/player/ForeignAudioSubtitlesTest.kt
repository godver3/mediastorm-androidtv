package com.strmr.tv.player

import com.strmr.tv.api.PlaybackSettings
import com.strmr.tv.api.ProbedSubtitleTrack
import com.strmr.tv.api.TrackSelection
import com.strmr.tv.player.ClientTrackSelector.FOREIGN_AUDIO
import com.strmr.tv.player.ClientTrackSelector.Subtitle
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Subtitles › Foreign Audio, as the server's `trackselect.ResolveForeignAudioMode`. */
class ForeignAudioSubtitlesTest {
    private val subtitles = listOf(
        Subtitle("eng", "Forced", "subrip", forced = true),
        Subtitle("eng", "", "subrip", forced = false),
        Subtitle("spa", "", "subrip", forced = false),
    )

    @Test fun resolvesFromTheAudioLanguage() {
        assertEquals("forced-only", ClientTrackSelector.resolveMode(FOREIGN_AUDIO, "en", "eng"))
        assertEquals("on", ClientTrackSelector.resolveMode(FOREIGN_AUDIO, "jpn", "eng"))
        assertEquals("on", ClientTrackSelector.resolveMode(FOREIGN_AUDIO, "fre", null, "eng"))
        assertEquals("forced-only", ClientTrackSelector.resolveMode(FOREIGN_AUDIO, null, "eng"))
        assertEquals("forced-only", ClientTrackSelector.resolveMode(FOREIGN_AUDIO, "und", "eng"))
        assertEquals("forced-only", ClientTrackSelector.resolveMode(FOREIGN_AUDIO, "jpn", null))
        assertEquals("on", ClientTrackSelector.resolveMode("on", "eng", "eng"))
        assertNull(ClientTrackSelector.resolveMode(null, "jpn", "eng"))
    }

    @Test fun picksForcedForOwnAudioAndFullForForeignAudio() {
        assertEquals(0, ClientTrackSelector.findSubtitle(subtitles, "eng", FOREIGN_AUDIO, "eng"))
        assertEquals(1, ClientTrackSelector.findSubtitle(subtitles, "eng", FOREIGN_AUDIO, "jpn"))
        assertEquals(-1, ClientTrackSelector.findSubtitle(subtitles.drop(1), "eng", FOREIGN_AUDIO, "eng"))
    }

    @Test fun sourcePanelDefaultFollowsTheAudio() {
        val probed = listOf(
            ProbedSubtitleTrack(index = 3, language = "eng", title = "Forced", forced = true),
            ProbedSubtitleTrack(index = 4, language = "eng"),
        )
        assertEquals(3, ManualTracks.defaultSubtitle(probed, "eng", FOREIGN_AUDIO, "eng"))
        assertEquals(4, ManualTracks.defaultSubtitle(probed, "eng", FOREIGN_AUDIO, "jpn"))
    }

    @Test fun serverPreferenceIsReResolvedOnDevice() {
        val selection = TrackSelection(audioLanguage = "eng", subtitleLanguage = "eng", subtitleMode = "forced-only",
                                       subtitleModePreference = FOREIGN_AUDIO)
        val same = EffectiveTracks.resolve(selection, PlaybackSettings(), null, null, null)
        assertEquals(FOREIGN_AUDIO, same.subtitleMode)
        assertFalse(same.clientSubtitles)
        // A Japanese audio override re-picks the subtitles, which foreign-audio turns on.
        val japanese = EffectiveTracks.resolve(selection, PlaybackSettings(), "jpn", null, null)
        assertTrue(japanese.clientSubtitles)
        assertEquals(FOREIGN_AUDIO, japanese.subtitleMode)
    }
}

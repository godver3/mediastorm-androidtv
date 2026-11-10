package com.strmr.tv.player

import com.strmr.tv.api.ProbedAudioTrack
import com.strmr.tv.api.ProbedSubtitleTrack
import com.strmr.tv.api.SelectedTrack
import com.strmr.tv.api.TrackSelection
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ManualTracksTest {
    private val audio = listOf(
        ProbedAudioTrack(1, "eng", "truehd", title = "TrueHD Atmos 7.1"),
        ProbedAudioTrack(2, "eng", "eac3", title = "Commentary"),
        ProbedAudioTrack(3, "eng", "ac3"),
        ProbedAudioTrack(4, "fre", "ac3"),
    )
    private val subs = listOf(
        ProbedSubtitleTrack(5, "eng", "subrip", title = "Forced", forced = true),
        ProbedSubtitleTrack(6, "eng", "subrip"),
        ProbedSubtitleTrack(7, "eng", "hdmv_pgs_subtitle", title = "SDH", isBitmap = true),
        ProbedSubtitleTrack(8, "fre", "dvd_subtitle"),
    )

    @Test fun audioDefaultSkipsCommentaryAndTrueHd() {
        assertEquals(3, ManualTracks.defaultAudio(audio, "eng"))
        assertEquals(4, ManualTracks.defaultAudio(audio, "fre"))
        // No preference, or no match: the first compatible non-commentary track.
        assertEquals(3, ManualTracks.defaultAudio(audio, null))
        assertEquals(3, ManualTracks.defaultAudio(audio, "jpn"))
        assertEquals(1, ManualTracks.defaultAudio(listOf(audio[0]), "eng"))
        assertNull(ManualTracks.defaultAudio(emptyList(), "eng"))
    }

    @Test fun subtitleDefaultFollowsMode() {
        assertNull(ManualTracks.defaultSubtitle(subs, "eng", null))
        assertNull(ManualTracks.defaultSubtitle(subs, "eng", "off"))
        assertEquals(5, ManualTracks.defaultSubtitle(subs, "eng", "forced-only"))
        assertEquals(7, ManualTracks.defaultSubtitle(subs, "eng", "on")) // SDH first
        assertEquals(6, ManualTracks.defaultSubtitle(subs.filter { it.index != 7 }, "eng", "on"))
        assertEquals(5, ManualTracks.defaultSubtitle(listOf(subs[0]), "eng", "on")) // forced last resort
        assertNull(ManualTracks.defaultSubtitle(subs, null, "on"))
    }

    @Test fun allowedLanguagesKeepUnknownAndFallBackToAll() {
        val tracks = audio + ProbedAudioTrack(9, "", "aac")
        assertEquals(listOf(4), ManualTracks.allowed(audio, listOf("fr")) { it.language }.map { it.index })
        assertEquals(listOf(4, 9), ManualTracks.allowed(tracks, listOf("fra")) { it.language }.map { it.index })
        assertEquals(audio, ManualTracks.allowed(audio, listOf("jpn")) { it.language })
        assertEquals(audio, ManualTracks.allowed(audio, emptyList()) { it.language })
    }

    @Test fun choiceReplacesServerTracksByContainerNumber() {
        val server = TrackSelection(
            audio = SelectedTrack(streamIndex = 3, typeIndex = 2, language = "eng"),
            subtitle = SelectedTrack(streamIndex = 6, typeIndex = 1, language = "eng"),
            subtitleMode = "forced-only",
        )
        val out = ManualTracks.applied(server, ManualTrackChoice(audio = 4, subtitle = 7), audio, subs)
        assertEquals(4, out.audio?.streamIndex)
        assertEquals(3, out.audio?.typeIndex)
        assertEquals("fre", out.audio?.languageCode)
        assertEquals(7, out.subtitle?.streamIndex)
        assertEquals(2, out.subtitle?.typeIndex)
        assertEquals(2, out.subtitle?.languageIndex)
        assertNull(out.textSubtitle) // bitmap
        assertEquals("on", out.subtitleMode)

        val off = ManualTracks.applied(server, ManualTrackChoice(subtitle = ManualTrackChoice.OFF), audio, subs)
        assertEquals(server.audio, off.audio)
        assertNull(off.subtitle)
        assertEquals("off", off.subtitleMode)

        // A VobSub pick is ignored, as on the Expo native players; the server's choice stays.
        val dvd = ManualTracks.applied(server, ManualTrackChoice(subtitle = 8), audio, subs)
        assertEquals(server, dvd)
    }

    @Test fun labels() {
        assertEquals("English · Commentary", ManualTracks.audioLabel(audio[1]))
        assertEquals("EAC3", ManualTracks.audioDetail(audio[1]))
        assertEquals("Unknown", ManualTracks.audioLabel(ProbedAudioTrack(0, "und", "aac")))
        assertEquals("Forced · SRT", ManualTracks.subtitleDetail(subs[0]))
        assertEquals("English · SDH", ManualTracks.subtitleLabel(subs[2]))
        assertEquals("PGS", ManualTracks.subtitleDetail(subs[2]))
    }
}

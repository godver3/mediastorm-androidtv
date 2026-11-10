package com.strmr.mobile.cast

import com.strmr.tv.api.DetailsTarget
import com.strmr.tv.api.SelectedTrack
import com.strmr.tv.api.StartPoint
import com.strmr.tv.api.TrackSelection
import com.strmr.tv.player.PlaybackRequest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class CastRulesTest {
    private val movie = PlaybackRequest(DetailsTarget("tmdb:1", "Movie", isMovie = true))

    @Test
    fun nearEndCountsAsFinished() {
        assertTrue(CastEnd.isNearEnd(false, 3_550.0, 3_600.0))
        assertTrue(CastEnd.isNearEnd(false, 9_300.0, 10_000.0))
        assertFalse(CastEnd.isNearEnd(false, 1_000.0, 3_600.0))
        assertFalse(CastEnd.isNearEnd(false, 100.0, 0.0))
        assertFalse(CastEnd.isNearEnd(true, 3_590.0, 3_600.0))
    }

    @Test
    fun startPositionFromSecondsOrPercent() {
        assertEquals(42.0, castStartPosition(movie, 42.0, 100.0), 0.0)
        val percent = movie.copy(start = StartPoint.Percent(25.0))
        assertEquals(1_000.0, castStartPosition(percent, 0.0, 4_000.0), 0.0)
        assertEquals(0.0, castStartPosition(percent, 0.0, null), 0.0)
        assertEquals(0.0, castStartPosition(movie, 0.0, 4_000.0), 0.0)
    }

    @Test
    fun subtitleForReceiverIsText() {
        val pgs = SelectedTrack(streamIndex = 5, typeIndex = 0, language = "English", languageCode = "eng", bitmap = true)
        val srt = SelectedTrack(streamIndex = 6, typeIndex = 1, languageCode = "eng", bitmap = false)
        assertEquals(6, castSubtitleTrack(TrackSelection(subtitle = pgs, textSubtitle = srt, subtitleMode = "on"))?.streamIndex)
        assertEquals(6, castSubtitleTrack(TrackSelection(subtitle = srt, subtitleMode = "on"))?.streamIndex)
        assertNull(castSubtitleTrack(TrackSelection(subtitle = pgs, subtitleMode = "on")))
        assertNull(castSubtitleTrack(TrackSelection(subtitle = srt, subtitleMode = "off")))
        assertNull(castSubtitleTrack(null))
    }

    @Test
    fun episodeSubtitleLine() {
        val episode = movie.copy(episode = PlaybackRequest.EpisodeInfo(1, 2, name = "Pilot"))
        assertEquals("S01E02 · Pilot", castSubtitle(episode))
        assertEquals("S01E02", castSubtitle(movie.copy(episode = PlaybackRequest.EpisodeInfo(1, 2, name = ""))))
        assertNull(castSubtitle(movie))
    }

    @Test
    fun deviceDetailSkipsRepeatedModel() {
        assertEquals("DLNA · 10.0.0.2", CastDevice("dlna:x", "TV", "TV", CastDevice.Kind.DLNA, "10.0.0.2").detail)
        assertEquals("Chromecast Ultra · Chromecast", CastDevice("cast:y", "Den", "Chromecast Ultra", CastDevice.Kind.GOOGLE_CAST, null).detail)
    }
}

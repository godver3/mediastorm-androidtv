package com.strmr.tv.player

import com.strmr.tv.api.DetailsTarget
import com.strmr.tv.api.StartPoint
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SourceSwitchTest {
    private val movie = PlaybackRequest(DetailsTarget("tmdb:movie:603", "The Matrix", isMovie = true, year = 1999))

    @Test fun queryAndResume() {
        assertEquals("The Matrix 1999", SourceSwitchRules.query(movie))
        val episode = movie.copy(title = DetailsTarget("tmdb:tv:1", "Show", isMovie = false),
                                 episode = PlaybackRequest.EpisodeInfo(1, 2))
        assertEquals("Show S01E02", SourceSwitchRules.query(episode))
        assertEquals(StartPoint.Seconds(754.5), SourceSwitchRules.resumeAt(movie, 754_500).start)
        assertEquals(StartPoint.Seconds(0.0), SourceSwitchRules.resumeAt(movie, -5).start)
    }

    @Test fun currentSourceMatchesByReleaseName() {
        val name = "The.Matrix.1999.1080p.BluRay.x264-GRP"
        assertTrue(SourceSwitchRules.isCurrent(name, null, "$name.mkv"))
        assertTrue(SourceSwitchRules.isCurrent("Other title", "$name\n👤 12", name))
        assertFalse(SourceSwitchRules.isCurrent("The.Matrix.1999.2160p.UHD", null, name))
        assertFalse(SourceSwitchRules.isCurrent(name, null, null))
    }

    @Test fun detailLine() {
        val attrs = mapOf("resolution" to "2160p", "hasDV" to "true", "hdr" to "HDR10", "codec" to "hevc", "tracker" to "")
        assertEquals("4K · DV · HDR · HEVC · 12.0 GB · Usenet (NZBgeek) · Healthy",
                     SourceSwitchRules.detail(attrs, 12L shl 30, "usenet", "NZBgeek", "healthy"))
        assertEquals("1080p · 700 MB · Debrid (RD) · Cached",
                     SourceSwitchRules.detail(mapOf("resolution" to "1080p", "tracker" to "RD"), 700L shl 20, "debrid", "x", "cached"))
    }

    @Test fun carriedLanguagePick() {
        val tracks = listOf(
            CarriedTracks.Candidate("en", selected = true),
            CarriedTracks.Candidate("fre"),
            CarriedTracks.Candidate("fr", forced = true),
        )
        assertNull("already playing that language", CarriedTracks.pick(tracks, "eng"))
        assertEquals(1, CarriedTracks.pick(tracks, "fra"))
        assertEquals(2, CarriedTracks.pick(tracks, "fr", forced = true))
        assertEquals(1, CarriedTracks.pick(tracks, "fr", forced = false))
        assertNull("not in the new stream", CarriedTracks.pick(tracks, "deu"))
        assertNull("unknown language", CarriedTracks.pick(tracks, null))
    }
}

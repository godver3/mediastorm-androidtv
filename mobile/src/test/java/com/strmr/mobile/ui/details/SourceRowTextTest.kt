package com.strmr.mobile.ui.details

import org.junit.Assert.assertEquals
import org.junit.Test

class SourceRowTextTest {
    @Test
    fun releaseIsTheRawTitlesFirstLine() {
        assertEquals("Movie.2024.2160p", SourceRowText.release("Movie", mapOf("raw_title" to "Movie.2024.2160p\n👤 12")))
        assertEquals("Movie", SourceRowText.release("Movie", emptyMap()))
        assertEquals("Movie", SourceRowText.release("Movie", mapOf("raw_title" to "")))
    }

    @Test
    fun badgesInOrder() {
        val attrs = mapOf("resolution" to "2160p", "hasDV" to "true", "hdr" to "HDR10+", "quality" to "WEB-DL", "codec" to "hevc")
        assertEquals(listOf("4K", "DV", "HDR10+", "WEB-DL", "HEVC"), SourceRowText.badges(attrs))
        assertEquals(listOf("1080p", "HDR"), SourceRowText.badges(mapOf("resolution" to "1080p", "hdr" to "HDR10")))
        assertEquals(emptyList<String>(), SourceRowText.badges(emptyMap()))
    }

    @Test
    fun usenetHasNoSeedersAndSaysUsenet() {
        val details = SourceRowText.details(mapOf("seeders" to "40", "tracker" to "T"), "4 GB", "usenet", "nzbgeek", tracksChosen = false)
        assertEquals(listOf("4 GB", "Usenet"), details)
    }

    @Test
    fun torrentDetailsAndChosenTracks() {
        val attrs = mapOf("seeders" to "40", "audio" to "DDP", "channels" to "5.1", "languages" to "en,fr⚙")
        val details = SourceRowText.details(attrs, null, "torrent", "idx", tracksChosen = true)
        assertEquals(listOf("idx", "40 seeders", "DDP", "5.1", "en fr", "Tracks chosen"), details)
    }

    @Test
    fun healthStates() {
        assertEquals(SourceRowText.Health.GOOD, SourceRowText.health("cached"))
        assertEquals(SourceRowText.Health.GOOD, SourceRowText.health("healthy"))
        assertEquals(SourceRowText.Health.BAD, SourceRowText.health("not_cached"))
        assertEquals(SourceRowText.Health.PENDING, SourceRowText.health("pending"))
        assertEquals(SourceRowText.Health.NONE, SourceRowText.health(null))
    }
}

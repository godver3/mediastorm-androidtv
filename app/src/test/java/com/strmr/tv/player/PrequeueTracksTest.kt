package com.strmr.tv.player

import com.strmr.tv.api.ApiJson
import com.strmr.tv.api.PrequeueStatus
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** Details › Audio & Subtitles: the prequeue status's lists, release name and the same-stream guard on the picks. */
class PrequeueTracksTest {
    private fun status(extra: String = "", path: String = "/debrid/x/Movie.2026.2160p.mkv", episode: String = ""): PrequeueStatus =
        ApiJson.decodeFromString(
            """
            {"prequeueId": "pq_1", "status": "ready", "streamPath": "$path", "displayName": "Movie", $episode
             "audioTracks": [{"index": 1, "language": "eng", "codec": "eac3", "profile": "Dolby Digital Plus + Dolby Atmos"}],
             "subtitleTracks": [
               {"index": 3, "absoluteIndex": 3, "language": "eng", "codec": "subrip"},
               {"index": 1, "absoluteIndex": 4, "language": "eng", "codec": "hdmv_pgs_subtitle", "forced": true, "isBitmap": true}
             ]$extra}
            """,
        )

    @Test fun trackListsUseContainerStreamNumbers() {
        val lists = status().trackLists
        assertEquals(listOf(1), lists.audio.map { it.index })
        assertEquals("Dolby Digital Plus + Dolby Atmos", lists.audio.first().profile)
        // `absoluteIndex` wins over a list position in `index`.
        assertEquals(listOf(3, 4), lists.subtitles.map { it.index })
        assertTrue(lists.subtitles.last().isBitmap)
        assertTrue(lists.subtitles.last().forced)
    }

    @Test fun releaseTitlePrefersTheRelease() {
        assertEquals("Movie.2026.2160p.mkv", status().releaseTitle)
        assertEquals("Movie 2026 2160p WEB-DL", status(""", "selectedResult": {"title": "Movie 2026 2160p WEB-DL"}""").releaseTitle)
        assertEquals("Raw Name", status(""", "resultAttributes": {"raw_title": "Raw Name\nsecond line"}""").releaseTitle)
        assertEquals("AIO Name", status(""", "passthroughName": "AIO Name"""").releaseTitle)
        assertEquals("Movie", status(path = "").releaseTitle)
    }

    @Test fun picksApplyOnlyToTheSameStreamAndEpisode() {
        val pick = PrequeuePick("/debrid/x/Movie.2026.2160p.mkv", null,
                                ManualSourceTracks(ManualTrackChoice(subtitle = ManualTrackChoice.OFF), emptyList(), emptyList()))
        assertTrue(pick.appliesTo(status()))
        assertFalse(pick.appliesTo(status(path = "/debrid/y/Other.mkv")))
        assertFalse(pick.appliesTo(status(episode = """"targetEpisode": {"seasonNumber": 1, "episodeNumber": 2},""")))
    }
}

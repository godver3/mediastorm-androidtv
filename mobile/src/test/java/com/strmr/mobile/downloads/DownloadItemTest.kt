package com.strmr.mobile.downloads

import com.strmr.tv.api.DetailsTarget
import com.strmr.tv.api.Episode
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DownloadItemTest {
    private val show = DetailsTarget("tvdb:series:9", "Show", isMovie = false, tvdbId = "9", tmdbId = "77")
    private val ep = Episode("e1", "Pilot", seasonNumber = 1, episodeNumber = 2)

    @Test fun extensionFromPath() {
        assertEquals("mp4", DownloadItem.fileExtension("/webdav/Some%20Show/file.MP4"))
        assertEquals("mkv", DownloadItem.fileExtension("/webdav/release/"))
        assertEquals("mkv", DownloadItem.fileExtension("/webdav/file.nfo"))
    }

    @Test fun episodeFields() {
        val item = DownloadItem.create(show, ep, "/x/a.mkv", "Rel", 1000)
        assertEquals("S01E02", item.episodeCode)
        assertEquals("Show · S01E02", item.displayTitle)
        assertEquals("Pilot", item.rowTitle)
        assertEquals("tvdb:series:9:S01E02", item.progressItemId)
        assertTrue(item.matches("tvdb:series:9", 1, 2))
        assertFalse(item.matches("tvdb:series:9", 1, 3))
        assertTrue(item.fileName.endsWith(".mkv"))
    }

    @Test fun progressAndFormat() {
        val item = DownloadItem.create(show, ep, "/x/a.mkv", null, 2000).copy(bytesWritten = 500)
        assertEquals(0.25f, item.progress)
        assertEquals("2.0 KB", DownloadFormat.bytes(2000))
        assertEquals("1.5 GB", DownloadFormat.bytes(1_500_000_000))
        assertEquals("Paused · 500 bytes of 2.0 KB", DownloadFormat.progressLine(item.copy(status = DownloadItem.Status.PAUSED)))
    }

    @Test fun watchedMatching() {
        val item = DownloadItem.create(show, ep, "/x/a.mkv", null, 0)
        assertTrue(DownloadWatched.isWatched(item, setOf("episode:tvdb:series:9:s01e02")))
        assertTrue(DownloadWatched.isWatched(item, setOf("episode:tmdb:tv:77:s01e02")))
        assertFalse(DownloadWatched.isWatched(item, setOf("episode:tvdb:series:9:s01e03")))
        val movie = DownloadItem.create(DetailsTarget("tmdb:movie:5", "M", true, imdbId = "tt1"), null, "/m.mkv", null, 0)
        assertTrue(DownloadWatched.isWatched(movie, setOf("movie:tt1")))
    }

    @Test fun showsGroupAndSort() {
        val a = DownloadItem.create(show, ep, "/a.mkv", null, 0, now = 1).copy(status = DownloadItem.Status.COMPLETED)
        val b = DownloadItem.create(show, ep.copy(episodeNumber = 1), "/b.mkv", null, 0, now = 5).copy(status = DownloadItem.Status.COMPLETED)
        val other = DownloadItem.create(DetailsTarget("s2", "Another", false), ep, "/c.mkv", null, 0, now = 3)
            .copy(status = DownloadItem.Status.COMPLETED)
        val shows = DownloadSorting.shows(listOf(a, b, other), DownloadSettings.Sort.NEWEST)
        assertEquals(listOf("tvdb:series:9", "s2"), shows.map { it.first })
        assertEquals(listOf(1, 2), DownloadSorting.seasons(shows[0].second)[0].second.map { it.episodeNumber })
    }
}

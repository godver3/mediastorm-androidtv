package com.strmr.tv.launcher

import com.strmr.tv.api.ContinueWatchingItem
import com.strmr.tv.api.EpisodeRef
import com.strmr.tv.api.ResumeState
import com.strmr.tv.home.WatchlistItem
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class LauncherPayloadTest {
    private val movie = ContinueWatchingItem(
        seriesId = "tmdb:movie:603", seriesTitle = "The Matrix", overview = "A hacker learns the truth.",
        posterUrl = "https://image.tmdb.org/t/p/original/poster.jpg",
        textBackdropUrl = "https://image.tmdb.org/t/p/original/textback.jpg",
        backdropUrl = "https://image.tmdb.org/t/p/original/back.jpg",
        year = 1999, externalIds = mapOf("tmdb" to "603", "imdb" to "tt0133093"),
        updatedAt = "2026-10-19T20:15:30.5+02:00",
        resume = ResumeState(position = 1800.0, duration = 8160.0, percent = 22.0, eligible = true),
    )
    private val show = ContinueWatchingItem(
        seriesId = "tmdb:tv:1399", seriesTitle = "Game of Thrones",
        posterUrl = "/api/images/poster.jpg",
        nextEpisode = EpisodeRef(seasonNumber = 2, episodeNumber = 3, title = "What Is Dead May Never Die", airDate = "2012-04-15"),
        updatedAt = "0001-01-01T00:00:00Z",
    )
    private val upcoming = ContinueWatchingItem(
        seriesId = "tmdb:tv:9", seriesTitle = "Not Yet",
        nextEpisode = EpisodeRef(seasonNumber = 1, episodeNumber = 2, airDate = "2999-01-01"),
    )

    private fun build(
        cw: List<ContinueWatchingItem> = listOf(movie, show),
        wl: List<WatchlistItem> = emptyList(),
        hideWatched: Boolean = false,
    ) = LauncherPayload.build(cw, wl, "pat", hideWatched, now = 1_000_000L) { if (it.startsWith("/")) "http://srv$it" else it }

    @Test fun continueWatchingMovieMapsResumeArtAndLinks() {
        val p = build().continueWatching.first()
        assertEquals("tmdb:movie:603", p.id)
        assertEquals(LauncherProgram.Kind.MOVIE, p.kind)
        assertEquals("A hacker learns the truth.", p.description)
        // The backdrop with its text first, sized down; 16:9.
        assertEquals("https://image.tmdb.org/t/p/w780/textback.jpg", p.imageUrl)
        assertTrue(p.landscape)
        assertEquals(1_800_000L, p.positionMs)
        assertEquals(8_160_000L, p.durationMs)
        assertEquals(LauncherProgram.WatchNext.CONTINUE, p.watchNext)
        assertEquals(java.time.OffsetDateTime.parse("2026-10-19T20:15:30.5+02:00").toInstant().toEpochMilli(), p.lastEngagementMs)
        assertTrue(p.displayUrl.startsWith("com.strmr.tv://details?"))
        assertTrue(p.playUrl.startsWith("com.strmr.tv://play?"))
        val link = DeepLink.parse(p.playUrl)!!
        assertEquals(DeepLink.Action.PLAY, link.action)
        assertEquals("pat", link.profileId)
        assertEquals("tmdb:movie:603", link.target.titleId)
        assertTrue(link.target.isMovie)
        assertEquals("tt0133093", link.target.imdbId)
    }

    @Test fun seriesWithNextEpisodeIsWatchNextNext() {
        val p = build().continueWatching[1]
        assertEquals(LauncherProgram.Kind.EPISODE, p.kind)
        assertEquals("Up next: S2 E3", p.description)
        assertEquals(2, p.seasonNumber)
        assertEquals(3, p.episodeNumber)
        assertEquals("What Is Dead May Never Die", p.episodeTitle)
        assertEquals(LauncherProgram.WatchNext.NEXT, p.watchNext)
        assertNull(p.positionMs)
        // A zero time isn't an engagement: the list order stands in (second item, a second earlier).
        assertEquals(999_000L, p.lastEngagementMs)
        // A server-relative poster is resolved, and drawn 2:3.
        assertEquals("http://srv/api/images/poster.jpg", p.imageUrl)
        assertFalse(p.landscape)
    }

    @Test fun upcomingEpisodesAreLeftOutAndSectionsCapAtTwenty() {
        val many = (1..30).map { movie.copy(seriesId = "tmdb:movie:$it") }
        val payload = build(cw = listOf(upcoming) + many)
        assertEquals(LauncherPayload.MAX_PER_SECTION, payload.continueWatching.size)
        assertTrue(payload.continueWatching.none { it.id == "tmdb:tv:9" })
        assertTrue(build(cw = listOf(upcoming)).isEmpty)
    }

    @Test fun percentOnlyProgressUsesExposNominalDuration() {
        val (position, duration) = LauncherPayload.progress(show.copy(resumePercent = 42.4))
        assertEquals(424L, position)
        assertEquals(1000L, duration)
        assertEquals(null to null, LauncherPayload.progress(show))
        // An ineligible server resume point is not offered.
        val ineligible = movie.copy(resume = ResumeState(position = 10.0, duration = 100.0, percent = 10.0, eligible = false))
        assertEquals(null to null, LauncherPayload.progress(ineligible))
    }

    @Test fun watchlistMapsToDetailsLinksAndHonorsHideWatched() {
        val wl = listOf(
            WatchlistItem(id = "tmdb:tv:1", mediaType = "series", name = "Show", posterUrl = "https://image.tmdb.org/t/p/original/p.jpg", watchState = "partial"),
            WatchlistItem(id = "tmdb:movie:2", mediaType = "movie", name = "Done", watchState = "complete"),
        )
        val all = build(wl = wl).watchlist
        assertEquals(listOf("series:tmdb:tv:1", "movie:tmdb:movie:2"), all.map { it.id })
        assertEquals(LauncherProgram.Kind.SERIES, all[0].kind)
        assertEquals("Saved in your watchlist", all[0].description)
        assertEquals("https://image.tmdb.org/t/p/w500/p.jpg", all[0].imageUrl)
        assertNull(all[0].watchNext)
        assertEquals(DeepLink.Action.DETAILS, DeepLink.parse(all[0].playUrl)!!.action)
        assertEquals(listOf("series:tmdb:tv:1"), build(wl = wl, hideWatched = true).watchlist.map { it.id })
    }

    @Test fun payloadEqualityDetectsNoChange() {
        assertEquals(build(), build())
        assertFalse(build() == build(cw = listOf(movie)))
    }
}

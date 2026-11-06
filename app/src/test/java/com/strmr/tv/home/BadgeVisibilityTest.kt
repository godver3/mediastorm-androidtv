package com.strmr.tv.home

import com.strmr.tv.api.ContinueWatchingItem
import com.strmr.tv.api.Title
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class BadgeVisibilityTest {
    @Test
    fun deviceOverrideBeatsTheServer() {
        assertFalse(BadgeVisibility.shows("watchState", false, listOf("watchState")))
        assertTrue(BadgeVisibility.shows("watchState", true, emptyList()))
    }

    @Test
    fun serverListDecidesWithoutAnOverride() {
        assertTrue(BadgeVisibility.shows("unwatchedCount", null, listOf("watchProgress", "unwatchedCount")))
        assertTrue(BadgeVisibility.shows("releaseStatus", null, listOf(" ReleaseStatus ")))
        // An empty list (every badge turned off) is a choice, not "unset".
        assertFalse(BadgeVisibility.shows("watchState", null, emptyList()))
    }

    @Test
    fun unreadServerFallsBackToProgressOnly() {
        assertTrue(BadgeVisibility.shows("watchProgress", null, null))
        assertFalse(BadgeVisibility.shows("watchState", null, null))
        assertFalse(BadgeVisibility.shows("releaseStatus", null, null))
        assertFalse(BadgeVisibility.shows("unwatchedCount", null, null))
    }

    private val all = BadgeVisibility(watchState = true, releaseStatus = true, unwatchedCount = true)

    private fun series(count: Int?, state: String? = "partial", availability: String = "released") =
        Title(id = "tmdb:tv:1", name = "Show", mediaType = "series", availability = availability, watchState = state, unwatchedCount = count)

    @Test
    fun countRidesInTheWatchStateBadge() {
        val badges = HomeCard.from(series(5)).visibleBadges(all)
        assertEquals(listOf(CardBadge("In Progress, 5 unwatched", CardBadge.Symbol.IN_PROGRESS, count = 5)), badges)
    }

    @Test
    fun countStandsAloneWithoutWatchState() {
        val badges = HomeCard.from(series(5)).visibleBadges(all.copy(watchState = false))
        assertEquals(listOf(CardBadge("5 unwatched", count = 5)), badges)
        assertEquals(listOf(CardBadge("5 unwatched", count = 5)), HomeCard.from(series(5, state = null)).visibleBadges(all))
    }

    @Test
    fun countShowsForEverySeriesTheServerCountsIncludingZero() {
        assertEquals(
            listOf(CardBadge("Watched, 0 unwatched", CardBadge.Symbol.WATCHED, count = 0)),
            HomeCard.from(series(0, state = "complete")).visibleBadges(all),
        )
        assertEquals(listOf(CardBadge("12 unwatched", count = 12)), HomeCard.from(series(12, state = "none")).visibleBadges(all))
        assertEquals(listOf(CardBadge.InProgress), HomeCard.from(series(null)).visibleBadges(all))
        assertEquals(listOf(CardBadge.InProgress), HomeCard.from(series(5)).visibleBadges(all.copy(unwatchedCount = false)))
        val movie = Title(id = "tmdb:movie:1", name = "Film", mediaType = "movie", watchState = "partial", unwatchedCount = 3)
        assertNull(HomeCard.from(movie).unwatchedCount)
    }

    private fun continuing(id: String = "tmdb:tv:1", total: Int?, watched: Int?) =
        ContinueWatchingItem(seriesId = id, seriesTitle = "Show", totalEpisodeCount = total, watchedEpisodeCount = watched)

    @Test
    fun continueWatchingCountsFromEpisodeTotals() {
        assertEquals(7, HomeCard.from(continuing(total = 10, watched = 3)).unwatchedCount)
        assertEquals(0, HomeCard.from(continuing(total = 10, watched = 12)).unwatchedCount)
        assertEquals(10, HomeCard.from(continuing(total = 10, watched = null)).unwatchedCount)
        assertNull(HomeCard.from(continuing(total = null, watched = 3)).unwatchedCount)
        assertNull(HomeCard.from(continuing(total = 0, watched = 0)).unwatchedCount)
        assertNull(HomeCard.from(continuing(id = "tmdb:movie:1", total = 10, watched = 3)).unwatchedCount)
        assertEquals(
            listOf(CardBadge("7 unwatched", count = 7)),
            HomeCard.from(continuing(total = 10, watched = 3)).visibleBadges(all.copy(watchState = false)),
        )
    }

    @Test
    fun continueWatchingSeriesWatchState() {
        assertEquals(CardBadge.Watched, HomeCard.from(continuing(total = 10, watched = 10)).watchBadge)
        assertEquals(CardBadge.InProgress, HomeCard.from(continuing(total = 10, watched = 3)).watchBadge)
        assertEquals(CardBadge.InProgress, HomeCard.from(continuing(total = null, watched = 2)).watchBadge)
        assertNull(HomeCard.from(continuing(total = 10, watched = 0)).watchBadge)
        val resumed = continuing(total = 10, watched = 0).copy(resumePercent = 40.0)
        assertEquals(CardBadge.InProgress, HomeCard.from(resumed).watchBadge)
        assertNull(HomeCard.from(continuing(id = "tmdb:movie:1", total = 10, watched = 10)).watchBadge)
        // Gated when the card draws, as the other cards are.
        val card = HomeCard.from(continuing(total = 10, watched = 3))
        assertEquals(listOf(CardBadge("7 unwatched", count = 7)), card.visibleBadges(all.copy(watchState = false)))
        assertEquals(
            listOf(CardBadge("In Progress, 7 unwatched", CardBadge.Symbol.IN_PROGRESS, count = 7)),
            card.visibleBadges(all),
        )
    }

    @Test
    fun settingsGateReleaseAndWatchStateButNotRank() {
        val title = series(null, availability = "coming_soon")
        assertEquals(emptyList<CardBadge>(), HomeCard.from(title).visibleBadges(BadgeVisibility()))
        assertEquals(listOf(CardBadge.ComingSoon, CardBadge.InProgress), HomeCard.from(title).visibleBadges(all))
        assertEquals(listOf(CardBadge("#2")), HomeCard.from(title, rank = 2).visibleBadges(BadgeVisibility()))
        assertEquals(listOf(CardBadge("#2")), HomeCard.from(title).ranked(2).visibleBadges(all.copy(watchState = false)))
    }
}

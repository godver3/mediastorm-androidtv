package com.strmr.tv.home

import com.strmr.tv.api.Title
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class WatchStateBadgeTest {
    @Test
    fun watchStateMapsToBadge() {
        assertEquals(CardBadge.Watched, CardBadge.forWatchState("complete"))
        assertEquals(CardBadge.InProgress, CardBadge.forWatchState("partial"))
        assertNull(CardBadge.forWatchState("none"))
        assertNull(CardBadge.forWatchState(null))
    }

    @Test
    fun searchTitleCardShowsWatchState() {
        val title = Title(id = "tmdb:movie:1", name = "Seen", mediaType = "movie", availability = "released", watchState = "complete")
        assertEquals(CardBadge.Watched, HomeCard.from(title).watchBadge)
    }

    @Test
    fun watchStateStacksUnderRankAndReleaseBadges() {
        val title = Title(id = "tmdb:movie:1", name = "Seen", mediaType = "movie", availability = "coming_soon", watchState = "partial")
        assertEquals(CardBadge.ComingSoon, HomeCard.from(title).badge)
        assertEquals(CardBadge.InProgress, HomeCard.from(title).watchBadge)
        assertEquals(CardBadge("#3"), HomeCard.from(title, rank = 3).badge)
        assertEquals(CardBadge.InProgress, HomeCard.from(title, rank = 3).watchBadge)
    }
}

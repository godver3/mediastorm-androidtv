package com.strmr.tv.library

import com.strmr.tv.api.DetailsTarget
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class HiddenItemsMatchTest {
    private val movie = DetailsTarget("tmdb:movie:1", "A Movie", isMovie = true, tmdbId = "1", imdbId = "tt9")

    @Test fun matchesByIdOrSharedProviderId() {
        assertTrue(HiddenItemsStore.matches(HiddenItem("tmdb:movie:1", "movie"), movie))
        assertTrue(HiddenItemsStore.matches(HiddenItem("imdb:tt9", "movie", externalIds = mapOf("imdb" to "tt9")), movie))
        assertFalse(HiddenItemsStore.matches(HiddenItem("tmdb:movie:2", "movie", externalIds = mapOf("tmdb" to "2")), movie))
    }

    @Test fun mediaTypeMustAgree() {
        assertFalse(HiddenItemsStore.matches(HiddenItem("tmdb:movie:1", "series"), movie))
        val show = DetailsTarget("tmdb:tv:5", "A Show", isMovie = false, tmdbId = "5")
        assertTrue(HiddenItemsStore.matches(HiddenItem("tvdb:series:7", "series", externalIds = mapOf("tmdb" to "5")), show))
    }
}

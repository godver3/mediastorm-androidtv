package com.strmr.tv.home

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class HomeViewsTest {
    private val movies = HomeMediaFilter.MOVIES
    private val shows = HomeMediaFilter.SHOWS

    @Test fun matchesHomeViewByMediaType() {
        listOf("movie", "Movies", "film", "FILMS").forEach { assertTrue(it, matchesHomeView(it, movies)); assertFalse(it, matchesHomeView(it, shows)) }
        listOf("tv", "series", "Show", "shows", "episode").forEach { assertTrue(it, matchesHomeView(it, shows)); assertFalse(it, matchesHomeView(it, movies)) }
        assertFalse(matchesHomeView(null, movies))
        assertFalse(matchesHomeView("", shows))
        assertFalse(matchesHomeView("person", movies))
        assertTrue(matchesHomeView(null, HomeMediaFilter.ALL))
        assertTrue(matchesHomeView("anything", HomeMediaFilter.ALL))
    }

    @Test fun shelfAllowedDropsLiveRowsAndTheOtherKind() {
        fun allowed(shelf: ShelfConfig, filter: HomeMediaFilter) = homeViewShelfAllowed(shelf, filter)
        listOf("live-favorites", "dashboard", "tonight").forEach {
            assertTrue(allowed(ShelfConfig(it), HomeMediaFilter.ALL))
            assertFalse(allowed(ShelfConfig(it), movies))
            assertFalse(allowed(ShelfConfig(it), shows))
        }
        listOf("trending-tv", "my-upcoming", "my-recently-aired").forEach {
            assertFalse(allowed(ShelfConfig(it), movies))
            assertTrue(allowed(ShelfConfig(it), shows))
        }
        assertFalse(allowed(ShelfConfig("trending-movies"), shows))
        assertTrue(allowed(ShelfConfig("trending-movies"), movies))
        // Typed sources: addon catalog type, then TMDB, then Simkl.
        assertFalse(allowed(ShelfConfig("a", type = "stremio", addonCatalogType = "series"), movies))
        assertTrue(allowed(ShelfConfig("a", type = "stremio", addonCatalogType = "movie"), movies))
        assertTrue(allowed(ShelfConfig("t", type = "tmdb", tmdbMediaType = "tv"), shows))
        assertFalse(allowed(ShelfConfig("t", type = "tmdb", tmdbMediaType = "movie"), shows))
        assertTrue(allowed(ShelfConfig("t", type = "tmdb", tmdbMediaType = "all"), shows))
        assertFalse(allowed(ShelfConfig("s", type = "simkl", simklMediaType = "movies"), shows))
        assertTrue(allowed(ShelfConfig("s", type = "simkl", simklMediaType = "shows"), shows))
        // Genre and decade IDs carry their kind.
        assertTrue(allowed(ShelfConfig("genre-28-movie"), movies))
        assertFalse(allowed(ShelfConfig("genre-28-movie"), shows))
        assertTrue(allowed(ShelfConfig("decade-1990-tv"), shows))
        assertFalse(allowed(ShelfConfig("decade-1990-tv"), movies))
        // Untyped rows stay.
        assertTrue(allowed(ShelfConfig("watchlist"), movies))
        assertTrue(allowed(ShelfConfig("m", type = "mdblist", listUrl = "u"), shows))
    }

    @Test fun mergeTakesOnlyEmptyNameIconFilterFromServer() {
        val global = mapOf(
            "page-a" to HomeViewSettings(name = "A", icon = "star", mediaFilter = "movies", mode = "custom", shelves = listOf(ShelfConfig("x"))),
            "page-g" to HomeViewSettings(name = "G", mode = "inherit"),
        )
        val profile = mapOf("page-a" to HomeViewSettings(name = "", mode = "custom", itemCap = 5))
        val merged = HomeViews.merge(global, profile)
        assertEquals("A", merged["page-a"]?.name)
        assertEquals("star", merged["page-a"]?.icon)
        assertEquals("movies", merged["page-a"]?.mediaFilter)
        assertNull("shelf lists never merge", merged["page-a"]?.shelves)
        assertEquals(5, merged["page-a"]?.itemCap)
        assertEquals("G", merged["page-g"]?.name)
    }

    @Test fun idsFilterAndLabels() {
        val views = mapOf(
            "page-b" to HomeViewSettings(name = "Zed", mediaFilter = "shows"),
            "page-a" to HomeViewSettings(name = "alpha"),
            "page-gone" to HomeViewSettings(name = "Gone", deleted = true, mediaFilter = "movies"),
            "movies" to HomeViewSettings(mode = "custom"),
            "bogus" to HomeViewSettings(name = "Bogus"),
        )
        assertEquals(listOf("all", "movies", "shows", "page-a", "page-b"), HomeViews.ids(views))
        assertEquals(movies, HomeViews.filter("movies", emptyMap()))
        assertEquals(shows, HomeViews.filter("page-b", views))
        assertEquals(HomeMediaFilter.ALL, HomeViews.filter("page-a", views))
        assertEquals(HomeMediaFilter.ALL, HomeViews.filter("page-gone", views))
        assertEquals("Home", HomeViews.label("all", views))
        assertEquals("Zed", HomeViews.label("page-b", views))
        assertEquals("Home page", HomeViews.label("page-missing", views))
        assertEquals("all", HomeViews.parse("page-"))
        assertEquals("page-x-1", HomeViews.parse("page-x-1"))
        assertEquals("all", HomeViews.parse(null))
    }
}

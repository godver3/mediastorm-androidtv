package com.strmr.tv.home

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class HomeViewResolutionTest {
    private fun shelf(id: String, enabled: Boolean = true, order: Int = 0, mediaType: String? = null) =
        ShelfConfig(id, id, enabled = enabled, order = order, tmdbMediaType = mediaType)

    /** Every built-in row, enabled, so `ensure` adds nothing. */
    private val home = HomeFields(
        shelves = DefaultHomeShelves.all.map { it.copy(enabled = true) } + shelf("tmdb-movies", order = 30, mediaType = "movie") +
            shelf("tmdb-shows", order = 31, mediaType = "tv"),
        itemCap = 20,
        homeShelfFocusModel = "left",
        tvTopShelfMode = "default",
    )

    private fun enabled(fields: HomeFields) = fields.shelves.orEmpty().filter { it.enabled == true }.map { it.id }.toSet()

    @Test fun allIsHomeItself() {
        val resolved = resolveHomeView(null, home, "all")
        assertEquals(home.shelves, resolved.shelves)
        assertEquals(20, resolved.itemCap)
    }

    @Test fun profileFieldsLayerOverServerAndShelvesMerge() {
        val global = HomeFields(
            shelves = listOf(ShelfConfig("watchlist", "Server Watchlist", enabled = true, order = 1, limit = 7), shelf("server-only", order = 2)),
            itemCap = 30, exploreCardPosition = "end",
        )
        val profile = HomeFields(shelves = listOf(ShelfConfig("watchlist", enabled = false)), itemCap = 10)
        val resolved = resolveHomeView(global, profile, "all")
        assertEquals(10, resolved.itemCap)
        assertEquals("end", resolved.exploreCardPosition)
        val watchlist = resolved.shelves!!.first { it.id == "watchlist" }
        assertEquals(false, watchlist.enabled)
        assertEquals("Server Watchlist", watchlist.name)
        assertEquals(7, watchlist.limit)
        assertEquals(listOf("watchlist", "server-only"), resolved.shelves!!.map { it.id })
    }

    @Test fun moviesInheritsHomeAndDisablesTheOtherKind() {
        val resolved = resolveHomeView(null, home, "movies")
        val on = enabled(resolved)
        assertTrue("tmdb-movies" in on)
        assertTrue("watchlist" in on)
        listOf("tmdb-shows", "trending-tv", "my-upcoming", "my-recently-aired", "dashboard", "live-favorites").forEach { assertFalse(it, it in on) }
        // The rows themselves stay, disabled.
        assertTrue(resolved.shelves!!.any { it.id == "tmdb-shows" && it.enabled == false })
        assertEquals(20, resolved.itemCap)
    }

    @Test fun showsDropsMovieRows() {
        val on = enabled(resolveHomeView(null, home, "shows"))
        assertTrue("tmdb-shows" in on && "trending-tv" in on && "my-upcoming" in on)
        assertFalse("tmdb-movies" in on || "trending-movies" in on)
    }

    @Test fun customPageBringsItsOwnShelvesAndLayout() {
        val page = HomeViewSettings(
            name = "Kids", mode = "custom", itemCap = 8, homeShelfFocusModel = "center",
            shelves = listOf(shelf("watchlist", order = 0), shelf("tmdb-shows", order = 1, mediaType = "tv")),
        )
        val resolved = resolveHomeView(null, home.copy(views = mapOf("page-kids" to page)), "page-kids")
        assertEquals(8, resolved.itemCap)
        assertEquals("center", resolved.homeShelfFocusModel)
        assertEquals(setOf("watchlist", "tmdb-shows"), enabled(resolved))
        // Built-in rows the page leaves out come back disabled.
        val ids = resolved.shelves!!.map { it.id }
        assertTrue(DefaultHomeShelves.all.all { it.id in ids })
        assertEquals("default", resolved.tvTopShelfMode)
    }

    @Test fun customPageWithMediaFilterFiltersItsShelves() {
        val page = HomeViewSettings(mode = "custom", mediaFilter = "movies",
                                    shelves = listOf(shelf("tmdb-shows", mediaType = "tv"), shelf("watchlist")))
        val resolved = resolveHomeView(null, home.copy(views = mapOf("page-m" to page)), "page-m")
        assertEquals(setOf("watchlist"), enabled(resolved))
    }

    @Test fun emptyPageHasNoRowsAndNoTopShelf() {
        val page = HomeViewSettings(name = "Empty", mode = "custom", shelves = emptyList())
        val resolved = resolveHomeView(null, home.copy(views = mapOf("page-e" to page)), "page-e")
        assertTrue(enabled(resolved).isEmpty())
        assertEquals("disabled", resolved.tvTopShelfMode)
        // An empty page that sets its own top shelf keeps it.
        val own = resolveHomeView(null, home.copy(views = mapOf("page-e" to page.copy(tvTopShelfMode = "default"))), "page-e")
        assertEquals("default", own.tvTopShelfMode)
    }

    @Test fun inheritPageWithoutShelvesInheritsHome() {
        val page = HomeViewSettings(name = "Same", mode = "inherit", itemCap = 3)
        val resolved = resolveHomeView(null, home.copy(views = mapOf("page-s" to page)), "page-s")
        assertEquals(20, resolved.itemCap)
        assertEquals(enabled(home) - "tonight", enabled(resolved))
    }

    @Test fun customPageWithoutShelvesInheritsHomeShelves() {
        val page = HomeViewSettings(mode = "custom", itemCap = 3)
        val resolved = resolveHomeView(null, home.copy(views = mapOf("page-s" to page)), "page-s")
        assertEquals(3, resolved.itemCap)
        assertTrue("watchlist" in enabled(resolved))
    }

    @Test fun deletedPageIsHome() {
        val page = HomeViewSettings(mode = "custom", deleted = true, shelves = emptyList())
        val profile = home.copy(views = mapOf("page-d" to page))
        assertEquals(resolveHomeView(null, profile, "all"), resolveHomeView(null, profile, "page-d"))
    }

    @Test fun disallowedTopShelfSourceFallsBackToDefault() {
        val withSource = home.copy(tvTopShelfMode = "shelf", tvTopShelfSourceId = "tmdb-shows")
        val movies = resolveHomeView(null, withSource, "movies")
        assertEquals("default", movies.tvTopShelfMode)
        assertNull(movies.tvTopShelfSourceId)
        val shows = resolveHomeView(null, withSource, "shows")
        assertEquals("shelf", shows.tvTopShelfMode)
        assertEquals("tmdb-shows", shows.tvTopShelfSourceId)
        val missing = resolveHomeView(null, home.copy(tvTopShelfMode = "shelf", tvTopShelfSourceId = "nope"), "shows")
        assertEquals("default", missing.tvTopShelfMode)
    }

    @Test fun ensureAddsMissingBuiltInsAndTurnsTonightOff() {
        val ensured = DefaultHomeShelves.ensure(listOf(
            shelf("continue-watching", order = 0), shelf("tonight", order = 1), shelf("watchlist", order = 2),
        ))
        val byId = ensured.associateBy { it.id }
        assertEquals("watch-together", ensured.first().id)
        assertEquals(false, byId["tonight"]?.enabled)
        listOf("my-upcoming", "my-recommended", "calendar", "my-recently-aired", "streaming-services", "watch-something")
            .forEach { assertEquals(it, true, byId[it]?.enabled) }
        listOf("live-favorites", "dashboard", "permanent-prequeue", "set-aside").forEach { assertEquals(it, false, byId[it]?.enabled) }
        assertEquals("air-date-asc", byId["my-upcoming"]?.sort)
        // Orders stay unique: every insert pushes the later rows down.
        val orders = ensured.drop(1).map { it.order }
        assertEquals(orders.size, orders.toSet().size)
        assertEquals(DefaultHomeShelves.all, DefaultHomeShelves.ensure(emptyList()))
    }

    @Test fun ensureKeepsLegacyRecommendations() {
        val ensured = DefaultHomeShelves.ensure(listOf(shelf("continue-watching"), shelf("gemini-recs", order = 1)))
        assertFalse(ensured.any { it.id == "my-recommended" })
    }
}

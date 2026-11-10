package com.strmr.tv.home

/** The Expo app's built-in Home rows (`DEFAULT_HOME_SHELF_CONFIG`), for resolving Home views. */
object DefaultHomeShelves {
    private val watchlistOnly = ShelfConfig.CalendarSources(watchlist = true, history = false, trending = false, topTrending = false, mdblists = false)

    val all: List<ShelfConfig> = listOf(
        ShelfConfig("watch-together", "Watch Together", enabled = true, order = -1),
        ShelfConfig("top-ten", "Top 10 Today", enabled = true, order = 0),
        ShelfConfig("continue-watching", "Continue Watching", enabled = true, order = 1),
        ShelfConfig("watch-something", "Watch Something", enabled = true, order = 2),
        ShelfConfig("tonight", "Tonight", enabled = false, order = 3),
        ShelfConfig("my-recommended", "My Recommended", enabled = true, order = 4),
        ShelfConfig("my-upcoming", "My Upcoming", enabled = true, order = 5, sort = "air-date-asc"),
        ShelfConfig("calendar", "Coming Up", enabled = true, order = 6),
        ShelfConfig("my-recently-aired", "My Recently Aired", enabled = true, order = 7, calendarSources = watchlistOnly),
        ShelfConfig("watchlist", "Your Watchlist", enabled = true, order = 8),
        ShelfConfig("trending-movies", "Trending Movies", enabled = true, order = 9),
        ShelfConfig("trending-tv", "Trending TV Shows", enabled = true, order = 10),
        ShelfConfig("streaming-services", "Streaming Services", enabled = true, order = 11),
        ShelfConfig("live-favorites", "Favorite Channels", enabled = false, order = 12),
        ShelfConfig("popular-on-server", "Popular on This Server", enabled = false, order = 13, limit = 20, activityWindowDays = 90, minimumProfiles = 2),
        ShelfConfig("recently-watched", "Recently Watched", enabled = false, order = 14, limit = 20, activityWindowDays = 14, maxItemsPerProfile = 3),
        ShelfConfig("dashboard", "Dashboard", enabled = false, order = 15),
        ShelfConfig("permanent-prequeue", "Permanent Prequeue", enabled = false, order = 16),
        ShelfConfig("set-aside", "Set Aside", enabled = false, order = 17),
    )

    /** One built-in row `ensure` adds when it's missing, after `after` (else at `fallback`, or the end). */
    private class Missing(val shelf: ShelfConfig, val after: String?, val fallback: Int?, val unless: String? = null)

    private val missing = listOf(
        Missing(ShelfConfig("my-upcoming", "My Upcoming", enabled = true, sort = "air-date-asc"), "continue-watching", 2),
        Missing(ShelfConfig("my-recommended", "My Recommended", enabled = true), "continue-watching", 2, unless = "gemini-recs"),
        Missing(ShelfConfig("calendar", "Coming Up", enabled = true), "my-upcoming", 4),
        Missing(ShelfConfig("my-recently-aired", "My Recently Aired", enabled = true, calendarSources = watchlistOnly), "calendar", 3),
        Missing(ShelfConfig("tonight", "Tonight", enabled = false), "continue-watching", 2),
        Missing(ShelfConfig("streaming-services", "Streaming Services", enabled = true), "trending-tv", null),
        Missing(ShelfConfig("live-favorites", "Favorite Channels", enabled = false), "streaming-services", null),
        Missing(ShelfConfig("dashboard", "Dashboard", enabled = false), "recently-watched", null),
        Missing(ShelfConfig("permanent-prequeue", "Permanent Prequeue", enabled = false), "dashboard", null),
        Missing(ShelfConfig("set-aside", "Set Aside", enabled = false), "permanent-prequeue", null),
        Missing(ShelfConfig("watch-something", "Watch Something", enabled = true), "continue-watching", 2),
    )

    /**
     * Home's shelves with the built-in rows a config predates (`ensureHomeShelfConfig`): none
     * configured is the defaults; Tonight is off; each missing built-in row goes in after its
     * neighbour, pushing later rows down; Watch Together always leads.
     */
    fun ensure(shelves: List<ShelfConfig>): List<ShelfConfig> {
        if (shelves.isEmpty()) return all
        val seen = mutableSetOf<String>()
        var next = shelves.filter { seen.add(it.id) }.map { if (it.id == "tonight" && it.enabled == true) it.copy(enabled = false) else it }
        next = next.map { if (it.id == "my-upcoming" && it.sort.isNullOrEmpty()) it.copy(sort = "air-date-asc") else it }
        for (m in missing) {
            if (next.any { it.id == m.shelf.id || it.id == m.unless }) continue
            val anchor = m.after?.let { id -> next.firstOrNull { it.id == id } }
            val at = anchor?.let { (it.order ?: 0) + 1 } ?: m.fallback ?: ((next.maxOfOrNull { it.order ?: 0 } ?: -1).coerceAtLeast(-1) + 1)
            next = next.map { if ((it.order ?: 0) >= at) it.copy(order = (it.order ?: 0) + 1) else it } + m.shelf.copy(order = at)
        }
        return listOf(ShelfConfig("watch-together", "Watch Together", enabled = true, order = -1)) + next.filterNot { it.id == "watch-together" }
    }
}

/** See [DefaultHomeShelves.ensure]. */
fun ensureHomeShelfConfig(shelves: List<ShelfConfig>) = DefaultHomeShelves.ensure(shelves)

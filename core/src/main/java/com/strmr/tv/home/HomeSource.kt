package com.strmr.tv.home

/** Where a shelf's cards come from. */
sealed interface HomeSource {
    data object ContinueWatching : HomeSource
    /** Continue Watching shows whose next episode hasn't aired. */
    data object Upcoming : HomeSource
    data object Watchlist : HomeSource
    data object SetAside : HomeSource
    /** A `display-list` source; ranked lists badge their cards "#1", "#2"… */
    data class List(val list: BrowseList, val ranked: Boolean) : HomeSource
    /** Recently aired then coming up, from the calendar. */
    data object Calendar : HomeSource
    data class RecentlyAired(val sources: ShelfConfig.CalendarSources?) : HomeSource
    /** What's playing on the server. */
    data object Dashboard : HomeSource
    data class WatchSomething(val hasAI: Boolean) : HomeSource
    data class Services(val services: kotlin.collections.List<StreamingService>) : HomeSource
    data class Hub(val links: kotlin.collections.List<HubLink>) : HomeSource
    /** The profile's favorite live channels. */
    data object LiveFavorites : HomeSource
    /** A local, Plex or Jellyfin library's newest titles. */
    data class Library(val library: com.strmr.tv.api.LibraryRef) : HomeSource

    data class HubLink(val id: String, val name: String, val list: BrowseList, val art: String?, val logo: String?)

    /** Rows that change after playback or a Continue Watching hide. */
    val followsPlayback: Boolean
        get() = this == ContinueWatching || this == Upcoming || this == SetAside || this == Dashboard ||
            (this is List && list == BrowseList.PermanentPrequeue)

    companion object {
        fun from(config: ShelfConfig, all: kotlin.collections.List<ShelfConfig>, settings: HomeSettings): HomeSource? = when (config.id) {
            HomeShelf.CONTINUE_WATCHING_ID -> ContinueWatching
            "my-upcoming" -> Upcoming
            "watchlist" -> Watchlist
            "set-aside" -> SetAside
            "top-ten" -> List(BrowseList.TopTen(), ranked = true)
            "my-recommended", "gemini-recs" -> List(BrowseList.Personalized, ranked = false)
            "calendar" -> Calendar
            "my-recently-aired" -> RecentlyAired(config.calendarSources)
            "dashboard" -> Dashboard
            "live-favorites" -> LiveFavorites
            "watch-something" -> WatchSomething(settings.hasAI)
            "streaming-services" -> Services(StreamingService.resolved(config.streamingServices))
            "popular-on-server", "recently-watched" -> {
                // The shelf's own window and caps; the explore grid keeps them.
                val q = mutableMapOf("source" to config.id, "name" to config.title)
                config.activityWindowDays?.takeIf { it > 0 }?.let { q["activityWindowDays"] = it.toString() }
                config.minimumProfiles?.takeIf { it > 0 }?.let { q["minimumProfiles"] = it.toString() }
                config.maxItemsPerProfile?.takeIf { it > 0 }?.let { q["maxItemsPerProfile"] = it.toString() }
                List(BrowseList.Remote(config.id, config.title, q), ranked = false)
            }
            else -> when {
                config.libraryID != null -> Library(
                    com.strmr.tv.api.LibraryRef(config.libraryID!!, if (config.name.isNullOrEmpty()) "Recently Added" else config.title),
                )
                config.type == "collection-hub" -> {
                    val byId = all.associateBy { it.id }
                    val links = (config.collectionItems ?: emptyList())
                        .filter { it.enabled != false }
                        .sortedBy { it.order ?: 0 }
                        .mapNotNull { item ->
                            val list = item.sourceShelfId?.let { byId[it] }?.browseList ?: return@mapNotNull null
                            HubLink(item.id, item.name ?: list.title, list, item.heroArtUrl, item.logoUrl)
                        }
                    if (links.isEmpty()) null else Hub(links)
                }
                // Remote lists, trending, genres, decades, permanent prequeue.
                else -> config.browseList?.let { List(it, ranked = false) }
            }
        }
    }
}

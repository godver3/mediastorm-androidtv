package com.strmr.tv.home

import kotlinx.serialization.Serializable

/** One configured Home shelf (`homeShelves.shelves`), server defaults filled in. */
@Serializable
data class ShelfConfig(
    val id: String,
    val name: String? = null,
    val enabled: Boolean? = null,
    val order: Int? = null,
    val type: String? = null,
    /** Cards the row shows; 0 / null for the profile's `itemCap`. */
    val limit: Int? = null,
    val activityWindowDays: Int? = null,
    val minimumProfiles: Int? = null,
    val maxItemsPerProfile: Int? = null,
    /** Which calendar sources a recently-aired shelf takes (watchlist only when unset). */
    val calendarSources: CalendarSources? = null,
    val listUrl: String? = null,
    /** A library shelf's library (`type: library`). */
    val libraryId: String? = null,
    val addonManifestUrl: String? = null,
    val addonCatalogType: String? = null,
    val addonCatalogId: String? = null,
    val tmdbSourceType: String? = null,
    val tmdbSourceId: String? = null,
    val tmdbMediaType: String? = null,
    val tmdbDiscoverQuery: String? = null,
    val sort: String? = null,
    val traktAccountId: String? = null,
    val traktListType: String? = null,
    val traktListId: String? = null,
    val publicMetaDBAccountId: String? = null,
    val publicMetaDBListId: String? = null,
    val simklAccountId: String? = null,
    val simklListType: String? = null,
    val simklMediaType: String? = null,
    val letterboxdListId: String? = null,
    val letterboxdListUrl: String? = null,
    val hideUnreleased: Boolean? = null,
    val streamingServices: List<StreamingService>? = null,
    val collectionItems: List<CollectionLink>? = null,
) {
    @Serializable
    data class CalendarSources(
        val watchlist: Boolean? = null,
        val history: Boolean? = null,
        val trending: Boolean? = null,
        val topTrending: Boolean? = null,
        val mdblists: Boolean? = null,
    )

    val title: String get() = name?.takeIf { it.isNotEmpty() } ?: id

    /**
     * A library shelf's library: `type: library` with `libraryId`, or the legacy
     * `local-library-<id>` shelf (as the Expo app reads them). Null for other shelves.
     */
    val libraryID: String?
        get() {
            if (type != "library" && type != "local-library") return null
            libraryId?.trim()?.ifEmpty { null }?.let { return it }
            return id.removePrefix("local-library-").takeIf { it != id && it.isNotEmpty() }
        }

    /**
     * The `display-list` query for a remote list shelf, as the server builds it for Home
     * (`startupDisplayListQueryForShelf`), without paging. Null for other shelves.
     */
    val remoteQuery: Map<String, String>?
        get() {
            fun v(s: String?) = s?.trim()?.ifEmpty { null }
            val q = mutableMapOf("name" to title)
            if (hideUnreleased == true) q["hideUnreleased"] = "true"
            when (type?.lowercase()) {
                "mdblist" -> {
                    q["source"] = "mdblist"
                    q["url"] = v(listUrl) ?: return null
                }
                "stremio" -> {
                    q["source"] = "stremio"
                    q["manifestUrl"] = v(addonManifestUrl) ?: return null
                    q["catalogType"] = v(addonCatalogType) ?: return null
                    q["catalogId"] = v(addonCatalogId) ?: return null
                }
                "tmdb" -> {
                    q["source"] = "tmdb-list"
                    q["sourceType"] = v(tmdbSourceType) ?: return null
                    q["sourceId"] = v(tmdbSourceId) ?: ""
                    q["mediaType"] = v(tmdbMediaType) ?: ""
                    q["sort"] = v(sort) ?: ""
                    q["discoverQuery"] = v(tmdbDiscoverQuery) ?: ""
                }
                "trakt" -> {
                    q["source"] = "trakt-list"
                    q["accountId"] = v(traktAccountId) ?: return null
                    q["listType"] = v(traktListType) ?: return null
                    v(traktListId)?.let { q["listId"] = it }
                }
                "publicmetadb" -> {
                    q["source"] = "publicmetadb-list"
                    q["accountId"] = v(publicMetaDBAccountId) ?: return null
                    q["listId"] = v(publicMetaDBListId) ?: return null
                }
                "simkl" -> {
                    q["source"] = "simkl-list"
                    q["accountId"] = v(simklAccountId) ?: return null
                    q["mediaType"] = v(simklMediaType) ?: return null
                    v(simklListType)?.let { q["listType"] = it }
                }
                "letterboxd" -> {
                    val listId = v(letterboxdListId)
                    val url = v(letterboxdListUrl)
                    if (listId == null && url == null) return null
                    q["source"] = "letterboxd-list"
                    listId?.let { q["listId"] = it }
                    url?.let { q["listUrl"] = it }
                }
                else -> return null
            }
            return q
        }

    /** The list a shelf opens: a remote list, or one of the built-in sources. */
    val browseList: BrowseList?
        get() {
            remoteQuery?.let { return BrowseList.Remote(id, title, it) }
            when (id) {
                "watchlist" -> return BrowseList.Watchlist
                "continue-watching" -> return BrowseList.ContinueWatching
                "trending-movies" -> return BrowseList.Trending(BrowseKind.MOVIES)
                "trending-tv" -> return BrowseList.Trending(BrowseKind.SHOWS)
                "popular-on-server" -> return BrowseList.PopularOnServer
                "permanent-prequeue" -> return BrowseList.PermanentPrequeue
            }
            // genre-{id}-{movie|tv} / decade-{year}-{movie|tv}
            val parts = id.split('-')
            val number = parts.getOrNull(1)?.toIntOrNull()
            if (parts.size == 3 && number != null) {
                val kind = if (parts[2] == "movie") BrowseKind.MOVIES else BrowseKind.SHOWS
                if (parts[0] == "genre") return BrowseList.Genre(number, title, kind)
                if (parts[0] == "decade") return BrowseList.Decade(number, kind)
            }
            return null
        }
}

/** A card of a collection hub shelf: opens another shelf's list. */
@Serializable
data class CollectionLink(
    val id: String,
    val name: String? = null,
    val enabled: Boolean? = null,
    val order: Int? = null,
    val sourceShelfId: String? = null,
    val logoUrl: String? = null,
    val heroArtUrl: String? = null,
)

/** A streaming service card: its top lists (usually movies and shows) from MDBList. */
@Serializable
data class StreamingService(
    val id: String,
    val name: String,
    val enabled: Boolean? = null,
    val order: Int? = null,
    val logoUrl: String? = null,
    val lists: List<ServiceList> = emptyList(),
) {
    @Serializable
    data class ServiceList(val key: String? = null, val title: String? = null, val url: String)

    companion object {
        /**
         * The configured services, or the defaults when the server has none. Disabled
         * services, services without lists, and repeated list URLs are dropped; retired URLs
         * are swapped for their replacements.
         */
        fun resolved(configured: List<StreamingService>?): List<StreamingService> {
            if (configured.isNullOrEmpty()) return defaults
            return configured.withIndex()
                .filter { (_, s) -> s.enabled != false && s.name.isNotEmpty() && !s.logoUrl.isNullOrEmpty() }
                .map { (index, s) ->
                    val seen = mutableSetOf<String>()
                    val lists = s.lists.mapNotNull { l ->
                        val url = retired(l.url)
                        if (url.isEmpty() || !seen.add(url)) null else l.copy(url = url)
                    }
                    s.copy(enabled = true, order = s.order ?: index, lists = lists)
                }
                .filter { it.lists.isNotEmpty() }
                .sortedBy { it.order ?: 0 }
        }

        private fun retired(url: String): String {
            val normalized = url.trim().trimEnd('/').removeSuffix("/json")
            return mapOf(
                "https://mdblist.com/lists/snoak/top-hulu-movies" to "https://mdblist.com/lists/azodath/top-hulu-movies/json",
                "https://mdblist.com/lists/snoak/top-tv-shows-hulu" to "https://mdblist.com/lists/azodath/top-hulu-shows/json",
            )[normalized] ?: url.trim()
        }

        private fun service(id: String, name: String, logo: String, movies: String, shows: String) = StreamingService(
            id, name, logoUrl = logo,
            lists = listOf(ServiceList("$id-movies", "Movies", movies), ServiceList("$id-shows", "Shows", shows)),
        )

        /** The Expo app's built-in services (`constants/streamingServices.ts`). */
        val defaults = listOf(
            service("netflix", "Netflix", "https://image.tmdb.org/t/p/original/wwemzKWzjKYJFfCeiB57q3r4Bcm.png",
                    "https://mdblist.com/lists/snoak/netflix-top-10-movies/json", "https://mdblist.com/lists/snoak/netflix-top-10-shows/json"),
            service("disney", "Disney+", "https://image.tmdb.org/t/p/original/1edZOYAfoyZyZ3rklNSiUpXX30Q.png",
                    "https://mdblist.com/lists/snoak/disney-plus-top-10-movies/json", "https://mdblist.com/lists/snoak/disney-plus-top-10-tv-shows/json"),
            service("amazon", "Amazon Prime", "https://image.tmdb.org/t/p/original/w7HfLNm9CWwRmAMU58udl2L7We7.png",
                    "https://mdblist.com/lists/snoak/amazon-prime-top-10-shows/json", "https://mdblist.com/lists/snoak/amazon-prime-top-10-tv-shows/json"),
            service("appletv", "Apple TV+", "https://image.tmdb.org/t/p/w780_filter(negate,000,fff)/bngHRFi794mnMq34gfVcm9nDxN1.png",
                    "https://mdblist.com/lists/snoak/apple-tv-top-10-movies/json", "https://mdblist.com/lists/snoak/apple-tv-top-10-tv-shows/json"),
            service("paramount", "Paramount+", "https://image.tmdb.org/t/p/original/fi83B1oztoS47xxcemFdPMhIzK.png",
                    "https://mdblist.com/lists/snoak/paramount-plus-top-10-movies/json", "https://mdblist.com/lists/snoak/paramount-plus-top-10-tv-shows/json"),
            service("hbomax", "HBO Max", "https://cdn.prod.website-files.com/6911e6b4bcc8af690d496a26/6911e6b4bcc8af690d496fed_nav-hbo-max.png",
                    "https://mdblist.com/lists/snoak/hbo-top-10-movies-2/json", "https://mdblist.com/lists/snoak/hbo-top-10-tv-shows/json"),
            service("hulu", "Hulu", "https://image.tmdb.org/t/p/original/pqUTCleNUiTLAVlelGxUgWn1ELh.png",
                    "https://mdblist.com/lists/azodath/top-hulu-movies/json", "https://mdblist.com/lists/azodath/top-hulu-shows/json"),
            service("crunchyroll", "Crunchyroll", "https://image.tmdb.org/t/p/original/qqyXcZlJQKlRmAD1TCKV7mGLQlt.png",
                    "https://mdblist.com/lists/snoak/trending-anime-movies/json", "https://mdblist.com/lists/snoak/trending-anime-shows/json"),
        )
    }
}

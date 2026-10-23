package com.strmr.tv.home

import com.strmr.tv.api.LibraryEntry
import com.strmr.tv.api.LibraryRef
import com.strmr.tv.api.Title
import kotlinx.serialization.Serializable

enum class BrowseKind(val mediaType: String, val noun: String) {
    MOVIES("movie", "Movies"),
    SHOWS("series", "Shows"),
}

/** A list Home opens as a grid (explore cards, services, hubs, Ask AI), read through `display-list`. */
sealed class BrowseList {
    data class Custom(val id: String, val name: String) : BrowseList()
    data object Watchlist : BrowseList()
    data object ContinueWatching : BrowseList()
    /** Titles hidden from this profile ([com.strmr.tv.library.HiddenItemsStore]), where they can be unhidden. */
    data object Hidden : BrowseList()
    /** Titles the server keeps resolved until they are removed. */
    data object PermanentPrequeue : BrowseList()
    /** Movies and shows together when the kind is null. */
    data class TopTen(val kind: BrowseKind? = null) : BrowseList()
    data class Trending(val kind: BrowseKind) : BrowseList()
    data object PopularOnServer : BrowseList()
    /** The server's recommendations from the profile's watch history. */
    data object Personalized : BrowseList()
    data class Genre(val genreId: Int, val name: String, val kind: BrowseKind) : BrowseList()
    data class Decade(val decade: Int, val kind: BrowseKind) : BrowseList()
    /** A list configured as a home shelf (MDBList, Stremio, TMDB, Trakt…), with its query. */
    data class Remote(val id: String, val name: String, val params: Map<String, String>) : BrowseList()
    /** A streaming service's top lists; the grid shows the first (movies) list. */
    data class Service(val service: StreamingService, val part: Int = 0) : BrowseList()
    /** "Because You Watched": titles like a Continue Watching title, with its backdrop for the tile. */
    data class Similar(val tmdbId: String, val kind: BrowseKind, val seed: String, val backdrop: String?) : BrowseList()
    /** A local, Plex or Jellyfin library, paged from `/api/library` ([com.strmr.tv.api.libraryPage]). */
    data class Library(val library: LibraryRef) : BrowseList()

    val title: String
        get() = when (this) {
            is Custom -> name
            Watchlist -> "Watchlist"
            ContinueWatching -> "Continue Watching"
            Hidden -> "Hidden"
            PermanentPrequeue -> "Permanent Prequeue"
            is TopTen -> when (kind) { null -> "Top 10 Today"; BrowseKind.MOVIES -> "Top 10 Movies"; BrowseKind.SHOWS -> "Top 10 Shows" }
            is Trending -> if (kind == BrowseKind.MOVIES) "Trending Movies" else "Trending Shows"
            PopularOnServer -> "Popular on This Server"
            Personalized -> "Recommended for You"
            is Genre -> name
            is Decade -> "${decade}s ${kind.noun}"
            is Remote -> name
            is Service -> service.name + (service.lists.getOrNull(part)?.title?.let { " · $it" } ?: "")
            is Similar -> "Because You Watched $seed"
            is Library -> library.name
        }

    /** Remote lists the server can page without reading the whole source first. */
    val loadsProgressively: Boolean
        get() = when (this) {
            is Remote -> params["source"]?.lowercase() in progressiveSources
            is Service -> true
            else -> false
        }

    /** How the source's `display-list` items are shaped: list items, or ranked titles. */
    val itemShaped: Boolean get() = this is Custom || this == Watchlist || this == PermanentPrequeue

    /** Sources whose items are bare titles (not ranked entries). */
    val titleShaped: Boolean get() = this is Similar

    /** `display-list` query parameters, without paging. */
    val query: Map<String, String>
        get() = when (this) {
            is Custom -> mapOf("source" to "custom-list", "listId" to id)
            Watchlist -> mapOf("source" to "watchlist")
            ContinueWatching -> mapOf("source" to "continue-watching")
            Hidden -> emptyMap()
            PermanentPrequeue -> mapOf("source" to "permanent-prequeue")
            is TopTen -> mapOf("source" to "top-ten") + (kind?.let { mapOf("mediaType" to it.mediaType) } ?: emptyMap())
            is Trending -> mapOf("source" to "trending", "mediaType" to kind.mediaType)
            PopularOnServer -> mapOf("source" to "popular-on-server")
            Personalized -> mapOf("source" to "personalized")
            is Genre -> mapOf("source" to "genre", "genreId" to genreId.toString(), "mediaType" to kind.mediaType)
            is Decade -> mapOf("source" to "decade", "decade" to decade.toString(), "mediaType" to kind.mediaType)
            is Remote -> params
            is Service -> mapOf("source" to "mdblist", "name" to service.name, "url" to (service.lists.getOrNull(part)?.url ?: ""))
            is Similar -> mapOf("source" to "similar", "tmdbId" to tmdbId, "mediaType" to kind.mediaType)
            is Library -> emptyMap()
        }

    companion object {
        private val progressiveSources = setOf(
            "mdblist", "trakt-list", "stremio", "simkl-list", "letterboxd-list", "publicmetadb-list", "tmdb-list",
        )

        /**
         * The server's AI picks for a description (`display-list?source=custom-ai&q=`), cached
         * server-side per description, so paging doesn't ask again.
         */
        fun askAI(prompt: String): BrowseList {
            val name = "Ask AI: $prompt"
            return Remote("custom-ai", name, mapOf("source" to "custom-ai", "q" to prompt, "name" to name))
        }
    }
}

/**
 * How much of a progressive list the server prepares before answering, as the Expo home asks
 * for it: the first cards fast, then the same page with full metadata and the exact total.
 */
enum class ShelfPhase(val value: String) {
    CARDS("cards"),
    COMPLETE("complete");

    val query: Map<String, String>
        get() = mapOf(
            "shelfPhase" to value, "lite" to "true", "deferArtwork" to (this == CARDS).toString(),
            "artworkLimit" to if (this == CARDS) "20" else "50",
        )
}

/** A watchlist / custom list entry (`display-list?source=watchlist`). */
@Serializable
data class WatchlistItem(
    val id: String,
    val mediaType: String = "movie",
    val name: String = "",
    val overview: String? = null,
    val year: Int? = null,
    val posterUrl: String? = null,
    val textPosterUrl: String? = null,
    val backdropUrl: String? = null,
    /** The backdrop with its title text, and ranked alternates: the launcher's 16:9 art. */
    val textBackdropUrl: String? = null,
    val backdropUrls: List<String>? = null,
    val logoUrl: String? = null,
    val externalIds: Map<String, String>? = null,
    /** none | partial | complete */
    val watchState: String? = null,
    val status: String? = null,
    val releaseDate: String? = null,
    val availability: String? = null,
) {
    val isMovie: Boolean get() = mediaType == "movie"
}

/** A list entry: discovery sources send titles, custom lists send list items. */
sealed interface BrowseEntry {
    data class TitleEntry(val title: Title) : BrowseEntry
    data class ItemEntry(val item: WatchlistItem) : BrowseEntry
    data class WatchingEntry(val item: com.strmr.tv.api.ContinueWatchingItem) : BrowseEntry
    data class LibraryItemEntry(val entry: LibraryEntry) : BrowseEntry
}

/** A page of a list, with the genre facets and first letters present when the source filters server-side. */
data class BrowsePage(val entries: List<BrowseEntry>, val total: Int, val genres: List<String> = emptyList(), val letters: List<String> = emptyList())

/** A custom list (`GET /api/users/{id}/custom-lists`). */
@Serializable
data class CustomList(val id: String, val name: String, val itemCount: Int? = null) {
    /** The reserved "Set Aside" list: titles parked out of Continue Watching. */
    val isSetAside: Boolean get() = name.trim().equals("Set Aside", ignoreCase = true)
}

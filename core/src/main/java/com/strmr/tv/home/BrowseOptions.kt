package com.strmr.tv.home

enum class BrowseSort(val value: String, val label: String) {
    DEFAULT("default", "Default"),
    ADDED("added", "Date Added"),
    NAME("name", "Name"),
    YEAR("year", "Year"),
    DURATION("duration", "Duration"),
    RATING("rating", "Rating");

    /** Names read A to Z; dates, lengths and ratings start at the top. */
    val ascendingByDefault: Boolean get() = this == NAME
}

/** Watch-status filter, in the server's vocabulary. */
enum class WatchFilter(val value: String, val label: String) {
    ALL("all", "Any Status"),
    UNWATCHED("unwatched", "Unwatched"),
    IN_PROGRESS("in_progress", "In Progress"),
    WATCHED("watched", "Watched"),
}

enum class RatingSource(val value: String, val label: String) {
    IMDB("imdb", "IMDb"),
    TMDB("tmdb", "TMDB"),
    TRAKT("trakt", "Trakt"),
    LETTERBOXD("letterboxd", "Letterboxd"),
    TOMATOES("tomatoes", "Rotten Tomatoes"),
    AUDIENCE("audience", "RT Audience"),
    METACRITIC("metacritic", "Metacritic"),
}

/** The browse grid's sort and filters. Changing any of them reloads the list. */
data class BrowseOptions(
    val sort: BrowseSort = BrowseSort.DEFAULT,
    val ascending: Boolean = false,
    val ratingSource: RatingSource = RatingSource.IMDB,
    val kind: BrowseKind? = null,
    val watch: WatchFilter = WatchFilter.ALL,
    val genres: Set<String> = emptySet(),
    val letter: String? = null,
) {
    val isFiltered: Boolean get() = kind != null || watch != WatchFilter.ALL || genres.isNotEmpty()

    /** Picks a sort, or flips the direction when it is already the current one. */
    fun choose(sort: BrowseSort): BrowseOptions {
        val next = if (this.sort == sort && sort != BrowseSort.DEFAULT) copy(ascending = !ascending)
        else copy(sort = sort, ascending = sort.ascendingByDefault)
        return if (sort != BrowseSort.NAME) next.copy(letter = null) else next
    }

    fun query(list: BrowseList): Map<String, String> = buildMap {
        if (sort != BrowseSort.DEFAULT && sort in list.sorts) {
            put("sortBy", sort.value)
            put("sortDirection", if (ascending) "asc" else "desc")
            if (sort == BrowseSort.RATING && list.filters) put("ratingSource", ratingSource.value)
        }
        if (!list.filters) return@buildMap
        put("includeFacets", "true")
        kind?.let { put("filterMediaType", it.mediaType) }
        if (watch != WatchFilter.ALL) put("watchStatus", watch.value)
        if (genres.isNotEmpty()) put("genres", genres.sorted().joinToString(","))
        letter?.let { put("alphabet", it) }
    }
}

/**
 * The sorts the server applies for this source. Custom lists are sorted and filtered by
 * `display-list` itself; genre and decade lists pass name / year / rating on to TMDB
 * discover (default: popularity); the charts come in their own order.
 */
val BrowseList.sorts: List<BrowseSort>
    get() = when (this) {
        is BrowseList.Custom, BrowseList.Watchlist, BrowseList.PermanentPrequeue -> BrowseSort.entries
        is BrowseList.Genre, is BrowseList.Decade, is BrowseList.Remote, is BrowseList.Service, is BrowseList.Trending ->
            listOf(BrowseSort.DEFAULT, BrowseSort.NAME, BrowseSort.YEAR, BrowseSort.RATING)
        is BrowseList.Library -> listOf(BrowseSort.DEFAULT, BrowseSort.ADDED, BrowseSort.NAME, BrowseSort.YEAR)
        else -> emptyList()
    }

/** The A–Z index under a Name sort: the profile's own lists and libraries. */
val BrowseList.indexesLetters: Boolean get() = filters || this is BrowseList.Library

/** Movies / Shows, watch status, genre facets and the A–Z index: the profile's own lists. */
val BrowseList.filters: Boolean
    get() = this is BrowseList.Custom || this == BrowseList.Watchlist || this == BrowseList.PermanentPrequeue

/** A random pick only makes sense past a handful of titles. */
val BrowseList.offersRandom: Boolean
    get() = this !is BrowseList.TopTen && this != BrowseList.ContinueWatching && this != BrowseList.Hidden

/** A TMDB genre; the server passes the id straight to TMDB discover. */
data class TmdbGenre(val id: Int, val name: String)

/** TMDB's genres for this kind; movie and TV genre ids differ. */
val BrowseKind.genres: List<TmdbGenre>
    get() = if (this == BrowseKind.MOVIES) movieGenres else tvGenres

/** Decades TMDB discover has enough titles for, newest first. */
val BrowseKind.decades: List<Int>
    get() = (2020 downTo (if (this == BrowseKind.MOVIES) 1930 else 1950) step 10).toList()

private val movieGenres = listOf(
    TmdbGenre(28, "Action"), TmdbGenre(12, "Adventure"), TmdbGenre(16, "Animation"), TmdbGenre(35, "Comedy"), TmdbGenre(80, "Crime"),
    TmdbGenre(99, "Documentary"), TmdbGenre(18, "Drama"), TmdbGenre(10751, "Family"), TmdbGenre(14, "Fantasy"), TmdbGenre(36, "History"),
    TmdbGenre(27, "Horror"), TmdbGenre(10402, "Music"), TmdbGenre(9648, "Mystery"), TmdbGenre(10749, "Romance"),
    TmdbGenre(878, "Science Fiction"), TmdbGenre(53, "Thriller"), TmdbGenre(10752, "War"), TmdbGenre(37, "Western"),
)

private val tvGenres = listOf(
    TmdbGenre(10759, "Action & Adventure"), TmdbGenre(16, "Animation"), TmdbGenre(35, "Comedy"), TmdbGenre(80, "Crime"),
    TmdbGenre(99, "Documentary"), TmdbGenre(18, "Drama"), TmdbGenre(10751, "Family"), TmdbGenre(10762, "Kids"), TmdbGenre(9648, "Mystery"),
    TmdbGenre(10764, "Reality"), TmdbGenre(10765, "Sci-Fi & Fantasy"), TmdbGenre(10768, "War & Politics"), TmdbGenre(37, "Western"),
)

package com.strmr.tv.home

import kotlinx.serialization.Serializable

// Home views: Movies, Shows and the profile's custom Home pages (`homeShelves.views`), a port
// of the Expo app's `utils/home-views.ts`. The server stores and merges them; the client
// resolves the layout and filters what it shows.

/** What a Home view shows: everything, or only movies or only series. */
enum class HomeMediaFilter(val raw: String) {
    ALL("all"), MOVIES("movies"), SHOWS("shows");

    /** The `mediaType` the server's list endpoints take (`homeViewMediaType`). */
    val mediaType: String get() = when (this) { MOVIES -> "movie"; SHOWS -> "series"; ALL -> "all" }

    companion object {
        fun from(raw: String?) = entries.firstOrNull { it.raw == raw }
    }
}

/**
 * One view's definition (`config.HomeViewSettings`). `mode` "custom" replaces Home's layout
 * fields with the view's own; "inherit" keeps Home's. Null `shelves` inherit Home's shelves;
 * an empty list is an intentionally empty page.
 */
@Serializable
data class HomeViewSettings(
    val name: String? = null,
    val icon: String? = null,
    val mediaFilter: String? = null,
    val deleted: Boolean? = null,
    val mode: String? = null,
    val shelves: List<ShelfConfig>? = null,
    val itemCap: Int? = null,
    val exploreCardPosition: String? = null,
    val excludeUpcomingFromContinue: Boolean? = null,
    val homeShelfFocusModel: String? = null,
    val disableTvLandscapeCardExpansion: Boolean? = null,
    val homeShelfScale: Double? = null,
    val homeHeroScale: Double? = null,
    val tvTopShelfMode: String? = null,
    val tvTopShelfSourceId: String? = null,
)

object HomeViews {
    const val ALL = "all"
    const val MOVIES = "movies"
    const val SHOWS = "shows"

    private val customId = Regex("^page-[a-zA-Z0-9-]{1,80}$")

    fun isCustom(id: String?) = id != null && customId.matches(id)

    /** A stored view ID, or All for anything else (`parseHomeView`). */
    fun parse(id: String?): String = if (id == MOVIES || id == SHOWS || isCustom(id)) id!! else ALL

    /**
     * The profile's definitions over the server's, by ID (`mergeHomeViews`). Only an empty name,
     * icon or media filter is taken from the server's entry; shelf lists never merge.
     */
    fun merge(global: Map<String, HomeViewSettings>?, profile: Map<String, HomeViewSettings>?): Map<String, HomeViewSettings> {
        val result = (global ?: emptyMap()).toMutableMap()
        for ((id, override) in profile ?: emptyMap()) {
            val inherited = global?.get(id)
            result[id] = override.copy(
                name = override.name?.ifEmpty { null } ?: inherited?.name,
                icon = override.icon?.ifEmpty { null } ?: inherited?.icon,
                mediaFilter = override.mediaFilter?.ifEmpty { null } ?: inherited?.mediaFilter,
            )
        }
        return result
    }

    /** What a view shows (`homeViewFilter`): Movies and Shows by ID, a page by its `mediaFilter`. */
    fun filter(view: String, views: Map<String, HomeViewSettings>): HomeMediaFilter {
        if (view == MOVIES) return HomeMediaFilter.MOVIES
        if (view == SHOWS) return HomeMediaFilter.SHOWS
        val definition = views[view]
        if (definition?.deleted == true) return HomeMediaFilter.ALL
        return when (definition?.mediaFilter) {
            "movies" -> HomeMediaFilter.MOVIES
            "shows" -> HomeMediaFilter.SHOWS
            else -> HomeMediaFilter.ALL
        }
    }

    /** All, Movies, Shows, then the pages that aren't deleted, by name (`homeViewIds`). */
    fun ids(views: Map<String, HomeViewSettings>): List<String> = listOf(ALL, MOVIES, SHOWS) +
        views.keys.filter { isCustom(it) && views[it]?.deleted != true }
            .sortedWith(compareBy<String, String>(String.CASE_INSENSITIVE_ORDER) { label(it, views) }.thenBy { it })

    /** "Home", "Movies", "Shows", or the page's name (`homeViewLabel`). */
    fun label(view: String, views: Map<String, HomeViewSettings>): String = when (view) {
        ALL -> "Home"
        MOVIES -> "Movies"
        SHOWS -> "Shows"
        else -> views[view]?.name?.takeIf { it.isNotEmpty() } ?: "Home page"
    }
}

/** Whether an item of this media type belongs in a view (`matchesHomeView`). */
fun matchesHomeView(mediaType: String?, filter: HomeMediaFilter): Boolean {
    if (filter == HomeMediaFilter.ALL) return true
    val type = mediaType?.lowercase() ?: ""
    return if (filter == HomeMediaFilter.MOVIES) type in movieTypes else type in showTypes
}

private val movieTypes = setOf("movie", "movies", "film", "films")
private val showTypes = setOf("tv", "series", "show", "shows", "episode")

private val shelfMediaSuffix = Regex("(?:genre-\\d+|decade-\\d+)-(movie|tv)$")

/**
 * Whether a shelf belongs in a view (`homeViewShelfAllowed`): Movies and Shows drop the live,
 * dashboard and tonight rows and the other kind's rows (by the shelf's addon / TMDB / Simkl
 * media type, or a `genre-N-movie|tv` / `decade-N-movie|tv` ID).
 */
fun homeViewShelfAllowed(shelf: ShelfConfig, filter: HomeMediaFilter): Boolean {
    if (filter == HomeMediaFilter.ALL) return true
    if (shelf.id in setOf("live-favorites", "dashboard", "tonight")) return false
    if (filter == HomeMediaFilter.MOVIES && shelf.id in setOf("trending-tv", "my-upcoming", "my-recently-aired")) return false
    if (filter == HomeMediaFilter.SHOWS && shelf.id == "trending-movies") return false
    val type = shelf.addonCatalogType?.ifEmpty { null }
        ?: shelf.tmdbMediaType?.ifEmpty { null }
        ?: shelf.simklMediaType?.ifEmpty { null }
        ?: shelfMediaSuffix.find(shelf.id)?.groupValues?.get(1)
    return type == null || type == "all" || matchesHomeView(type, filter)
}

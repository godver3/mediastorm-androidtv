package com.strmr.tv.home

import com.strmr.tv.api.ApiClient
import com.strmr.tv.api.ContinueWatchingItem
import com.strmr.tv.api.DisplayListPage
import com.strmr.tv.api.ListedTitle
import com.strmr.tv.api.LossyListSerializer
import com.strmr.tv.api.Title
import com.strmr.tv.app.Config
import java.util.TimeZone
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

@Serializable private data class DisplayFields(val hideWatched: Boolean? = null)

@Serializable private data class UserHome(val homeShelves: HomeFields? = null, val display: DisplayFields? = null)

@Serializable private data class MetadataFields(val aiApiKey: String? = null, val geminiApiKey: String? = null)

@Serializable private data class ServerHome(val metadata: MetadataFields? = null, val homeShelves: HomeFields? = null)

@Serializable private data class DeviceHome(val homeShelfFocusModel: String? = null)

@Serializable private data class Manifest(val revision: String? = null)

@Serializable private data class CalendarResponse(
    @Serializable(with = LossyListSerializer::class) val items: List<CalendarItem> = emptyList(),
    /** The server has no calendar for the profile yet and is building one: ask again shortly. */
    val pending: Boolean = false,
)

/** Home's calendar is still being built on the server (right after a restart); its rows keep loading and retry. */
class HomeCalendarPending : Exception("calendar is still building")

@Serializable private data class DashboardResponse(
    @Serializable(with = LossyListSerializer::class) val streams: List<DashboardStream> = emptyList(),
)

@Serializable private data class SurprisePick(val title: Title)

/**
 * The profile's `homeShelves` config resolved for a Home view (see [resolveHomeView]), layered
 * with the server's AI key presence and this device's focus position (set in the admin UI).
 */
suspend fun ApiClient.homeSettings(view: String = HomeViews.ALL): HomeSettings = coroutineScope {
    // The server's own settings only say whether an AI key is set (masked); a failure there
    // just hides the AI-backed Watch Something cards.
    val server = async { runCatching { send<ServerHome>("GET", "/api/settings", timeoutMs = 10_000) }.getOrNull() }
    val device = async {
        runCatching {
            send<DeviceHome>("GET", "/api/clients/${segment(Config.clientId)}/settings", mapOf("userId" to profileId),
                             promptOnLock = false, timeoutMs = 10_000)
        }.getOrNull()
    }
    val settings = send<UserHome>("GET", "$profilePath/settings")
    val serverHome = server.await()
    val metadata = serverHome?.metadata
    val views = HomeViews.merge(serverHome?.homeShelves?.views, settings.homeShelves?.views)
    // A view that no longer exists is Home itself; the caller falls back to All.
    val shown = if (view in HomeViews.ids(views)) view else HomeViews.ALL
    val home = resolveHomeView(serverHome?.homeShelves, settings.homeShelves, shown)
    HomeSettings(
        shelves = (home.shelves ?: emptyList()).sortedBy { it.order ?: 0 },
        itemCap = home.itemCap?.takeIf { it > 0 } ?: 20,
        exploreFirst = home.exploreCardPosition != "end",
        hideWatched = settings.display?.hideWatched ?: false,
        excludeUpcomingFromContinue = home.excludeUpcomingFromContinue ?: true,
        layout = ProfileHomeLayout.from(
            focusModel = device.await()?.homeShelfFocusModel ?: home.homeShelfFocusModel,
            disableCardExpansion = home.disableTvLandscapeCardExpansion,
            shelfScale = home.homeShelfScale,
            heroScale = home.homeHeroScale,
        ),
        hasAI = listOf(metadata?.aiApiKey, metadata?.geminiApiKey).any { !it.isNullOrEmpty() },
        topShelfMode = TopShelfMode.from(home.tvTopShelfMode),
        topShelfSourceId = home.tvTopShelfSourceId,
        view = shown,
        filter = HomeViews.filter(shown, views),
        views = views,
    )
}

/**
 * A fingerprint of everything Home shows that the server tracks (settings, shelf config,
 * Continue Watching, watchlist, hidden titles); null when it can't be read.
 */
suspend fun ApiClient.homeRevision(): String? =
    runCatching { send<Manifest>("GET", "$profilePath/home/manifest", promptOnLock = false, timeoutMs = 15_000) }.getOrNull()?.revision

/**
 * Continue Watching, minus what the profile put aside. Titles hidden with
 * [hideFromContinueWatching] are dropped server-side; the "Set Aside" custom list is
 * filtered here, as the other clients do.
 */
suspend fun ApiClient.continueWatching(): List<ContinueWatchingItem> = coroutineScope {
    val rows = async { sendList<ContinueWatchingItem>("GET", "$profilePath/history/continue") }
    // None when the list is missing or unreadable, so a failure here never costs the row.
    val aside = async {
        runCatching { customLists().firstOrNull { it.isSetAside }?.let { customListItems(it.id) } }.getOrNull().orEmpty()
    }
    val items = rows.await()
    val parked = aside.await()
    if (parked.isEmpty()) items else items.filterNot { item -> parked.any { it.matches(item) } }
}

/** Drops a title from Continue Watching until it is played again. */
suspend fun ApiClient.hideFromContinueWatching(seriesId: String) {
    sendRaw("POST", "$profilePath/history/continue/hide", body = buildJsonObject { put("seriesId", seriesId) })
}

/** The same title: its ID, or a shared provider ID. */
private fun WatchlistItem.matches(item: ContinueWatchingItem): Boolean {
    if (id == item.seriesId) return true
    val mine = externalIds ?: return false
    val theirs = item.externalIds ?: return false
    return listOf("tmdb", "imdb", "tvdb").any { key -> mine[key]?.takeIf { it.isNotEmpty() }?.let { it == theirs[key] } == true }
}

/**
 * The calendar window Home's calendar rows use: two weeks ahead and the recent past. Throws
 * [HomeCalendarPending] while the server is still building the profile's calendar.
 */
suspend fun ApiClient.homeCalendar(limit: Int): List<CalendarItem> {
    val response = send<CalendarResponse>(
        "GET", "$profilePath/calendar",
        mapOf("days" to "14", "recentDays" to "90", "compact" to "1", "home" to "1", "limit" to limit.toString(),
              "tz" to TimeZone.getDefault().id),
        timeoutMs = 60_000,
    )
    if (response.pending && response.items.isEmpty()) throw HomeCalendarPending()
    return response.items
}

/** Streams playing on the server right now, for the Dashboard row. */
suspend fun ApiClient.dashboardStreams(): List<DashboardStream> =
    send<DashboardResponse>("GET", "/api/dashboard/shelf", promptOnLock = false, timeoutMs = 15_000).streams

/** A random title for Watch Something's Surprise Me card (needs an AI key on the server), of the view's kind. */
suspend fun ApiClient.surprise(filter: HomeMediaFilter = HomeMediaFilter.ALL): Title = send<SurprisePick>(
    "GET", "/api/recommendations/surprise",
    mapOf("userId" to profileId) + if (filter == HomeMediaFilter.ALL) emptyMap() else mapOf("mediaType" to filter.mediaType),
    timeoutMs = 60_000,
).title

suspend fun ApiClient.customLists(): List<CustomList> = sendList("GET", "$profilePath/custom-lists")

suspend fun ApiClient.customListItems(id: String): List<WatchlistItem> =
    sendList("GET", "$profilePath/custom-lists/${segment(id)}/items")

/**
 * One page of a list, as titles whatever the source's item shape. `extra` adds query
 * parameters (Home's `hideWatched` / `hideUnreleased`). With a `phase`, a remote list that
 * pages progressively reads only enough of its source to fill the page.
 */
suspend fun ApiClient.browsePage(
    list: BrowseList, limit: Int, offset: Int = 0, extra: Map<String, String> = emptyMap(), phase: ShelfPhase? = null,
    options: BrowseOptions? = null,
): BrowsePage {
    // Continue Watching's own shape: the whole feed, paged here.
    if (list == BrowseList.ContinueWatching) {
        // Opened from a Home view: only that view's kind, as its row shows.
        val filter = extra["homeView"]?.let { HomeViews.filter(it, HomeViewStore.views) } ?: HomeMediaFilter.ALL
        val all = continueWatching().filter { matchesHomeView(if (it.nextEpisode != null) "series" else "movie", filter) }
        return BrowsePage(all.drop(offset).take(limit).map { BrowseEntry.WatchingEntry(it) }, all.size)
    }
    // Hidden titles come from the profile's hidden items, newest first.
    if (list == BrowseList.Hidden) {
        com.strmr.tv.library.HiddenItemsStore.refresh()
        val all = com.strmr.tv.library.HiddenItemsStore.items.reversed().map {
            WatchlistItem(it.id, if (it.isMovie) "movie" else "series", it.name, year = it.year, posterUrl = it.posterUrl,
                          backdropUrl = it.backdropUrl, externalIds = it.externalIds)
        }
        return BrowsePage(all.drop(offset).take(limit).map { BrowseEntry.ItemEntry(it) }, all.size)
    }
    val query = (list.query + extra + (options?.query(list) ?: emptyMap())).toMutableMap()
    query["limit"] = limit.toString()
    query["offset"] = offset.toString()
    if (phase != null && list.loadsProgressively) query += phase.query
    // An AI answer can take well over the usual 30 s the first time it is asked, and so can a
    // long list's complete pass on a cold server (it runs behind cards already showing).
    val timeout = when {
        query["source"] == "custom-ai" -> 120_000L
        phase == ShelfPhase.COMPLETE && list.loadsProgressively -> 180_000L
        else -> 30_000L
    }
    val path = "$profilePath/display-list"
    fun <T> DisplayListPage<T>.page(entry: (T) -> BrowseEntry) = BrowsePage(
        items.map(entry), maxOf(sourceTotal ?: total, items.size), genres.orEmpty(), alphabetBuckets.orEmpty(),
    )
    return when {
        list.itemShaped -> send<DisplayListPage<WatchlistItem>>("GET", path, query, timeoutMs = timeout).page { BrowseEntry.ItemEntry(it) }
        list.titleShaped -> send<DisplayListPage<Title>>("GET", path, query, timeoutMs = timeout).page { BrowseEntry.TitleEntry(it) }
        else -> send<DisplayListPage<ListedTitle>>("GET", path, query, timeoutMs = timeout).page { BrowseEntry.TitleEntry(it.title) }
    }
}

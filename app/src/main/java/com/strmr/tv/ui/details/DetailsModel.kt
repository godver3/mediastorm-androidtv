package com.strmr.tv.ui.details

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.strmr.tv.api.ApiClient
import com.strmr.tv.api.ApiError
import com.strmr.tv.api.CastMember
import com.strmr.tv.api.DetailsTarget
import com.strmr.tv.api.Episode
import com.strmr.tv.api.EpisodeRef
import com.strmr.tv.api.ResumeState
import com.strmr.tv.api.Season
import com.strmr.tv.api.SourceResult
import com.strmr.tv.api.StartPoint
import com.strmr.tv.api.Title
import com.strmr.tv.api.TrailerList
import com.strmr.tv.api.collectionMovies
import com.strmr.tv.api.detailsBundle
import com.strmr.tv.api.formatRuntime
import com.strmr.tv.api.libraryMatches
import com.strmr.tv.api.setWatched
import com.strmr.tv.home.CardBadge
import com.strmr.tv.home.ReleaseBadge
import com.strmr.tv.library.LibraryVersion
import com.strmr.tv.library.LibraryVersions
import com.strmr.tv.player.ManualSourceTracks
import com.strmr.tv.player.PlaybackCoordinator
import com.strmr.tv.player.PrequeuePick
import com.strmr.tv.player.PlaybackRequest
import com.strmr.tv.player.playLibrary
import kotlin.random.Random
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull

/**
 * A details page's data and decisions, from one `/details-bundle` call: metadata, seasons
 * with per-episode watched / resume, the up-next episode and the server's resume decision
 * (no client thresholds). Reloaded after playback to refresh progress.
 */
class DetailsModel(val target: DetailsTarget) {
    var title by mutableStateOf<Title?>(null)
        private set
    var seasons by mutableStateOf<List<Season>>(emptyList())
        private set
    var selectedSeason by mutableStateOf<Int?>(null)
    /** The episode the primary button plays: the server's up-next episode, else the first one. */
    var primaryEpisode by mutableStateOf<Episode?>(null)
        private set
    /** The episode spoiler blurring counts from (see [EpisodePrivacy.currentEpisode]). */
    var privacyEpisode by mutableStateOf<Episode?>(null)
        private set
    var loadError by mutableStateOf<String?>(null)
        private set
    var trailers by mutableStateOf<TrailerList?>(null)
        private set
    /** The bundle's `inWatchlist` (null on older servers); WatchlistStore layers toggles on top. */
    var serverInWatchlist by mutableStateOf<Boolean?>(null)
        private set
    /** The bundle's movie `watched` (null on older servers or for series). */
    var movieWatched by mutableStateOf<Boolean?>(null)
        private set
    /** Watched changes made here, shown until the reload after the write lands (by episode ID). */
    val watchedOverrides = mutableStateMapOf<String, Boolean>()
    var watchedError by mutableStateOf<String?>(null)
        private set
    /** More Like This (the bundle's, de-duplicated). */
    var similar by mutableStateOf<List<Title>>(emptyList())
        private set
    /** Movies: the rest of the movie's collection, in release order. */
    var collectionMovies by mutableStateOf<List<Title>>(emptyList())
        private set
    private var watchedBusy = false
    private var upNext: EpisodeRef? = null
    /** Server resume decision for the movie, or for `upNext`. */
    private var resume: ResumeState? = null
    /** Fixed per page, so the stream-start art order doesn't reshuffle between plays. */
    private val artSeed = Random.nextLong()
    /**
     * The title's copies in the profile's local, Plex and Jellyfin libraries (null: none, or not
     * looked up yet). Play uses the best copy of the movie or episode (local first, by user
     * decision); Versions lists them all, and Choose Source still searches for streams.
     */
    var library by mutableStateOf<LibraryVersions?>(null)
        private set
    /** Completes once the libraries have been asked (with the copies, or null). */
    private val libraryLookup = CompletableDeferred<LibraryVersions?>()

    /** Looks the title up in the libraries, once per page (not on every reload after playback). */
    suspend fun loadLibrary() {
        if (libraryLookup.isCompleted) return
        val versions = runCatching { LibraryVersions.of(ApiClient.libraryMatches(target)) }.getOrNull()?.takeUnless { it.isEmpty }
        library = versions
        libraryLookup.complete(versions)
    }

    /** The copies of a movie (no episode) or of one episode, best first. */
    fun libraryVersions(episode: Episode?): List<LibraryVersion> {
        val all = library ?: return emptyList()
        return if (episode == null) { if (target.isMovie) all.movie else emptyList() } else all.versions(episode.seasonNumber, episode.episodeNumber)
    }

    /** The copies of what the primary button plays. */
    val primaryVersions: List<LibraryVersion> get() = libraryVersions(if (target.isMovie) null else primaryEpisode)

    /** Specials last, so the season chips open on real seasons. */
    val visibleSeasons: List<Season> get() = seasons.filter { it.number > 0 } + seasons.filter { it.number == 0 }
    val currentSeason: Season? get() = visibleSeasons.firstOrNull { it.number == selectedSeason } ?: visibleSeasons.firstOrNull()

    suspend fun load() {
        try {
            val bundle = ApiClient.detailsBundle(target)
            if (target.isMovie) {
                title = bundle.movieDetails ?: throw ApiError.NotReady("Details unavailable")
            } else {
                val details = bundle.seriesDetails ?: throw ApiError.NotReady("Details unavailable")
                title = details.title
                seasons = details.seasons
                upNext = bundle.upNextEpisode
                primaryEpisode = findPrimaryEpisode(details.seasons, bundle.upNextEpisode)
                privacyEpisode = EpisodePrivacy.currentEpisode(details.seasons, bundle.upNextEpisode)
                if (selectedSeason == null) selectedSeason = primaryEpisode?.seasonNumber
            }
            resume = bundle.resume
            trailers = bundle.trailers
            serverInWatchlist = bundle.inWatchlist
            movieWatched = bundle.watched
            similar = bundle.similar.orEmpty().distinctBy { it.id }
            watchedOverrides.clear()
            loadError = null
        } catch (e: Exception) {
            if (title == null) loadError = e.message
        }
        title?.collection?.let { c ->
            if (collectionMovies.isEmpty()) collectionMovies = runCatching { ApiClient.collectionMovies(c.id) }.getOrDefault(emptyList()).distinctBy { it.id }
        }
    }

    /** The top-billed cast, as many as the Expo TV row shows. */
    val cast: List<CastMember> get() = title?.credits?.cast.orEmpty().distinctBy { it.id }.take(10)

    fun isWatched(episode: Episode): Boolean = watchedOverrides[episode.id] ?: (episode.watched == true)

    /** Series: none / some / all aired regular-season episodes watched (the Expo rule). */
    val seriesWatchState: WatchState
        get() {
            val aired = seasons.filter { it.number > 0 }.flatMap { it.episodes }.filter(::hasAired)
            val watched = aired.count(::isWatched)
            return when {
                watched == 0 -> WatchState.NONE
                watched == aired.size -> WatchState.COMPLETE
                else -> WatchState.PARTIAL
            }
        }

    /** Repeat presses while a change is in flight are ignored. */
    suspend fun toggleMovieWatched() {
        if (watchedBusy) return
        watchedBusy = true
        val watched = movieWatched != true
        try {
            ApiClient.setWatched(watched, target)
            movieWatched = watched
            watchedError = null
            com.strmr.tv.library.TitleActions.changed()
        } catch (e: Exception) {
            watchedError = e.message
        } finally {
            watchedBusy = false
        }
    }

    suspend fun toggleWatched(episode: Episode) {
        val watched = !isWatched(episode)
        watchedOverrides[episode.id] = watched
        try {
            ApiClient.setWatched(watched, listOf(episode), target)
            watchedError = null
            com.strmr.tv.library.TitleActions.changed()
        } catch (e: Exception) {
            watchedOverrides.remove(episode.id)
            watchedError = e.message
        }
    }

    /** Where to resume, per the server's `resume.eligible` decision. */
    fun resumePoint(episode: Episode?): StartPoint? {
        if (episode == null) return resume?.startPoint
        episode.resume?.let { return it.startPoint }
        val next = upNext ?: return null
        return if (isUpNext(episode, next)) resume?.startPoint else null
    }

    /** The episode / resume point the primary button plays, and its label. */
    data class Primary(val episode: Episode?, val start: StartPoint?, val label: String)

    val primary: Primary?
        get() {
            if (target.isMovie) {
                val at = resumePoint(null)
                return if (at != null) Primary(null, at, "Resume from ${at.label}") else Primary(null, null, "Play")
            }
            val ep = primaryEpisode ?: return null
            val at = resumePoint(ep)
            return if (at != null) Primary(ep, at, "Resume ${ep.code} · ${at.label}") else Primary(ep, null, "Play ${ep.code}")
        }

    /**
     * Play, through the library when it has the movie or episode. A press while the lookup is still
     * running waits for it, so a library title never starts a search by mistake.
     */
    fun play(episode: Episode?, start: StartPoint?, picks: PrequeuePick? = null) {
        if (libraryLookup.isCompleted) return PlaybackCoordinator.play(request(episode, start), picks)
        MainScope().launch {
            val versions = withTimeoutOrNull(8_000) { libraryLookup.await() }
            PlaybackCoordinator.play(request(episode, start).copy(library = versions), picks)
        }
    }

    /** A copy picked in Versions. */
    fun playVersion(version: LibraryVersion, episode: Episode?, start: StartPoint?) =
        PlaybackCoordinator.playLibrary(request(episode, start), version)

    fun play(source: SourceResult, episode: Episode?, start: StartPoint?, manual: ManualSourceTracks? = null) =
        PlaybackCoordinator.play(request(episode, start), source, manual)

    fun request(episode: Episode?, start: StartPoint?): PlaybackRequest {
        val primaryBackdrop = title?.backdrop?.url ?: target.backdropUrl
        val alternates = title?.backdrops?.map { it.url }.orEmpty()
        val art = (listOfNotNull(primaryBackdrop) + alternates).distinct().shuffled(Random(artSeed))
        return PlaybackRequest(
            title = target,
            episode = episode?.let(::episodeInfo),
            start = start,
            backdrops = listOfNotNull(episode?.image?.url) + art,
            logoUrl = title?.logo?.url ?: target.logoUrl,
            following = episode?.let { airedEpisodes(it) }.orEmpty(),
            library = library,
        )
    }

    private fun episodeInfo(e: Episode) = PlaybackRequest.EpisodeInfo(
        e.seasonNumber, e.episodeNumber, e.absoluteEpisodeNumber, e.name, e.itemId, e.image?.url, e.overview,
    )

    /** The aired regular-season episodes after `after`, for Up Next. */
    private fun airedEpisodes(after: Episode): List<PlaybackRequest.EpisodeInfo> {
        val regular = seasons.filter { it.number > 0 }.sortedBy { it.number }.flatMap { it.episodes }
        val index = regular.indexOfFirst { it.id == after.id }
        if (index < 0) return emptyList()
        return regular.drop(index + 1).takeWhile(::hasAired).map(::episodeInfo)
    }

    /** The title's release badge, else (series) whether the episode Play targets has aired. */
    val headerBadge: CardBadge?
        get() {
            title?.let { t -> ReleaseBadge.forTitle(t.availability, t.status, t.releaseDate)?.let { return it } }
            if (target.isMovie) return null
            return primaryEpisode?.let { ReleaseBadge.forEpisode(it.airedDate) }?.let { CardBadge(it) }
        }

    val metadataLine: String
        get() {
            val parts = mutableListOf<String>()
            (title?.year ?: target.year)?.let { parts += it.toString() }
            title?.certification?.takeIf { it.isNotEmpty() }?.let { parts += it }
            formatRuntime(title?.runtimeMinutes)?.let { parts += it }
            if (seasons.isNotEmpty()) {
                val n = seasons.count { it.number > 0 }
                parts += if (n == 1) "1 Season" else "$n Seasons"
            }
            title?.genres?.takeIf { it.isNotEmpty() }?.let { parts += it.take(3).joinToString(", ") }
            return parts.joinToString("  ·  ")
        }

    enum class WatchState { NONE, PARTIAL, COMPLETE }

    companion object {
        /** Aired: no upcoming-release badge (only aired episodes are marked watched in bulk). */
        fun hasAired(episode: Episode): Boolean = ReleaseBadge.forEpisode(episode.airedDate) == null

        fun findPrimaryEpisode(seasons: List<Season>, upNext: EpisodeRef?): Episode? {
            if (upNext != null) {
                for (season in seasons) season.episodes.firstOrNull { isUpNext(it, upNext) }?.let { return it }
            }
            val first = seasons.firstOrNull { it.number > 0 } ?: seasons.firstOrNull()
            return first?.episodes?.firstOrNull()
        }

        fun isUpNext(episode: Episode, ref: EpisodeRef): Boolean {
            val a = episode.itemId
            val b = ref.itemId
            if (a != null && b != null) return a == b
            return episode.seasonNumber == ref.seasonNumber && episode.episodeNumber == ref.episodeNumber
        }
    }
}

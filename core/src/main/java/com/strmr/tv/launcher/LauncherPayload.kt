package com.strmr.tv.launcher

import com.strmr.tv.api.Artwork
import com.strmr.tv.api.ContinueWatchingItem
import com.strmr.tv.api.DetailsTarget
import com.strmr.tv.home.WatchlistItem
import com.strmr.tv.home.isUpcoming
import java.time.OffsetDateTime

/** One launcher card: a preview program in a channel and, for Continue Watching, a Watch Next program. */
data class LauncherProgram(
    /** Stable per title; stored as `strmr:<id>` (the provider's internal ID) so the app finds its own rows. */
    val id: String,
    val title: String,
    val description: String,
    val kind: Kind,
    val seasonNumber: Int? = null,
    val episodeNumber: Int? = null,
    val episodeTitle: String? = null,
    val imageUrl: String?,
    /** The art's shape: the launcher's backdrop art is 16:9, a poster fallback 2:3. */
    val landscape: Boolean,
    val displayUrl: String,
    val playUrl: String,
    val positionMs: Long? = null,
    val durationMs: Long? = null,
    val lastEngagementMs: Long? = null,
    /** Watch Next only: resume where it stopped, or the next episode. */
    val watchNext: WatchNext? = null,
) {
    enum class Kind { MOVIE, EPISODE, SERIES }
    enum class WatchNext { CONTINUE, NEXT }
}

/** What the launcher shows for a profile: the two channels; Watch Next mirrors Continue Watching. */
data class LauncherPayload(val continueWatching: List<LauncherProgram>, val watchlist: List<LauncherProgram>) {
    val isEmpty: Boolean get() = continueWatching.isEmpty() && watchlist.isEmpty()

    companion object {
        /** As in the Expo Android build (`ANDROID_MAX_ITEMS_PER_SECTION`): 20 per channel. */
        const val MAX_PER_SECTION = 20
        /** Expo's progress without a known duration: tenths of a percent of a nominal 1000 ms. */
        private const val NOMINAL_DURATION_MS = 1000L

        /**
         * Continue Watching without episodes that haven't aired (they can't be resumed from the
         * launcher, as on tvOS), and the watchlist (without finished titles when the profile hides
         * watched ones). [resolve] makes server-relative art absolute for the launcher to fetch.
         */
        fun build(
            continueWatching: List<ContinueWatchingItem>,
            watchlist: List<WatchlistItem>,
            profileId: String?,
            hideWatched: Boolean = false,
            now: Long = System.currentTimeMillis(),
            resolve: (String) -> String? = { it },
        ): LauncherPayload {
            val resuming = continueWatching.filterNot { it.isUpcoming }.take(MAX_PER_SECTION).mapIndexed { index, item ->
                program(item, profileId, now - index * 1000L, resolve)
            }
            val saved = watchlist.filter { !hideWatched || it.watchState != "complete" }.take(MAX_PER_SECTION).map {
                program(it, profileId, resolve)
            }
            return LauncherPayload(resuming, saved)
        }

        private fun program(item: ContinueWatchingItem, profileId: String?, fallbackEngagement: Long, resolve: (String) -> String?): LauncherProgram {
            val target = DetailsTarget.from(item)
            val ep = item.nextEpisode
            val (position, duration) = progress(item)
            val (image, landscape) = art(item.textBackdropUrl, item.backdropUrl, item.backdropUrls, item.textPosterUrl, item.posterUrl, resolve)
            return LauncherProgram(
                id = item.seriesId,
                title = item.seriesTitle,
                description = if (ep != null) "Up next: S${ep.seasonNumber} E${ep.episodeNumber}" else item.overview ?: "Continue watching",
                kind = if (item.isMovie) LauncherProgram.Kind.MOVIE else LauncherProgram.Kind.EPISODE,
                seasonNumber = ep?.seasonNumber,
                episodeNumber = ep?.episodeNumber,
                episodeTitle = ep?.title?.takeIf { it.isNotBlank() },
                imageUrl = image,
                landscape = landscape,
                displayUrl = DeepLink(DeepLink.Action.DETAILS, target, profileId).url,
                playUrl = DeepLink(DeepLink.Action.PLAY, target, profileId).url,
                positionMs = position,
                durationMs = duration,
                lastEngagementMs = engagement(item.updatedAt) ?: fallbackEngagement,
                // Partway through it: Continue; a series waiting on its next episode: Next.
                watchNext = if (ep != null && position == null) LauncherProgram.WatchNext.NEXT else LauncherProgram.WatchNext.CONTINUE,
            )
        }

        private fun program(item: WatchlistItem, profileId: String?, resolve: (String) -> String?): LauncherProgram {
            val target = DetailsTarget(
                titleId = item.id, name = item.name, isMovie = item.isMovie, year = item.year,
                tmdbId = item.externalIds?.get("tmdb"), imdbId = item.externalIds?.get("imdb"), tvdbId = item.externalIds?.get("tvdb"),
                posterUrl = item.posterUrl, textPosterUrl = item.textPosterUrl, backdropUrl = item.backdropUrl, logoUrl = item.logoUrl,
            )
            val (image, landscape) = art(item.textBackdropUrl, item.backdropUrl, item.backdropUrls, item.textPosterUrl, item.posterUrl, resolve)
            return LauncherProgram(
                id = "${item.mediaType}:${item.id}",
                title = item.name,
                description = item.overview?.takeIf { it.isNotBlank() } ?: "Saved in your watchlist",
                kind = if (item.isMovie) LauncherProgram.Kind.MOVIE else LauncherProgram.Kind.SERIES,
                imageUrl = image,
                landscape = landscape,
                displayUrl = DeepLink(DeepLink.Action.DETAILS, target, profileId).url,
                playUrl = DeepLink(DeepLink.Action.DETAILS, target, profileId).url,
            )
        }

        /**
         * Where it stopped, in ms: the server's resume point when it offers one, else Expo's
         * percentage form (`resumePercent` in tenths of a nominal 1000 ms). Null when nothing to resume.
         */
        internal fun progress(item: ContinueWatchingItem): Pair<Long?, Long?> {
            val resume = item.resume
            if (resume != null && resume.eligible && resume.position > 0 && resume.duration > resume.position) {
                return (resume.position * 1000).toLong() to (resume.duration * 1000).toLong()
            }
            val percent = item.resumePercent ?: resume?.percent?.takeIf { resume.eligible } ?: 0.0
            if (percent <= 0) return null to null
            return Math.round(percent * 10).coerceIn(1, 999) to NOMINAL_DURATION_MS
        }

        /** Expo's Android order: the backdrop with its title text first, the poster last. */
        internal fun art(
            textBackdrop: String?, backdrop: String?, backdrops: List<String>?, textPoster: String?, poster: String?,
            resolve: (String) -> String?,
        ): Pair<String?, Boolean> {
            val wide = listOfNotNull(textBackdrop, backdrop, backdrops?.firstOrNull()).firstOrNull { it.isNotBlank() }
            val raw = wide ?: listOfNotNull(textPoster, poster).firstOrNull { it.isNotBlank() }
            // A sized rendition: the launcher draws cards a few hundred pixels wide.
            val url = raw?.let(resolve)?.let { Artwork.url(it, if (wide != null) "w780" else "w500") }
            return url to (wide != null || raw == null)
        }

        internal fun engagement(updatedAt: String?): Long? =
            updatedAt?.let { runCatching { OffsetDateTime.parse(it).toInstant().toEpochMilli() }.getOrNull() }?.takeIf { it > 0 }
    }
}

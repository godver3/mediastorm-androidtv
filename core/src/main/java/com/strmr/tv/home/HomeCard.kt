package com.strmr.tv.home

import com.strmr.tv.api.CardEpisode
import com.strmr.tv.api.ContinueWatchingItem
import com.strmr.tv.api.DetailsTarget
import com.strmr.tv.api.LibraryEntry
import com.strmr.tv.api.PersonRef
import com.strmr.tv.api.Title
import com.strmr.tv.live.EpgProgram
import com.strmr.tv.live.LiveChannel

/** What selecting a Home card does. */
sealed interface HomeLink {
    data class Details(val target: DetailsTarget) : HomeLink
    /** A whole list (streaming service, collection hub card). */
    data class OpenList(val list: BrowseList) : HomeLink
    /** A Watch Something card. */
    data class Action(val action: HomeAction) : HomeLink
    /** A favorite channel (and what it airs now): tunes it in the player. */
    data class Channel(val channel: LiveChannel, val program: EpgProgram?) : HomeLink
    /** A person (a Search result): opens their page. */
    data class Person(val person: PersonRef) : HomeLink
    /**
     * An unmatched library title, which has no details page: plays the file, or opens the show's
     * library episodes ([com.strmr.tv.ui.library.rememberLibraryOpener]). Matched ones open [Details].
     */
    data class Library(val entry: LibraryEntry) : HomeLink
}

enum class HomeAction { SURPRISE, ASK_AI }

/** Which optional card badges show (Settings › Card Badges over the server's `badgeVisibility`). */
data class BadgeVisibility(
    val watchState: Boolean = false,
    val releaseStatus: Boolean = false,
    val unwatchedCount: Boolean = false,
) {
    companion object {
        /** The `badgeVisibility` the server sends when nothing is set: progress bars only. */
        val DEFAULT_SERVER = listOf("watchProgress")

        /** The device's choice, else whether the server's list (null: [DEFAULT_SERVER]) names [key]. */
        fun shows(key: String, override: Boolean?, server: List<String>?): Boolean =
            override ?: (server ?: DEFAULT_SERVER).any { it.trim().equals(key, ignoreCase = true) }
    }
}

/** One Home card, from any shelf: what the card, the hero and details need. */
data class HomeCard(
    val id: String,
    val link: HomeLink,
    val title: String,
    /** Under the title on the card, and under the logo in the hero. */
    val subtitle: String,
    val overview: String? = null,
    val backdropUrl: String? = null,
    val logoUrl: String? = null,
    /** Card image: the next episode's still for Continue Watching, else the backdrop. */
    val artworkUrl: String? = backdropUrl,
    /** For poster cards and the explore card's collage. */
    val posterUrl: String? = null,
    /** 0–1; 0 hides the bar. */
    val progress: Float = 0f,
    /** Rank or air date: shown first, whatever the badge settings say. */
    val badge: CardBadge? = null,
    /** Coming soon, unreleased or in theaters, when the Release Status badge is on. */
    val releaseBadge: CardBadge? = null,
    /** Watched or in progress, shown under the others (a watched title can still be in theaters). */
    val watchBadge: CardBadge? = null,
    /** A series' unwatched episodes (the server's `unwatchedCount`); null for movies and unknown counts. */
    val unwatchedCount: Int? = null,
    /** "New": Continue Watching's next episode came out in the last 72 hours. */
    val newEpisode: NewEpisodeBadge? = null,
    /** The card draws `logoUrl` on a plain tile instead of artwork (services, hubs, channels). */
    val logoTile: Boolean = false,
    /** A bundled image drawn as the card (Watch Something). */
    val drawable: Int? = null,
    /** The carousel's metadata line: the Top 10 rank, the rating (certification) and the first genre. */
    val rank: Int? = null,
    val certification: String? = null,
    val genre: String? = null,
    /** The one episode the card stands for, for its long press's Mark as watched. */
    val episode: CardEpisode? = null,
    /** A hub card's preview of its list: four titles' art, drawn 2×2 in place of artwork. */
    val collageUrls: List<String> = emptyList(),
    val forceTitleOverlay: Boolean = false,
) {
    val target: DetailsTarget? get() = (link as? HomeLink.Details)?.target

    /** A rank shown as the first badge, in place of the release status (Top 10 grids). */
    fun ranked(rank: Int) = copy(badge = CardBadge("#$rank"), releaseBadge = null)

    /**
     * The badges the card draws, top to bottom, under [visibility]: the rank or air date always,
     * then the release status and the watch state when on. A series' unwatched count rides in
     * the watch-state badge (as the Expo card puts the number beside the icon), or stands alone.
     * A zero count is dropped, so a fully watched series shows just the checkmark.
     */
    fun visibleBadges(visibility: BadgeVisibility): List<CardBadge> {
        val count = unwatchedCount?.takeIf { visibility.unwatchedCount && it > 0 }
        val watch = watchBadge?.takeIf { visibility.watchState }
        val watchOrCount = when {
            count == null -> watch
            watch != null -> watch.copy(text = "${watch.text}, $count unwatched", count = count)
            else -> CardBadge("$count unwatched", count = count)
        }
        return listOfNotNull(badge, releaseBadge?.takeIf { visibility.releaseStatus }, watchOrCount)
    }

    companion object {
        fun kindLine(year: Int?, isMovie: Boolean) =
            listOfNotNull(year?.toString(), if (isMovie) "Movie" else "Series").joinToString(" · ")

        fun from(item: ContinueWatchingItem): HomeCard {
            val ep = item.nextEpisode
            val subtitle = when {
                item.isMovie -> item.year?.toString() ?: "Movie"
                ep != null -> ep.code + (ep.title?.let { " · $it" } ?: "")
                else -> "Series"
            }
            // The server's resume state for the item the row plays; older servers send only percentages.
            val progress = item.resume?.let { if (it.eligible) it.fraction else 0f }
                ?: (((if (item.isMovie) item.percentWatched else item.resumePercent) ?: 0.0) / 100).toFloat().coerceIn(0f, 1f)
            // An unaired next episode beats the series status; movies use their release status.
            val airs = if (!item.isMovie && ep != null) ReleaseBadge.forEpisode(ep.airDate, ep.airDateTimeUTC) else null
            return HomeCard(
                id = item.seriesId,
                link = HomeLink.Details(DetailsTarget.from(item)),
                title = item.seriesTitle,
                subtitle = subtitle,
                overview = ep?.overview ?: item.overview,
                backdropUrl = item.backdropUrl,
                logoUrl = item.logoUrl,
                artworkUrl = ep?.image?.url ?: item.backdropUrl,
                posterUrl = item.textPosterUrl ?: item.posterUrl,
                progress = progress,
                badge = airs?.let { CardBadge(it) },
                releaseBadge = if (airs == null) ReleaseBadge.forStatus(item.status) else null,
                newEpisode = if (!item.isMovie && ep != null) {
                    NewEpisodeBadge.from(ep.airDate, ep.airDateTimeUTC, item.prequeueStatus)
                } else null,
                episode = if (!item.isMovie && ep != null) CardEpisode.from(ep) else null,
                watchBadge = if (item.isMovie) null else CardBadge.forWatchState(item.seriesWatchState(progress)),
                unwatchedCount = item.unwatchedCount,
            )
        }

        fun from(item: WatchlistItem) = HomeCard(
            id = item.id,
            link = HomeLink.Details(
                DetailsTarget(
                    titleId = item.id, name = item.name, isMovie = item.isMovie, year = item.year,
                    tmdbId = item.externalIds?.get("tmdb"), imdbId = item.externalIds?.get("imdb"), tvdbId = item.externalIds?.get("tvdb"),
                    posterUrl = item.posterUrl, textPosterUrl = item.textPosterUrl, backdropUrl = item.backdropUrl,
                    logoUrl = item.logoUrl, overview = item.overview,
                ),
            ),
            title = item.name,
            subtitle = kindLine(item.year, item.isMovie),
            overview = item.overview,
            backdropUrl = item.backdropUrl,
            logoUrl = item.logoUrl,
            artworkUrl = item.backdropUrl ?: item.posterUrl,
            posterUrl = item.textPosterUrl ?: item.posterUrl,
            releaseBadge = ReleaseBadge.forTitle(item.availability, item.status, item.releaseDate),
            watchBadge = CardBadge.forWatchState(item.watchState),
            unwatchedCount = item.unwatchedCount.takeUnless { item.isMovie },
        )

        /** A list title; `rank` (Top 10) shows as the badge. */
        fun from(title: Title, rank: Int? = null) = HomeCard(
            id = title.id,
            link = HomeLink.Details(DetailsTarget.from(title)),
            title = title.name,
            subtitle = title.cardSubtitle?.trim()?.takeIf { it.isNotEmpty() } ?: kindLine(title.year, title.isMovie),
            forceTitleOverlay = title.forceTitleOverlay,
            overview = title.overview,
            backdropUrl = title.backdrop?.url,
            logoUrl = title.logo?.url,
            artworkUrl = title.cardImage?.url ?: title.backdrop?.url ?: title.poster?.url,
            posterUrl = title.textPoster?.url ?: title.poster?.url,
            badge = rank?.let { CardBadge("#$it") },
            releaseBadge = if (rank == null) ReleaseBadge.forTitle(title.availability, title.status, title.releaseDate) else null,
            watchBadge = CardBadge.forWatchState(title.watchState),
            unwatchedCount = title.unwatchedCount.takeUnless { title.isMovie },
            rank = rank,
            certification = title.certification?.takeIf { it.isNotBlank() },
            genre = title.genres?.firstOrNull(),
        )

        fun from(entry: BrowseEntry) = when (entry) {
            is BrowseEntry.TitleEntry -> from(entry.title)
            is BrowseEntry.ItemEntry -> from(entry.item)
            is BrowseEntry.WatchingEntry -> from(entry.item)
            is BrowseEntry.LibraryItemEntry -> from(entry.entry)
        }

        /** A library title (Home library rows, library grids). */
        fun from(entry: LibraryEntry): HomeCard {
            val group = entry.group
            return HomeCard(
                id = entry.id,
                link = entry.detailsTarget?.let { HomeLink.Details(it) } ?: HomeLink.Library(entry),
                title = group.title,
                subtitle = kindLine(group.year, !group.isShow),
                overview = group.overview,
                backdropUrl = group.backdropUrl,
                artworkUrl = group.backdropUrl ?: group.posterUrl,
                posterUrl = group.posterUrl,
            )
        }
    }
}

/**
 * A Home row, one per enabled shelf of the profile's `homeShelves` config. It shows its first
 * `cap` cards; a longer list gets an explore card that opens the whole list.
 */
data class HomeShelf(
    /** The shelf config's id. */
    val id: String,
    val title: String,
    /** The cards loaded for the row: the shown ones plus any overflow for the collage. */
    val cards: List<HomeCard>,
    val cap: Int = 20,
    val list: BrowseList? = null,
    /** The explore card leads the row instead of closing it (`exploreCardPosition`). */
    val exploreFirst: Boolean = false,
    private val reportedTotal: Int? = null,
) {
    /** The whole list's size, larger than `cards` when the server pages it. */
    val total: Int = maxOf(reportedTotal ?: cards.size, cards.size)
    val shown: List<HomeCard> get() = cards.take(cap)
    val explore: ExploreCard? = if (total > cap && list != null) ExploreCard.build(id, title, list, cards, cap, total) else null

    fun removing(card: String) = copy(cards = cards.filterNot { it.id == card }, reportedTotal = total - 1)

    companion object {
        const val CONTINUE_WATCHING_ID = "continue-watching"
        /** Titles fetched beyond a row's cap, for the explore card's collage. */
        const val COLLAGE_OVERFLOW = 8
    }
}

/**
 * The card on a row holding more than its cap: a 2×2 grid of posters from titles the row
 * doesn't show, and how many more there are. It opens the whole list.
 */
data class ExploreCard(
    val list: BrowseList,
    val title: String,
    val moreCount: Int,
    val posterUrls: List<String>,
    val artworkUrls: List<String>,
    /** The hero's backdrop while the card has focus: the first hidden title's. */
    val backdropUrl: String?,
) {
    companion object {
        const val ID = "__explore__"

        fun build(shelf: String, title: String, list: BrowseList, cards: List<HomeCard>, cap: Int, total: Int): ExploreCard {
            val shown = cards.take(cap)
            val overflow = cards.drop(cap)
            return ExploreCard(
                list, title, total - cap,
                collage({ it.posterUrl }, shown, overflow, shelf),
                collage({ it.artworkUrl }, shown, overflow, shelf),
                overflow.firstNotNullOfOrNull { it.backdropUrl } ?: cards.firstOrNull()?.backdropUrl,
            )
        }

        /**
         * Four images from the hidden titles, skipping any already shown, picked by a shuffle
         * seeded from the row so the card stays the same between loads (as the Expo app's
         * `pickExploreCollageImages`). Falls back to the last shown titles when too few are hidden.
         */
        private fun collage(image: (HomeCard) -> String?, shown: List<HomeCard>, overflow: List<HomeCard>, seed: String): List<String> {
            val seen = shown.mapNotNull(image).toMutableSet()
            val hidden = overflow.mapNotNull(image).filter { seen.add(it) }
            val random = kotlin.random.Random((listOf(seed) + hidden.take(10)).joinToString("").hashCode())
            val picked = hidden.shuffled(random).take(4).toMutableList()
            if (picked.size < 4) {
                val fill = picked.toMutableSet()
                picked += shown.reversed().mapNotNull(image).filter { fill.add(it) }.take(4 - picked.size)
            }
            return picked
        }
    }
}

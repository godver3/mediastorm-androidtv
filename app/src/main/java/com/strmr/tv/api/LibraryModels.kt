package com.strmr.tv.api

import kotlinx.serialization.Serializable

// The server's local, Plex and Jellyfin libraries (`/api/library/…`). The server reads Plex and
// Jellyfin for the app and streams their files itself, so all three look the same here.

/** A configured library (`GET /api/library/libraries`). */
@Serializable
data class LibraryInfo(
    val id: String,
    val name: String,
    /** movie | show | other */
    val type: String = "movie",
    /** idle | scanning | complete | failed */
    val lastScanStatus: String? = null,
    /** local | plex | jellyfin */
    val sourceType: String? = null,
    val sourceServerName: String? = null,
) {
    val ref: LibraryRef get() = LibraryRef(id, name, type, sourceType)
    val source: LibrarySource get() = LibrarySource.of(sourceType)
    val isScanning: Boolean get() = lastScanStatus == "scanning"

    /** Under the tile's name: where it lives and what it holds, or that it is being scanned. */
    val tileSubtitle: String
        get() = if (isScanning) "Scanning…" else {
            val kind = when (type) { "show" -> "Shows"; "movie" -> "Movies"; else -> "Videos" }
            "${source.label} · $kind"
        }
}

/** A library as its grid and cards need it. A Home row knows only the id and its own name. */
data class LibraryRef(val id: String, val name: String, val type: String? = null, val sourceType: String? = null)

/** Where a library's files live, with the Expo app's labels. */
enum class LibrarySource(val label: String) {
    LOCAL("Local"), PLEX("Plex"), JELLYFIN("Jellyfin");

    companion object {
        fun of(raw: String?) = when (raw?.lowercase()) {
            "plex" -> PLEX
            "jellyfin" -> JELLYFIN
            else -> LOCAL
        }
    }
}

@Serializable
data class LibraryProbe(
    val durationSeconds: Double? = null,
    val videoCodec: String? = null,
    val width: Int? = null,
    val height: Int? = null,
    val hdrFormat: String? = null,
    val audioCodecs: List<String>? = null,
)

/** One file: a movie version or an episode's copy. */
@Serializable
data class LibraryItem(
    val id: String,
    val fileName: String? = null,
    val seasonNumber: Int? = null,
    val episodeNumber: Int? = null,
    val episodeTitle: String? = null,
    val episodeOverview: String? = null,
    val episodeImage: ImageRef? = null,
    val detectedTitle: String? = null,
    val probe: LibraryProbe? = null,
    val sizeBytes: Long? = null,
    val sourceType: String? = null,
    val sourceServerName: String? = null,
    /** Plex / Jellyfin's own version name ("1080p · H264"). */
    val versionLabel: String? = null,
)

@Serializable
data class LibraryEpisode(
    val episodeNumber: Int,
    val episodeTitle: String? = null,
    val episodeOverview: String? = null,
    val episodeImage: ImageRef? = null,
    val items: List<LibraryItem> = emptyList(),
)

@Serializable
data class LibrarySeason(val seasonNumber: Int, val episodes: List<LibraryEpisode> = emptyList())

/**
 * A title in a library: a movie with its versions, a show with its seasons, or (an "other"
 * library) a single file. `include=cards` leaves the files out.
 */
@Serializable
data class LibraryGroup(
    val id: String,
    /** title | show | file */
    val groupType: String? = null,
    /** movie | show | other */
    val libraryType: String? = null,
    val title: String,
    val overview: String? = null,
    val year: Int? = null,
    val tmdbId: Long? = null,
    val tvdbId: Long? = null,
    val imdbId: String? = null,
    val poster: ImageRef? = null,
    val textPoster: ImageRef? = null,
    val backdrop: ImageRef? = null,
    val textBackdrop: ImageRef? = null,
    val items: List<LibraryItem>? = null,
    val seasons: List<LibrarySeason>? = null,
) {
    val isShow: Boolean get() = groupType == "show" || libraryType == "show"
    val hasFiles: Boolean get() = !items.isNullOrEmpty() || !seasons.isNullOrEmpty()
    val posterUrl: String? get() = textPoster?.url ?: poster?.url
    val backdropUrl: String? get() = backdrop?.url ?: textBackdrop?.url

    /**
     * The catalog title the group was matched to, as the server names it (tmdb, else tvdb, else
     * imdb); null for unmatched files and home videos, which have no details page.
     */
    val catalogTitleId: String?
        get() {
            tmdbId?.takeIf { it > 0 }?.let { return if (isShow) "tmdb:tv:$it" else "tmdb:movie:$it" }
            tvdbId?.takeIf { it > 0 }?.let { return if (isShow) "tvdb:series:$it" else "tvdb:movie:$it" }
            return imdbId?.ifEmpty { null }
        }
}

@Serializable
data class LibraryGroupPage(val groups: List<LibraryGroup> = emptyList(), val total: Int = 0, val alphabetBuckets: List<String>? = null)

/** A library's copy of a title (`GET /api/library/matches`). */
@Serializable
data class LibraryMatch(val libraryId: String, val libraryName: String = "", val group: LibraryGroup)

/** `GET /api/library/items/{id}/playback`: the file's stream path, played like any other. */
@Serializable
data class LibraryPlaybackInfo(val itemId: String, val streamPath: String, val durationSeconds: Double? = null)

/** A library title in a grid or Home row, with the library it came from. */
data class LibraryEntry(val library: LibraryRef, val group: LibraryGroup) {
    val id: String get() = "${library.id}|${group.id}"

    /** Matched titles open their catalog details page, which finds this copy itself. */
    val detailsTarget: DetailsTarget? get() = DetailsTarget.fromLibrary(group)
}

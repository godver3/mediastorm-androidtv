package com.strmr.tv.library

import com.strmr.tv.api.LibraryGroup
import com.strmr.tv.api.LibraryItem
import com.strmr.tv.api.LibraryMatch
import com.strmr.tv.api.LibrarySource
import java.util.Locale

/** One playable copy of a movie or episode: a file in a local, Plex or Jellyfin library. */
data class LibraryVersion(val item: LibraryItem, val libraryName: String) {
    val id: String get() = item.id
    val source: LibrarySource get() = LibrarySource.of(item.sourceType)

    /** "Plex · Films". */
    val origin: String get() = "${source.label} · $libraryName"

    /** "4K · HEVC · HDR10" from the file's probe, else the server's version name, else the file. */
    val quality: String
        get() = listOfNotNull(resolution, codec, hdr).joinToString(" · ").ifEmpty { null }
            ?: item.versionLabel?.ifEmpty { null } ?: item.fileName ?: "Library File"

    /** "21.1 GB · 28 Mbps". */
    val size: String?
        get() {
            val bytes = item.sizeBytes?.takeIf { it > 0 } ?: return null
            val parts = mutableListOf(
                if (bytes >= 1_000_000_000) String.format(Locale.US, "%.1f GB", bytes / 1e9)
                else String.format(Locale.US, "%.0f MB", bytes / 1e6),
            )
            item.probe?.durationSeconds?.takeIf { it > 0 }?.let { parts += "${Math.round(bytes * 8 / it / 1e6)} Mbps" }
            return parts.joinToString(" · ")
        }

    /** The release name, for the player's Switch Source list and subtitle matching. */
    val releaseName: String get() = item.fileName ?: quality

    /** Size class from the frame, by width first: a 2.39:1 4K file is 3840×1608. */
    val resolution: String?
        get() {
            val width = item.probe?.width ?: 0
            val height = item.probe?.height ?: 0
            return when {
                width >= 3200 || height >= 1800 -> "4K"
                width >= 1800 || height >= 1000 -> "1080p"
                width >= 1200 || height >= 700 -> "720p"
                height > 0 -> "${height}p"
                else -> null
            }
        }

    private val codec: String?
        get() = when (val raw = item.probe?.videoCodec?.lowercase()) {
            null, "" -> null
            "hevc", "h265" -> "HEVC"
            "h264", "avc", "avc1" -> "H.264"
            else -> raw.uppercase()
        }

    private val hdr: String?
        get() {
            val raw = item.probe?.hdrFormat?.lowercase()?.ifEmpty { null } ?: return null
            return when {
                "dolby" in raw || "dovi" in raw || raw == "dv" -> "Dolby Vision"
                "10+" in raw || "10plus" in raw -> "HDR10+"
                "hlg" in raw -> "HLG"
                "hdr" in raw || "pq" in raw -> "HDR10"
                else -> raw.uppercase()
            }
        }

    companion object {
        /** Better copies sort first: more pixels, then HDR, then the larger file. */
        val best: Comparator<LibraryVersion> = compareByDescending<LibraryVersion> { (it.item.probe?.width ?: 0) * (it.item.probe?.height ?: 0) }
            .thenByDescending { it.hdr != null }
            .thenByDescending { it.item.sizeBytes ?: 0 }
    }
}

/**
 * A title's copies in the profile's libraries, from `/api/library/matches` or a library group: a
 * movie's versions, or a series' episodes by season and episode number (the library's numbering,
 * as the Expo app maps them). Best copy first.
 */
data class LibraryVersions(val movie: List<LibraryVersion>, val episodes: Map<Key, List<LibraryVersion>>) {
    data class Key(val season: Int, val episode: Int)

    val isEmpty: Boolean get() = movie.isEmpty() && episodes.isEmpty()

    /** A movie's versions (no episode), or one episode's copies. */
    fun versions(season: Int?, episode: Int?): List<LibraryVersion> =
        if (season == null || episode == null) movie else episodes[Key(season, episode)].orEmpty()

    fun best(season: Int?, episode: Int?): LibraryVersion? = versions(season, episode).firstOrNull()

    companion object {
        fun of(matches: List<LibraryMatch>) = build(matches.map { it.group to it.libraryName })
        fun of(group: LibraryGroup, libraryName: String) = build(listOf(group to libraryName))

        private fun build(groups: List<Pair<LibraryGroup, String>>): LibraryVersions {
            val movie = linkedMapOf<String, LibraryVersion>()
            val episodes = linkedMapOf<Key, LinkedHashMap<String, LibraryVersion>>()
            for ((group, library) in groups) {
                group.items.orEmpty().forEach { movie.putIfAbsent(it.id, LibraryVersion(it, library)) }
                for (season in group.seasons.orEmpty()) for (episode in season.episodes) {
                    val copies = episodes.getOrPut(Key(season.seasonNumber, episode.episodeNumber)) { linkedMapOf() }
                    episode.items.forEach { copies.putIfAbsent(it.id, LibraryVersion(it, library)) }
                }
            }
            return LibraryVersions(
                movie.values.sortedWith(LibraryVersion.best),
                episodes.mapValues { (_, copies) -> copies.values.sortedWith(LibraryVersion.best) },
            )
        }
    }
}

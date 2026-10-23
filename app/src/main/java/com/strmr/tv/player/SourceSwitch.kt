package com.strmr.tv.player

import androidx.annotation.OptIn
import androidx.media3.common.C
import androidx.media3.common.TrackSelectionOverride
import androidx.media3.common.TrackSelectionParameters
import androidx.media3.common.Tracks
import androidx.media3.common.util.UnstableApi
import com.strmr.tv.api.DetailsTarget
import com.strmr.tv.api.SourceResult
import com.strmr.tv.api.StartPoint

/** What Switch Source hands the coordinator: the same title at the current position, on another source. */
data class SourceSwitchRequest(val request: PlaybackRequest, val source: SourceResult, val carried: CarriedTracks)

/**
 * The audio and subtitle languages playing when the source was switched, re-applied on the new
 * stream's tracks once they are known. Null languages leave the new stream's own choice.
 */
data class CarriedTracks(
    val audioLanguage: String? = null,
    val subtitleLanguage: String? = null,
    val subtitleForced: Boolean = false,
    val subtitlesOff: Boolean = false,
) {
    /** One track of the new stream, reduced to what matching needs. */
    data class Candidate(val language: String?, val forced: Boolean = false, val selected: Boolean = false)

    companion object {
        /**
         * The track to select among `tracks` for `language`: none when the language is unknown or
         * the stream's own pick already has it, else the first with that language (the same
         * forced-ness first for subtitles).
         */
        fun pick(tracks: List<Candidate>, language: String?, forced: Boolean? = null): Int? {
            val want = SubtitleLanguages.canonical(language) ?: return null
            val matching = tracks.indices.filter { SubtitleLanguages.canonical(tracks[it].language) == want }
            if (matching.isEmpty()) return null
            val best = forced?.let { f -> matching.filter { tracks[it].forced == f } }?.ifEmpty { null } ?: matching
            if (best.any { tracks[it].selected }) return null
            return best.first()
        }

        @OptIn(UnstableApi::class)
        fun capture(tracks: Tracks, params: TrackSelectionParameters, externalSubtitle: Boolean): CarriedTracks {
            val audio = tracks.groups.firstOrNull { it.type == C.TRACK_TYPE_AUDIO && it.isSelected }
            val text = tracks.groups.firstOrNull { it.type == C.TRACK_TYPE_TEXT && it.isSelected }
            val textFormat = text?.let { g -> (0 until g.length).firstOrNull { g.isTrackSelected(it) }?.let { g.getTrackFormat(it) } }
            val audioFormat = audio?.let { g -> (0 until g.length).firstOrNull { g.isTrackSelected(it) }?.let { g.getTrackFormat(it) } }
            val off = !externalSubtitle && (C.TRACK_TYPE_TEXT in params.disabledTrackTypes || text == null)
            return CarriedTracks(
                audioLanguage = audioFormat?.language,
                // A downloaded / translated subtitle isn't carried: the new stream keeps its own choice.
                subtitleLanguage = textFormat?.language?.takeIf { !externalSubtitle },
                subtitleForced = textFormat?.let { it.selectionFlags and C.SELECTION_FLAG_FORCED != 0 } ?: false,
                subtitlesOff = off,
            )
        }
    }

    /** The new stream's selection with these languages applied. */
    @OptIn(UnstableApi::class)
    fun apply(tracks: Tracks, params: TrackSelectionParameters): TrackSelectionParameters {
        val builder = params.buildUpon()
        fun select(type: Int, language: String?, forced: Boolean?) {
            val entries = tracks.groups.filter { it.type == type }.flatMap { g ->
                (0 until g.length).filter { g.isTrackSupported(it) }.map { i -> g to i }
            }
            val candidates = entries.map { (g, i) ->
                val f = g.getTrackFormat(i)
                Candidate(f.language, f.selectionFlags and C.SELECTION_FLAG_FORCED != 0, g.isTrackSelected(i))
            }
            val index = pick(candidates, language, forced) ?: return
            val (group, track) = entries[index]
            builder.setTrackTypeDisabled(type, false).setOverrideForType(TrackSelectionOverride(group.mediaTrackGroup, track))
        }
        select(C.TRACK_TYPE_AUDIO, audioLanguage, null)
        if (subtitlesOff) builder.setTrackTypeDisabled(C.TRACK_TYPE_TEXT, true)
        else select(C.TRACK_TYPE_TEXT, subtitleLanguage, subtitleForced)
        return builder.build()
    }
}

/** The Switch Source list's rules, pure for tests. */
object SourceSwitchRules {
    /**
     * The manual source search for a title or episode ("Show S01E02", "Movie 1999"), shared by
     * the details page's source picker and Switch Source so both list the server's results in
     * the same order (neither re-sorts or filters them).
     */
    fun query(title: DetailsTarget, episodeCode: String?): String =
        title.name + (episodeCode?.let { " $it" } ?: title.year?.let { " $it" } ?: "")

    fun query(request: PlaybackRequest): String = query(request.title, request.episode?.code)

    /** The request to resume on another source: the same title at `positionMs`. */
    fun resumeAt(request: PlaybackRequest, positionMs: Long): PlaybackRequest =
        request.copy(start = StartPoint.Seconds(positionMs.coerceAtLeast(0) / 1000.0))

    private val extension = Regex("\\.(mkv|mp4|m4v|avi|ts|mov|webm)$", RegexOption.IGNORE_CASE)
    private fun normalize(name: String) = name.trim().replace(extension, "").lowercase().filter { it.isLetterOrDigit() }

    /** The release name a result shows: the first line of the scraper's raw title when present. */
    fun releaseName(title: String, rawTitle: String?): String = (rawTitle ?: title).lineSequence().firstOrNull()?.trim() ?: title

    /** Whether a result is the release playing (by name; the server names prequeued ones). */
    fun isCurrent(resultTitle: String, rawTitle: String?, playing: String?): Boolean {
        val p = playing?.let(::normalize)?.ifEmpty { null } ?: return false
        return normalize(resultTitle) == p || rawTitle?.let { normalize(releaseName(resultTitle, it)) == p } == true
    }

    /** "4K · DV · HEVC · 12.3 GB · Usenet (NZBgeek) · Healthy". */
    fun detail(attrs: Map<String, String>, sizeBytes: Long, serviceType: String, indexer: String, health: String?): String =
        buildList {
            attrs["resolution"]?.takeIf { it.isNotEmpty() }?.let { add(if (it == "2160p") "4K" else it) }
            if (attrs["hasDV"] == "true") add("DV")
            attrs["hdr"]?.let { if (it.contains("HDR10+")) add("HDR10+") else if (it.contains("HDR")) add("HDR") }
            attrs["codec"]?.takeIf { it.isNotEmpty() }?.let { add(it.uppercase()) }
            if (sizeBytes > 0) add(size(sizeBytes))
            val provider = attrs["tracker"]?.ifEmpty { null } ?: indexer.ifEmpty { null }
            when (serviceType) {
                "usenet" -> add(listOfNotNull("Usenet", provider?.let { "($it)" }).joinToString(" "))
                "debrid" -> add(listOfNotNull("Debrid", provider?.let { "($it)" }).joinToString(" "))
                else -> provider?.let { add(it) }
            }
            healthLabel(health)?.let { add(it) }
        }.joinToString(" · ")

    fun healthLabel(state: String?): String? = when (state) {
        "cached" -> "Cached"
        "healthy" -> "Healthy"
        "not_cached" -> "Not cached"
        "unhealthy" -> "Unhealthy"
        "pending" -> "Checking…"
        else -> null
    }

    fun size(bytes: Long): String =
        if (bytes >= 1L shl 30) "%.1f GB".format(bytes / (1L shl 30).toDouble()) else "${bytes shr 20} MB"
}

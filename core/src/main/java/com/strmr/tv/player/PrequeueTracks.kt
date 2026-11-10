package com.strmr.tv.player

import com.strmr.tv.api.PrequeueStatus
import com.strmr.tv.api.ProbedSubtitleTrack
import com.strmr.tv.api.TrackProbe

// The details page's Audio & Subtitles button: track picks for the stream the page's prequeue
// resolved (the Expo details page's prequeue track pills, `usePlayback` `trackOverrideAudio` /
// `trackOverrideSubtitle`), shown in manual selection's track panel.

/** The prequeue started as a details page opened ([PlaybackCoordinator.prewarm]), as its button shows it. */
data class WarmPrequeue(val titleId: String, val profile: String, val phase: Phase, val status: PrequeueStatus? = null) {
    /** Unavailable: failed, expired, timed out, or prequeue is off for the profile. */
    enum class Phase { RESOLVING, READY, UNAVAILABLE }

    /** The resolved status when it lists tracks to pick from. */
    val tracks: PrequeueStatus? get() = status?.takeIf { phase == Phase.READY && it.hasTrackLists }
}

/**
 * Picks made on details for the prequeued stream. Track numbers belong to one file, so they
 * apply only when Play resolves that same stream (and episode).
 */
data class PrequeuePick(val streamPath: String, val episode: PrequeueStatus.TargetEpisode?, val tracks: ManualSourceTracks) {
    fun appliesTo(status: PrequeueStatus) = status.streamPath == streamPath && status.targetEpisode == episode

    /**
     * The picks for the stream Play resolved: as made for the same stream, else carried by
     * intent (another episode, or a re-resolve that found another file).
     */
    fun tracksFor(status: PrequeueStatus): ManualSourceTracks {
        if (appliesTo(status)) return tracks
        val lists = status.trackLists
        return ManualSourceTracks(tracks.carried(lists), lists.audio, lists.subtitles)
    }
}

/**
 * The picks moved to another file's lists: Off stays off; a track becomes that file's track in
 * the same language (and forced / SDH / commentary kind), or the default when it has none.
 */
fun ManualSourceTracks.carried(lists: TrackProbe.Tracks): ManualTrackChoice {
    val commentary = { title: String? -> ClientTrackSelector.isCommentary(title.orEmpty()) }
    val sdh = { t: ProbedSubtitleTrack -> t.title.orEmpty().lowercase().let { "sdh" in it || "deaf" in it || "hard of hearing" in it } }
    val audioPick = choice.audio?.let { index ->
        val old = audio.firstOrNull { it.index == index }?.takeIf { it.language.isNotBlank() } ?: return@let null
        val same = lists.audio.filter { TrackPreferences.sameLanguage(it.language, old.language) }
        (same.firstOrNull { commentary(it.title) == commentary(old.title) } ?: same.firstOrNull())?.index
    }
    val subtitlePick = when (val index = choice.subtitle) {
        null -> null
        ManualTrackChoice.OFF -> ManualTrackChoice.OFF
        else -> {
            val old = subtitles.firstOrNull { it.index == index }?.takeIf { it.language.isNotBlank() }
            val same = if (old == null) emptyList() else lists.subtitles.filter {
                TrackPreferences.sameLanguage(it.language, old.language) && it.forced == old.forced
            }
            (same.firstOrNull { old != null && sdh(it) == sdh(old) } ?: same.firstOrNull())?.index
        }
    }
    return ManualTrackChoice(audioPick, subtitlePick)
}

val PrequeueStatus.hasTrackLists: Boolean get() = !audioTracks.isNullOrEmpty() || !subtitleTracks.isNullOrEmpty()

/** The stream's lists as the track panel takes them, by container stream number. */
val PrequeueStatus.trackLists: TrackProbe.Tracks
    get() = TrackProbe.Tracks(
        audioTracks.orEmpty(),
        subtitleTracks.orEmpty().map {
            ProbedSubtitleTrack(if (it.absoluteIndex >= 0) it.absoluteIndex else it.index, it.language, it.codec,
                                it.title.ifEmpty { null }, it.forced, it.isBitmap)
        },
    )

/**
 * The release being played: the passthrough name, else the indexer result's title, else the
 * file name, else the server's display name (often just the title).
 */
val PrequeueStatus.releaseTitle: String?
    get() {
        val attributes = resultAttributes ?: selectedResult?.attributes
        val passthrough = passthroughName ?: attributes?.get("raw_name")?.takeIf { attributes["passthrough_format"] == "true" }
        val file = streamPath?.substringBefore('?')?.substringAfterLast('/')
        return listOf(passthrough, attributes?.get("raw_title"), selectedResult?.title, file, displayName)
            .firstNotNullOfOrNull { name -> name?.lineSequence()?.firstOrNull()?.trim()?.ifEmpty { null } }
    }

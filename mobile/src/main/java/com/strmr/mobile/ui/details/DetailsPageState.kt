package com.strmr.mobile.ui.details

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.strmr.tv.api.Episode
import com.strmr.tv.api.SourceResult
import com.strmr.tv.api.StartPoint
import com.strmr.tv.library.LibraryVersion
import com.strmr.tv.player.ManualSourceTracks
import com.strmr.tv.player.PlaybackCoordinator
import com.strmr.tv.ui.details.DetailsModel
import com.strmr.tv.ui.details.PrequeueTracks

/** The source list's or Versions' subject: the movie (null), or one episode. */
data class EpisodeTarget(val episode: Episode?)

/** A picked source waiting on the Resume / Start Over choice. */
data class PendingSource(val source: SourceResult, val episode: Episode?, val resumeAt: StartPoint, val manual: ManualSourceTracks?)

/** A library copy picked in Versions, waiting on the Resume / Start Over choice. */
data class PendingVersion(val version: LibraryVersion, val episode: Episode?, val resumeAt: StartPoint)

/**
 * A phone details page's own state over the shared [DetailsModel]: which episode is selected
 * (a tap on an episode selects it and the primary button then plays it, as the Expo and iPhone
 * pages; a tap on the selected one plays it), and which sheet or prompt is up. Main thread only.
 */
class DetailsPageState(val model: DetailsModel) {
    /** The episode tapped in the carousel; null follows the server's up-next one. */
    var selectedEpisodeId by mutableStateOf<String?>(null)
    var sourcesFor by mutableStateOf<EpisodeTarget?>(null)
    var versionsFor by mutableStateOf<EpisodeTarget?>(null)
    var pendingSource by mutableStateOf<PendingSource?>(null)
    var pendingVersion by mutableStateOf<PendingVersion?>(null)
    var episodeMenu by mutableStateOf<Episode?>(null)
    var more by mutableStateOf(false)
    var watchState by mutableStateOf(false)
    var episodeOrder by mutableStateOf(false)

    /** The Audio & Subtitles picks for the prequeued stream of the episode Play targets. */
    val prequeueTracks = PrequeueTracks(model) { activeEpisode }

    /** What the primary button plays: the selected episode, else up next (series only). */
    val activeEpisode: Episode?
        get() {
            if (model.target.isMovie) return null
            val id = selectedEpisodeId ?: return model.primaryEpisode
            return model.seasons.firstNotNullOfOrNull { s -> s.episodes.firstOrNull { it.id == id } } ?: model.primaryEpisode
        }

    /** The primary button's episode, resume point and label, following the selection. */
    val primary: DetailsModel.Primary?
        get() {
            if (model.target.isMovie) return model.primary
            val ep = activeEpisode ?: return null
            val at = model.resumePoint(ep)
            return DetailsModel.Primary(ep, at, if (at != null) "Resume ${ep.code}" else "Play ${ep.code}")
        }

    fun heading(episode: Episode?) = episode?.let { "${model.target.name} · ${it.code}" } ?: model.target.name

    /** Play / Resume: library first, else the prequeue with the page's track picks. */
    fun playPrimary() {
        val p = primary ?: return
        model.play(p.episode, p.start, prequeueTracks.pick(PlaybackCoordinator.warm.value))
    }

    /** Start Over: what Play plays, from the beginning. */
    fun startOver() {
        val p = primary ?: return
        model.play(p.episode, null, prequeueTracks.pick(PlaybackCoordinator.warm.value))
    }

    /** An episode from where it was left (its own resume point). */
    fun play(episode: Episode, from: StartPoint?) =
        model.play(episode, from, prequeueTracks.pick(PlaybackCoordinator.warm.value))

    /** Tap on an episode card: the selected one plays, another becomes the selection. */
    fun tapEpisode(episode: Episode) {
        if (episode.id == activeEpisode?.id) play(episode, model.resumePoint(episode)) else selectedEpisodeId = episode.id
    }

    /** A source picked in the list: asks Resume / Start Over when there is a resume point. */
    fun choose(source: SourceResult, episode: Episode?, manual: ManualSourceTracks?) {
        val resumeAt = model.resumePoint(episode)
        if (resumeAt != null) pendingSource = PendingSource(source, episode, resumeAt, manual) else model.play(source, episode, null, manual)
    }

    /** A library copy picked in Versions or the source list. */
    fun chooseVersion(version: LibraryVersion, episode: Episode?) {
        val resumeAt = model.resumePoint(episode)
        if (resumeAt != null) pendingVersion = PendingVersion(version, episode, resumeAt) else model.playVersion(version, episode, null)
    }
}

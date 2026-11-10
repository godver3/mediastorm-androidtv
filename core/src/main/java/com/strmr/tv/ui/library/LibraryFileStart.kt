package com.strmr.tv.ui.library

import com.strmr.tv.api.ApiClient
import com.strmr.tv.api.DetailsTarget
import com.strmr.tv.api.LibraryGroup
import com.strmr.tv.api.LibraryPlaybackInfo
import com.strmr.tv.api.StartPoint
import com.strmr.tv.api.libraryPlayback
import com.strmr.tv.api.resumeState
import com.strmr.tv.library.LibraryVersion
import com.strmr.tv.library.LibraryVersions
import com.strmr.tv.player.PlaybackCoordinator
import com.strmr.tv.player.PlaybackRequest
import com.strmr.tv.player.playLibrary

/**
 * An unmatched library file ready to play: its stream path, and where it was left. Its progress is
 * keyed by that path (the Expo app's rule), since no catalog id names it.
 */
data class LibraryFileStart(
    val group: LibraryGroup,
    val versions: LibraryVersions,
    val version: LibraryVersion,
    val episode: PlaybackRequest.EpisodeInfo?,
    val following: List<PlaybackRequest.EpisodeInfo>,
    val playback: LibraryPlaybackInfo,
    val resumeAt: StartPoint?,
) {
    fun play(from: StartPoint?) {
        val request = PlaybackRequest(
            DetailsTarget.unmatched(group), episode, from, backdrops = listOfNotNull(episode?.imageUrl, group.backdropUrl),
            following = following, library = versions,
        )
        PlaybackCoordinator.playLibrary(request, version, playback)
    }

    companion object {
        suspend fun prepare(
            version: LibraryVersion, versions: LibraryVersions, group: LibraryGroup,
            episode: PlaybackRequest.EpisodeInfo? = null, following: List<PlaybackRequest.EpisodeInfo> = emptyList(),
        ): LibraryFileStart {
            val playback = ApiClient.libraryPlayback(version.id)
            val resume = ApiClient.resumeState(if (episode == null) "movie" else "episode", playback.streamPath)
            return LibraryFileStart(group, versions, version, episode, following, playback, resume?.startPoint)
        }
    }
}

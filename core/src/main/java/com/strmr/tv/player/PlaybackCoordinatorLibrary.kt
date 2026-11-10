package com.strmr.tv.player

import com.strmr.tv.api.ApiClient
import com.strmr.tv.api.LibraryPlaybackInfo
import com.strmr.tv.api.libraryPlayback
import com.strmr.tv.library.LibraryVersion

/**
 * Plays a library copy (local, Plex or Jellyfin) instead of searching: the server names the file's
 * stream path and streams it like any other source. There is no server track pick, so the
 * profile's languages choose the audio and subtitles once the player has read them. `known` is
 * the file's playback info when the caller already fetched it.
 */
fun PlaybackCoordinator.playLibrary(request: PlaybackRequest, version: LibraryVersion, known: LibraryPlaybackInfo? = null) =
    begin(request.copy(libraryVersion = version), "Preparing…", resolve = {
        val playback = known ?: ApiClient.libraryPlayback(version.id)
        ResolvedStream(playback.streamPath, null, version.releaseName, pickTracksOnDevice = true)
    })

package com.strmr.mobile.downloads

import android.net.Uri
import com.strmr.tv.api.ApiClient
import com.strmr.tv.api.StartPoint
import com.strmr.tv.api.resumeState
import com.strmr.tv.app.AppLog
import com.strmr.tv.player.PlaybackCoordinator
import com.strmr.tv.player.PlaybackRequest
import com.strmr.tv.player.PlayerSession
import kotlinx.coroutines.withTimeoutOrNull

/**
 * Plays a finished download in the phone player from its file (the iPhone app's
 * `DownloadPlayback`). Progress still goes to the server under the title's ids, so watch
 * history and resume carry over; offline the reports are held ([OfflineProgress]) and sent
 * later. The player leaves out what needs the server for a local file (seek previews, Auto
 * Switch, Switch Source); there is no Up Next, as only this file is on the device.
 */
object DownloadPlayback {
    fun request(item: DownloadItem, start: StartPoint?): PlaybackRequest {
        val episode = if (item.seasonNumber != null && item.episodeNumber != null) {
            PlaybackRequest.EpisodeInfo(item.seasonNumber, item.episodeNumber, name = item.episodeName,
                                        itemId = item.episodeItemId, overview = item.episodeOverview)
        } else null
        return PlaybackRequest(item.target, episode, start, backdrops = listOfNotNull(item.backdropUrl))
    }

    /**
     * Where to offer resuming: the server's resume point when it answers within 2.5 s, else
     * the one kept on the device.
     */
    suspend fun resumePoint(item: DownloadItem): StartPoint? {
        val mediaType = if (item.isMovie) "movie" else "episode"
        val server = withTimeoutOrNull(2_500) { ApiClient.resumeState(mediaType, item.progressItemId) }
        return server?.startPoint ?: OfflineProgress.resumePoint(item)
    }

    /** Opens the player on the file; returns an error message when it can't. */
    fun play(item: DownloadItem, start: StartPoint?): String? {
        val file = DownloadStorage.file(item.fileName)
        if (!file.isFile) return "The downloaded file is missing."
        AppLog.i("Downloads", "playing download ${item.displayTitle} from ${file.name}")
        PlaybackCoordinator.playSession(
            PlayerSession(
                request = request(item, start),
                url = Uri.fromFile(file).toString(),
                tracks = item.tracks,
                // streamPath stays the server path: online subtitles and skip segments look it up there.
                streamPath = item.streamPath,
                releaseName = item.releaseName ?: item.fileName,
                pickTracksOnDevice = item.tracks == null,
                sourceLabel = "Downloaded",
                localFile = true,
                progressSink = OfflineProgress.sink(item),
            ),
        )
        return null
    }
}

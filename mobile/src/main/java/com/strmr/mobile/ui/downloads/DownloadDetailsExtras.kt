package com.strmr.mobile.ui.downloads

import androidx.compose.foundation.layout.RowScope
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DownloadDone
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Download
import androidx.compose.material.icons.outlined.DownloadForOffline
import androidx.compose.material.icons.outlined.Pause
import androidx.compose.runtime.Composable
import com.strmr.mobile.downloads.DownloadActions
import com.strmr.mobile.downloads.DownloadItem.Status
import com.strmr.mobile.downloads.DownloadPlayback
import com.strmr.mobile.downloads.DownloadRequest
import com.strmr.mobile.downloads.Downloads
import com.strmr.mobile.downloads.OfflineProgress
import com.strmr.mobile.ui.components.SheetAction
import com.strmr.mobile.ui.details.DetailsActionContext
import com.strmr.mobile.ui.details.DetailsExtraActions
import com.strmr.tv.api.DetailsTarget
import com.strmr.tv.api.Episode
import com.strmr.tv.ui.AppToast
import com.strmr.tv.ui.details.DetailsModel

/**
 * Downloads on the details page (the iPhone app's Download entries): the round Download
 * action for the movie or the episode Play targets, Download Season… in More, and an episode's
 * download (Download, Play / Pause / Resume, Delete) in its long-press sheet. Registered by
 * the application.
 */
object DownloadDetailsExtras : DetailsExtraActions {
    @Composable
    override fun RowScope.Actions(context: DetailsActionContext) {
        DownloadActionItem(downloadTarget(context.model), if (context.model.target.isMovie) null else context.episode)
    }

    override fun moreActions(context: DetailsActionContext): List<SheetAction> {
        val model = context.model
        if (model.target.isMovie) return emptyList()
        val season = model.currentSeason?.takeIf { it.episodes.isNotEmpty() } ?: return emptyList()
        val name = if (season.number == 0) "Specials" else "Season ${season.number}"
        return listOf(SheetAction("Download $name…", Icons.Outlined.DownloadForOffline) {
            DownloadActions.pick(DownloadRequest(downloadTarget(model), season = season))
        })
    }

    override fun episodeActions(context: DetailsActionContext, episode: Episode): List<SheetAction> {
        val target = downloadTarget(context.model)
        val item = Downloads.find(target, episode)
            ?: return listOf(SheetAction("Download ${episode.code}…", Icons.Outlined.Download) {
                DownloadActions.pick(DownloadRequest(target, episode))
            })
        val primary = when (item.status) {
            Status.COMPLETED -> SheetAction("Play Download", Icons.Filled.DownloadDone) {
                // From the sheet there is no prompt: resume from what the device knows.
                DownloadPlayback.play(item, OfflineProgress.resumePoint(item) ?: context.model.resumePoint(episode))
                    ?.let(AppToast::show)
            }
            Status.DOWNLOADING, Status.PENDING ->
                SheetAction("Downloading ${Math.round(item.progress * 100)}% · Pause", Icons.Outlined.Pause) { Downloads.pause(item.id) }
            Status.PAUSED -> SheetAction("Resume Download", Icons.Outlined.Download) { Downloads.resume(item.id) }
            Status.FAILED -> SheetAction("Retry Download", Icons.Outlined.Download) { Downloads.resume(item.id) }
        }
        val delete = SheetAction(if (item.isActive) "Cancel Download" else "Delete Download", Icons.Outlined.Delete, destructive = true) {
            Downloads.delete(item.id)
        }
        return listOf(primary, delete)
    }

    /** The page's title with the art and overview the details load filled in, kept for the offline list. */
    fun downloadTarget(model: DetailsModel): DetailsTarget {
        val t = model.target
        val title = model.title ?: return t
        return t.copy(
            name = title.name.ifEmpty { t.name }, year = title.year ?: t.year,
            tmdbId = t.tmdbId ?: title.tmdbId?.toString(), imdbId = t.imdbId ?: title.imdbId,
            tvdbId = t.tvdbId ?: title.tvdbId?.toString(), posterUrl = title.poster?.url ?: t.posterUrl,
            backdropUrl = title.backdrop?.url ?: t.backdropUrl, overview = title.overview ?: t.overview,
        )
    }
}

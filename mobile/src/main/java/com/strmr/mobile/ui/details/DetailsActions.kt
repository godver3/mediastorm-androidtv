package com.strmr.mobile.ui.details

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.PlaylistAdd
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.outlined.BookmarkBorder
import androidx.compose.material.icons.outlined.Movie
import androidx.compose.material.icons.outlined.Replay
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.Storage
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.strmr.tv.library.CustomListsStore
import com.strmr.tv.library.WatchlistStore
import com.strmr.tv.ui.AppIcons
import com.strmr.tv.ui.details.DetailsModel
import com.strmr.tv.ui.details.TrailerController
import kotlinx.coroutines.launch

/**
 * Play / Resume (filling the width) and, with a resume point, Start Over beside it (the iPhone
 * page's split) with the progress under the pair.
 */
@Composable
fun PrimaryActions(state: DetailsPageState) {
    val model = state.model
    val primary = state.primary
    val resuming = primary?.start != null
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Button(
                onClick = state::playPrimary,
                enabled = primary != null && model.title != null,
                modifier = Modifier.weight(1f).height(48.dp),
            ) {
                Icon(Icons.Filled.PlayArrow, contentDescription = null, modifier = Modifier.size(ButtonDefaults.IconSize))
                Text(primary?.label ?: "Play", Modifier.padding(start = ButtonDefaults.IconSpacing), maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            if (resuming) {
                FilledTonalButton(onClick = state::startOver, modifier = Modifier.width(150.dp).height(48.dp)) {
                    Icon(Icons.Outlined.Replay, contentDescription = null, modifier = Modifier.size(ButtonDefaults.IconSize))
                    Text("Start Over", Modifier.padding(start = ButtonDefaults.IconSpacing), maxLines = 1)
                }
            }
        }
        val start = primary?.start
        if (start != null) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                LinearProgressIndicator(
                    progress = { model.resumeFraction(primary.episode) },
                    modifier = Modifier.weight(1f),
                    drawStopIndicator = {},
                )
                Text("From ${start.label}", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

/**
 * The secondary actions as round tonal buttons with captions (the iPhone page's row): Choose
 * Source, Versions (library copies), Trailer, Watchlist, Lists and the watch state (a toggle
 * for a movie, the bulk sheet for a series), then whatever [DetailsExtras] adds.
 */
@Composable
fun SecondaryActions(state: DetailsPageState, trailer: TrailerController, extras: DetailsActionContext) {
    val model = state.model
    val target = model.target
    val scope = rememberCoroutineScope()
    var watchlistBusy by remember { mutableStateOf(false) }
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly, verticalAlignment = Alignment.Top) {
        DetailsActionItem("Sources", Icons.Outlined.Search, enabled = target.isMovie || state.activeEpisode != null) {
            state.sourcesFor = EpisodeTarget(state.activeEpisode)
        }
        if (model.libraryVersions(state.activeEpisode).isNotEmpty()) {
            DetailsActionItem("Versions", Icons.Outlined.Storage) { state.versionsFor = EpisodeTarget(state.activeEpisode) }
        }
        if (trailer.available) {
            DetailsActionItem("Trailer", Icons.Outlined.Movie, busy = trailer.preparingFullScreen) { trailer.buttonPressed(target.name) }
        }
        val inList = WatchlistStore.contains(target, model.serverInWatchlist)
        DetailsActionItem(if (inList) "Saved" else "Watchlist", if (inList) Icons.Filled.Bookmark else Icons.Outlined.BookmarkBorder) {
            if (!watchlistBusy) {
                watchlistBusy = true
                scope.launch {
                    runCatching { WatchlistStore.toggle(target, model.serverInWatchlist) }
                    watchlistBusy = false
                }
            }
        }
        DetailsActionItem("Lists", Icons.AutoMirrored.Outlined.PlaylistAdd) { CustomListsStore.picking = target }
        if (target.isMovie) {
            val watched = model.movieWatched == true
            DetailsActionItem(if (watched) "Watched" else "Unwatched", if (watched) AppIcons.WatchedAll else AppIcons.WatchedNone) {
                scope.launch { model.toggleMovieWatched() }
            }
        } else if (model.seasons.isNotEmpty()) {
            val icon = when (model.seriesWatchState) {
                DetailsModel.WatchState.NONE -> AppIcons.WatchedNone
                DetailsModel.WatchState.PARTIAL -> AppIcons.WatchedPartial
                DetailsModel.WatchState.COMPLETE -> AppIcons.WatchedAll
            }
            DetailsActionItem("Watched", icon) { state.watchState = true }
        }
        DetailsExtras.providers.forEach { it.run { Actions(extras) } }
    }
}

/** A round tonal icon button with a caption under it (a spinner in place of the icon while [busy]). */
@Composable
fun RowScope.DetailsActionItem(caption: String, icon: ImageVector, enabled: Boolean = true, busy: Boolean = false, onClick: () -> Unit) {
    Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(4.dp)) {
        FilledTonalIconButton(onClick = onClick, enabled = enabled, modifier = Modifier.size(52.dp)) {
            if (busy) CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp) else Icon(icon, contentDescription = caption)
        }
        Text(
            caption, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1,
            overflow = TextOverflow.Ellipsis, modifier = Modifier.clearAndSetSemantics { },
        )
    }
}

package com.strmr.tv.ui.details

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.tv.material3.Button
import androidx.tv.material3.ButtonDefaults
import androidx.tv.material3.Icon
import androidx.tv.material3.IconButton
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import coil.compose.SubcomposeAsyncImage
import com.strmr.tv.api.Artwork
import com.strmr.tv.library.CustomListsStore
import com.strmr.tv.library.WatchlistStore
import com.strmr.tv.player.WarmPrequeue
import com.strmr.tv.ui.AppIcons
import com.strmr.tv.ui.ContentMargin
import com.strmr.tv.ui.ExpandableText
import com.strmr.tv.ui.home.BadgeLabel
import kotlinx.coroutines.launch

/**
 * The first screen's lower-left block: title logo (else the name), the metadata line, three
 * lines of overview and the actions, in the tvOS order: Play / Resume, Start Over, Choose
 * Source, Audio & Subtitles (the prequeued stream's tracks, [PrequeueTracksButton]), Trailer, Watchlist, Add to List, watched (a series opens [onWatchState]) and More
 * (Set Aside, Continue Watching and Hide, [DetailsMoreMenu]).
 */
@Composable
fun BoxScope.DetailsHeader(
    model: DetailsModel,
    trailer: TrailerController,
    onPlay: (DetailsModel.Primary) -> Unit,
    onStartOver: (DetailsModel.Primary) -> Unit,
    onChooseSource: () -> Unit,
    prequeueTracks: PrequeueTracks,
    warm: WarmPrequeue?,
    onWatchState: () -> Unit,
    onMore: () -> Unit,
    onFocusChanged: (Boolean) -> Unit,
) {
    val target = model.target
    val primary = model.primary
    val playFocus = remember { FocusRequester() }
    // The primary action only exists once details have loaded.
    LaunchedEffect(model.title != null) { if (model.title != null) runCatching { playFocus.requestFocus() } }

    Column(
        Modifier.align(Alignment.BottomStart).padding(start = ContentMargin, end = ContentMargin, bottom = 32.dp)
            .onFocusChanged { onFocusChanged(it.hasFocus) },
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        val logo = Artwork.logo(model.title?.logo?.url ?: target.logoUrl)
        if (logo != null) {
            SubcomposeAsyncImage(
                model = logo,
                contentDescription = target.name,
                contentScale = ContentScale.Fit,
                alignment = Alignment.BottomStart,
                error = { TitleText(target.name) },
                modifier = Modifier.height(96.dp).widthIn(max = 340.dp),
            )
        } else {
            TitleText(target.name)
        }
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
            model.headerBadge?.let { BadgeLabel(it.text, it.symbol) }
            Text(model.metadataLine, fontSize = 13.sp, fontWeight = FontWeight.Medium, color = Color.White.copy(alpha = 0.7f))
        }
        (model.title?.overview ?: target.overview)?.let {
            // Cut off, it is focusable and opens whole over the page (Up from the actions reaches it).
            ExpandableText(it, title = target.name, maxLines = 3, fontSize = 13.sp, modifier = Modifier.widthIn(max = 520.dp))
        }
        (model.loadError ?: model.watchedError ?: trailer.errorMessage)?.let { Text(it, fontSize = 12.sp, color = Color(0xFFFF6B6B)) }

        Row(Modifier.padding(top = 8.dp), horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
            // One primary button whose label changes as details load (Play → Resume), rather
            // than swapping buttons, so focus stays put.
            Button(
                onClick = { primary?.let(onPlay) },
                contentPadding = ButtonDefaults.ButtonWithIconContentPadding,
                modifier = Modifier.focusRequester(playFocus),
            ) {
                Icon(Icons.Filled.PlayArrow, contentDescription = null, modifier = Modifier.size(ButtonDefaults.IconSize))
                Spacer(Modifier.width(ButtonDefaults.IconSpacing))
                Text(primary?.label ?: "Play")
            }
            if (primary?.start != null) {
                // Filled like the icon buttons beside it (the tvOS secondary button), not outlined.
                Button(
                    onClick = { onStartOver(primary) },
                    contentPadding = ButtonDefaults.ButtonWithIconContentPadding,
                    colors = ButtonDefaults.colors(
                        containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.8f),
                        contentColor = MaterialTheme.colorScheme.onSurface,
                    ),
                ) {
                    Icon(AppIcons.Restart, contentDescription = null, modifier = Modifier.size(ButtonDefaults.IconSize))
                    Spacer(Modifier.width(ButtonDefaults.IconSpacing))
                    Text("Start Over")
                }
            }
            // Secondary actions are icon-only, as on the Expo details page.
            IconButton(onClick = onChooseSource, enabled = target.isMovie || model.primaryEpisode != null) {
                Icon(Icons.Filled.Search, contentDescription = "Choose Source")
            }
            PrequeueTracksButton(prequeueTracks, warm)
            if (trailer.available) {
                IconButton(onClick = { trailer.buttonPressed(target.name) }) {
                    when {
                        trailer.preparingFullScreen -> CircularProgressIndicator(Modifier.size(18.dp), color = Color.White, strokeWidth = 2.dp)
                        trailer.showing && !trailer.paused -> Icon(AppIcons.Pause, contentDescription = "Pause Trailer")
                        else -> Icon(AppIcons.Film, contentDescription = "Play Trailer")
                    }
                }
            }
            WatchlistButton(model)
            IconButton(onClick = { CustomListsStore.picking = target }) {
                Icon(AppIcons.PlaylistAdd, contentDescription = "Add to List")
            }
            WatchedButton(model, onWatchState)
            IconButton(onClick = onMore) { Icon(AppIcons.MoreHoriz, contentDescription = "More") }
        }
    }
}

/** One button whose icon flips, so focus stays on it; repeat presses while a change is in flight are ignored. */
@Composable
private fun WatchlistButton(model: DetailsModel) {
    val scope = rememberCoroutineScope()
    var busy by remember { mutableStateOf(false) }
    val inList = WatchlistStore.contains(model.target, model.serverInWatchlist)
    IconButton(onClick = {
        if (busy) return@IconButton
        busy = true
        scope.launch {
            runCatching { WatchlistStore.toggle(model.target, model.serverInWatchlist) }
            busy = false
        }
    }) {
        Icon(if (inList) AppIcons.Bookmark else AppIcons.BookmarkBorder, contentDescription = if (inList) "In Watchlist" else "Add to Watchlist")
    }
}

/** Movies toggle watched in place; a series opens the bulk watch-state dialog. The eye shows none / some / all. */
@Composable
private fun WatchedButton(model: DetailsModel, onWatchState: () -> Unit) {
    val scope = rememberCoroutineScope()
    if (model.target.isMovie) {
        val watched = model.movieWatched == true
        IconButton(onClick = { scope.launch { model.toggleMovieWatched() } }) {
            Icon(if (watched) AppIcons.WatchedAll else AppIcons.WatchedNone, contentDescription = if (watched) "Watched" else "Mark as Watched")
        }
    } else if (model.seasons.isNotEmpty()) {
        IconButton(onClick = onWatchState) {
            val icon = when (model.seriesWatchState) {
                DetailsModel.WatchState.NONE -> AppIcons.WatchedNone
                DetailsModel.WatchState.PARTIAL -> AppIcons.WatchedPartial
                DetailsModel.WatchState.COMPLETE -> AppIcons.WatchedAll
            }
            Icon(icon, contentDescription = "Watch State")
        }
    }
}

@Composable
private fun TitleText(name: String) {
    Box(Modifier.widthIn(max = 520.dp)) {
        Text(name, fontSize = 34.sp, fontWeight = FontWeight.Bold, maxLines = 2, overflow = TextOverflow.Ellipsis)
    }
}

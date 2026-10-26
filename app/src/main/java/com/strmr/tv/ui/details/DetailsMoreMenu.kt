package com.strmr.tv.ui.details

import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.tv.material3.ListItem
import androidx.tv.material3.Text
import com.strmr.tv.api.ApiClient
import com.strmr.tv.api.ContinueWatchingItem
import com.strmr.tv.api.DetailsTarget
import com.strmr.tv.home.continueWatching
import com.strmr.tv.library.CustomListsStore
import com.strmr.tv.library.HiddenItemsStore
import com.strmr.tv.library.SetAsideStore
import com.strmr.tv.library.TitleActions
import com.strmr.tv.library.WatchlistStore
import com.strmr.tv.player.WarmPrequeue
import com.strmr.tv.ui.DialogChoice
import com.strmr.tv.ui.SidePanelDialog
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

/**
 * The details page's More menu (the Expo details page's More Options, its title actions):
 * Set Aside and Remove from Continue Watching while the title is in Continue Watching, Remove
 * from Set Aside while it is set aside, and Hide from this profile (Unhide when hidden).
 * Hiding closes the page, as in the Expo app. A side panel, like Watch State and Manage Lists.
 * Actions run in `scope` (the page's). Above them, the row's actions Customize Buttons moved here
 * (`moved`, [movedActions]); Customize Buttons… comes last.
 */
@Composable
fun DetailsMoreMenu(
    target: DetailsTarget,
    scope: CoroutineScope,
    moved: List<DialogChoice>,
    onCustomize: () -> Unit,
    onHidden: () -> Unit,
    onDismiss: () -> Unit,
) {
    var inContinueWatching by remember { mutableStateOf(false) }
    LaunchedEffect(target.titleId) {
        SetAsideStore.refresh()
        inContinueWatching = runCatching { ApiClient.continueWatching() }.getOrNull().orEmpty().any { it.isTitle(target) }
    }
    val choices = buildList {
        addAll(moved)
        if (inContinueWatching) {
            add(DialogChoice("Set Aside") { scope.launch { TitleActions.setAside(target) } })
            add(DialogChoice("Remove from Continue Watching") { scope.launch { TitleActions.hideFromContinueWatching(target) } })
        }
        if (SetAsideStore.contains(target)) add(DialogChoice("Remove from Set Aside") { scope.launch { TitleActions.removeFromSetAside(target) } })
        if (HiddenItemsStore.isHidden(target)) {
            add(DialogChoice("Unhide") { scope.launch { TitleActions.unhide(target) } })
        } else {
            add(DialogChoice("Hide from this profile") { scope.launch { if (TitleActions.hide(target)) onHidden() } })
        }
        add(DialogChoice("Customize Buttons…", onCustomize))
    }
    val first = remember { FocusRequester() }
    // The rows change once Continue Watching is read; focus the first one each time.
    LaunchedEffect(choices.map { it.label }) { withFrameNanos { }; runCatching { first.requestFocus() } }
    SidePanelDialog("More Options", target.name, onDismiss) {
        itemsIndexed(choices) { index, choice ->
            ListItem(
                selected = false,
                onClick = { onDismiss(); choice.action() },
                headlineContent = { Text(choice.label) },
                modifier = if (index == 0) Modifier.focusRequester(first) else Modifier,
            )
        }
    }
}

/** The Continue Watching entry for this title: its ID, or a shared provider ID. */
private fun ContinueWatchingItem.isTitle(target: DetailsTarget): Boolean {
    if (isMovie != target.isMovie) return false
    if (seriesId == target.titleId) return true
    val ids = externalIds ?: return false
    return listOf("tmdb" to target.tmdbId, "imdb" to target.imdbId, "tvdb" to target.tvdbId)
        .any { (key, value) -> !value.isNullOrEmpty() && ids[key] == value }
}

/**
 * The row's actions set to More, in row order, each labelled with its current state. The
 * callbacks are the page's, as the row's buttons use them.
 */
@Composable
fun movedActions(
    model: DetailsModel,
    trailer: TrailerController,
    prequeueTracks: PrequeueTracks,
    warm: WarmPrequeue?,
    scope: CoroutineScope,
    onStartOver: (DetailsModel.Primary) -> Unit,
    onVersions: () -> Unit,
    onChooseSource: () -> Unit,
    onWatchState: () -> Unit,
): List<DialogChoice> {
    val more = DetailsButtons.inMore()
    val target = model.target
    val primary = model.primary
    val inWatchlist = WatchlistStore.contains(target, model.serverInWatchlist)
    return buildList {
        if (DetailsAction.START_OVER in more && primary?.start != null) add(DialogChoice("Start Over") { onStartOver(primary) })
        if (DetailsAction.VERSIONS in more && model.primaryVersions.isNotEmpty()) add(DialogChoice("Versions", onVersions))
        if (DetailsAction.CHOOSE_SOURCE in more && (target.isMovie || model.primaryEpisode != null)) {
            add(DialogChoice("Choose Source", onChooseSource))
        }
        if (DetailsAction.TRACKS in more && prequeueTracks.visible(warm)?.tracks != null) {
            add(DialogChoice("Audio & Subtitles") { prequeueTracks.open = true })
        }
        if (DetailsAction.TRAILER in more && trailer.available) {
            add(DialogChoice(if (trailer.showing && !trailer.paused) "Pause Trailer" else "Play Trailer") { trailer.buttonPressed(target.name) })
        }
        if (DetailsAction.WATCHLIST in more) {
            add(DialogChoice(if (inWatchlist) "Remove from Watchlist" else "Add to Watchlist") {
                scope.launch { runCatching { WatchlistStore.toggle(target, model.serverInWatchlist) } }
            })
        }
        if (DetailsAction.LISTS in more) add(DialogChoice("Manage Lists…") { CustomListsStore.picking = target })
        if (DetailsAction.WATCHED in more) {
            if (target.isMovie) {
                add(DialogChoice(if (model.movieWatched == true) "Mark as Unwatched" else "Mark as Watched") {
                    scope.launch { model.toggleMovieWatched() }
                })
            } else if (model.seasons.isNotEmpty()) {
                add(DialogChoice("Watch State…", onWatchState))
            }
        }
    }
}

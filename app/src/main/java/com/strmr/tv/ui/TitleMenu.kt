package com.strmr.tv.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import com.strmr.tv.api.ApiClient
import com.strmr.tv.api.isMovieWatched
import com.strmr.tv.home.HomeCard
import com.strmr.tv.library.CustomListsStore
import com.strmr.tv.library.HiddenItemsStore
import com.strmr.tv.library.SetAsideStore
import com.strmr.tv.library.TitleActions
import com.strmr.tv.library.WatchlistStore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

/**
 * A title's long-press menu, in the Expo app's order: on a Continue Watching card Hide from
 * Continue Watching and Set Aside; Remove from Set Aside for a set-aside title; Add to List…;
 * the watchlist toggle; Hide from this profile (Unhide when it is hidden); Mark as watched /
 * unwatched for a movie (its current state read when the menu opens), the bulk watch-state
 * sheet for a series; then any `extra` choices. Actions run in `scope` (the caller's, which
 * outlives the menu) and report failures to `onError`.
 */
@Composable
fun TitleMenu(
    card: HomeCard,
    scope: CoroutineScope,
    extra: List<DialogChoice> = emptyList(),
    continueWatching: Boolean = false,
    onError: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    val target = card.target ?: return
    val navigator = LocalNavigator.current
    val inWatchlist = WatchlistStore.contains(target)
    var watched by remember { mutableStateOf<Boolean?>(null) }
    LaunchedEffect(target.titleId) {
        if (target.isMovie) watched = runCatching { ApiClient.isMovieWatched(target) }.getOrNull()
        SetAsideStore.refresh()
    }
    fun act(action: suspend () -> Unit) = scope.launch { action() }

    val choices = buildList {
        if (continueWatching) {
            add(DialogChoice("Hide from Continue Watching") { act { TitleActions.hideFromContinueWatching(target) } })
            add(DialogChoice("Set Aside") { act { TitleActions.setAside(target) } })
        }
        if (SetAsideStore.contains(target)) add(DialogChoice("Remove from Set Aside") { act { TitleActions.removeFromSetAside(target) } })
        add(DialogChoice("Add to List…") { CustomListsStore.picking = target })
        add(DialogChoice(if (inWatchlist) "Remove from Watchlist" else "Add to Watchlist") {
            scope.launch { runCatching { WatchlistStore.toggle(target) }.onFailure { onError(it.message ?: "Unknown error") } }
        })
        if (HiddenItemsStore.isHidden(target)) {
            add(DialogChoice("Unhide") { act { TitleActions.unhide(target) } })
        } else {
            add(DialogChoice("Hide from this profile") { act { TitleActions.hide(target) } })
        }
        if (target.isMovie) {
            // A partly watched movie (Continue Watching) or an unknown state offers Mark as watched.
            val isWatched = watched == true
            add(DialogChoice(if (isWatched) "Mark as unwatched" else "Mark as watched") { act { TitleActions.setMovieWatched(target, !isWatched) } })
        } else {
            add(DialogChoice("Mark as watched/unwatched (bulk)") { navigator.openDetails(target, openWatchState = true) })
        }
        addAll(extra)
        add(DialogChoice("Cancel") {})
    }
    ChoiceDialog(title = card.title, choices = choices, onDismiss = onDismiss)
}

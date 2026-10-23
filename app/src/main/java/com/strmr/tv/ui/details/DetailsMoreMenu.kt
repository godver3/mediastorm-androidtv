package com.strmr.tv.ui.details

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import com.strmr.tv.api.ApiClient
import com.strmr.tv.api.ContinueWatchingItem
import com.strmr.tv.api.DetailsTarget
import com.strmr.tv.home.continueWatching
import com.strmr.tv.library.HiddenItemsStore
import com.strmr.tv.library.SetAsideStore
import com.strmr.tv.library.TitleActions
import com.strmr.tv.ui.ChoiceDialog
import com.strmr.tv.ui.DialogChoice
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

/**
 * The details page's More menu (the Expo details page's More Options, its title actions):
 * Set Aside and Remove from Continue Watching while the title is in Continue Watching, Remove
 * from Set Aside while it is set aside, and Hide from this profile (Unhide when hidden).
 * Hiding closes the page, as in the Expo app. Actions run in `scope` (the page's).
 */
@Composable
fun DetailsMoreMenu(target: DetailsTarget, scope: CoroutineScope, onHidden: () -> Unit, onDismiss: () -> Unit) {
    var inContinueWatching by remember { mutableStateOf(false) }
    LaunchedEffect(target.titleId) {
        SetAsideStore.refresh()
        inContinueWatching = runCatching { ApiClient.continueWatching() }.getOrNull().orEmpty().any { it.isTitle(target) }
    }
    val choices = buildList {
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
        add(DialogChoice("Close") {})
    }
    ChoiceDialog(title = target.name, choices = choices, onDismiss = onDismiss)
}

/** The Continue Watching entry for this title: its ID, or a shared provider ID. */
private fun ContinueWatchingItem.isTitle(target: DetailsTarget): Boolean {
    if (isMovie != target.isMovie) return false
    if (seriesId == target.titleId) return true
    val ids = externalIds ?: return false
    return listOf("tmdb" to target.tmdbId, "imdb" to target.imdbId, "tvdb" to target.tvdbId)
        .any { (key, value) -> !value.isNullOrEmpty() && ids[key] == value }
}

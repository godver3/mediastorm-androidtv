package com.strmr.mobile.ui.details

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.PlaylistAdd
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.Checklist
import androidx.compose.material.icons.outlined.FormatListNumbered
import androidx.compose.material.icons.outlined.PauseCircle
import androidx.compose.material.icons.outlined.PlayArrow
import androidx.compose.material.icons.outlined.RemoveCircleOutline
import androidx.compose.material.icons.outlined.Replay
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.Storage
import androidx.compose.material.icons.outlined.Visibility
import androidx.compose.material.icons.outlined.VisibilityOff
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import com.strmr.mobile.ui.components.ActionSheet
import com.strmr.mobile.ui.components.SheetAction
import com.strmr.mobile.ui.components.SheetHeader
import com.strmr.tv.api.ApiClient
import com.strmr.tv.api.Episode
import com.strmr.tv.home.continueWatching
import com.strmr.tv.library.CustomListsStore
import com.strmr.tv.library.HiddenItemsStore
import com.strmr.tv.library.SetAsideStore
import com.strmr.tv.library.TitleActions
import com.strmr.tv.player.WarmPrequeue
import com.strmr.tv.ui.AppIcons
import com.strmr.tv.ui.details.isTitle
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

/**
 * The top bar's More (the Expo page's More Options with the TV page's extras): Audio &
 * Subtitles, Choose Source, Versions, Add to List…, Watch State…, Set Aside and Remove from
 * Continue Watching while the title is in Continue Watching, Remove from Set Aside, Episode
 * Order…, then Hide from This Profile (which leaves the page) or Unhide, and any added actions.
 */
@Composable
fun DetailsMoreSheet(state: DetailsPageState, warm: WarmPrequeue?, scope: CoroutineScope, extras: DetailsActionContext, onHidden: () -> Unit) {
    val model = state.model
    val target = model.target
    var inContinueWatching by remember { mutableStateOf(false) }
    LaunchedEffect(target.titleId) {
        SetAsideStore.refresh()
        inContinueWatching = runCatching { ApiClient.continueWatching() }.getOrNull().orEmpty().any { it.isTitle(target) }
    }
    val actions = buildList {
        if (state.prequeueTracks.visible(warm)?.tracks != null) add(SheetAction("Audio & Subtitles", AppIcons.Waveform) { state.prequeueTracks.open = true })
        if (target.isMovie || state.activeEpisode != null) add(SheetAction("Choose Source", Icons.Outlined.Search) { state.sourcesFor = EpisodeTarget(state.activeEpisode) })
        if (model.libraryVersions(state.activeEpisode).isNotEmpty()) {
            add(SheetAction("Versions", Icons.Outlined.Storage) { state.versionsFor = EpisodeTarget(state.activeEpisode) })
        }
        DetailsExtras.providers.forEach { addAll(it.moreActions(extras)) }
        add(SheetAction("Add to List…", Icons.AutoMirrored.Outlined.PlaylistAdd) { CustomListsStore.picking = target })
        if (!target.isMovie && model.seasons.isNotEmpty()) add(SheetAction("Watch State…", Icons.Outlined.Checklist) { state.watchState = true })
        if (inContinueWatching) {
            add(SheetAction("Set Aside", Icons.Outlined.PauseCircle) { scope.launch { TitleActions.setAside(target) } })
            add(SheetAction("Remove from Continue Watching", Icons.Outlined.RemoveCircleOutline) {
                scope.launch { TitleActions.hideFromContinueWatching(target) }
            })
        }
        if (SetAsideStore.contains(target)) {
            add(SheetAction("Remove from Set Aside", Icons.Outlined.PauseCircle) { scope.launch { TitleActions.removeFromSetAside(target) } })
        }
        if (model.offersOrderings) {
            add(SheetAction("Episode Order: ${model.currentOrdering?.name ?: "Default"}…", Icons.Outlined.FormatListNumbered) { state.episodeOrder = true })
        }
        if (HiddenItemsStore.isHidden(target)) {
            add(SheetAction("Unhide", Icons.Outlined.Visibility) { scope.launch { TitleActions.unhide(target) } })
        } else {
            add(SheetAction("Hide from This Profile", Icons.Outlined.VisibilityOff, destructive = true) {
                scope.launch { if (TitleActions.hide(target)) onHidden() }
            })
        }
    }
    ActionSheet({ state.more = false }, header = { SheetHeader(target.name, model.metadataLine, target.posterUrl) }, actions = actions)
}

/**
 * An episode's long press: Play (or Resume) and Start Over, Choose Version (library copies),
 * Choose Source, Mark as Watched / Unwatched, and any added actions (Download).
 */
@Composable
fun EpisodeMenuSheet(state: DetailsPageState, episode: Episode, scope: CoroutineScope, extras: DetailsActionContext) {
    val model = state.model
    val resumeAt = model.resumePoint(episode)
    val watched = model.isWatched(episode)
    val actions = buildList {
        add(SheetAction(resumeAt?.let { "Resume from ${it.label}" } ?: "Play", Icons.Outlined.PlayArrow) { state.play(episode, resumeAt) })
        if (resumeAt != null) add(SheetAction("Start Over", Icons.Outlined.Replay) { state.play(episode, null) })
        if (model.libraryVersions(episode).isNotEmpty()) add(SheetAction("Choose Version", Icons.Outlined.Storage) { state.versionsFor = EpisodeTarget(episode) })
        add(SheetAction("Choose Source", Icons.Outlined.Search) { state.sourcesFor = EpisodeTarget(episode) })
        DetailsExtras.providers.forEach { addAll(it.episodeActions(extras, episode)) }
        add(SheetAction(if (watched) "Mark as Unwatched" else "Mark as Watched", Icons.Outlined.CheckCircle) {
            scope.launch { model.toggleWatched(episode) }
        })
    }
    ActionSheet(
        { state.episodeMenu = null },
        header = { SheetHeader("${episode.code} · ${episode.name}", model.target.name, null) },
        actions = actions,
    )
}

/**
 * Episode Order (TVDB season types: Aired, DVD, Absolute, …): the current one checked. Picking
 * one saves it for the profile and reloads the page in it. A non-default order pauses
 * watch-history sync for the show, which the header says.
 */
@Composable
fun EpisodeOrderSheet(state: DetailsPageState, scope: CoroutineScope) {
    val model = state.model
    val current = model.currentOrdering
    val actions = model.orderings.map { ordering ->
        val label = if (ordering.isOfficial) "${ordering.name} (Default)" else ordering.name
        SheetAction(label, if (ordering.type == current?.type) Icons.Outlined.CheckCircle else Icons.Outlined.FormatListNumbered) {
            state.selectedEpisodeId = null
            scope.launch { model.pickOrdering(ordering) }
        }
    }
    ActionSheet(
        { state.episodeOrder = false },
        header = { SheetHeader("Episode Order", "Non-default orders pause watch-history sync for this show.", null) },
        actions = actions,
    )
}

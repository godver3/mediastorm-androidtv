package com.strmr.mobile.ui.details

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.strmr.mobile.ui.shell.LocalNavigator
import com.strmr.tv.api.DetailsTarget
import com.strmr.tv.library.TitleActions
import com.strmr.tv.player.PlaybackCoordinator
import com.strmr.tv.ui.details.DetailsModel
import com.strmr.tv.ui.details.TrailerController
import com.strmr.tv.ui.settings.SpoilerSettings

/**
 * A title's page on a phone or tablet, laid out like the Expo and iPhone phone pages: the
 * backdrop with the logo, metadata, ratings and overview, Play / Resume with Start Over and the
 * secondary actions, what Play will use (library copy or prequeued stream with its Audio &
 * Subtitles), then (series) the season chips over the episode row, and the collection, More
 * Like This and cast rows. One `/details-bundle` load ([DetailsModel]); playback goes through
 * the shared PlaybackCoordinator as on the TV page. [autoplay] plays the primary action once,
 * when the first load has it (a deep link's Play).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DetailsScreen(target: DetailsTarget, autoplay: Boolean) {
    val model = remember(target) { DetailsModel(target) }
    val state = remember(model) { DetailsPageState(model) }
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    val navigator = LocalNavigator.current
    val trailer = remember(model) { TrailerController(context, scope) }
    val sessionsEnded by PlaybackCoordinator.sessionsEnded.collectAsState()
    val warm by PlaybackCoordinator.warm.collectAsState()
    val list = rememberLazyListState()
    val extras = DetailsActionContext(model, state.activeEpisode, state::playPrimary)

    LaunchedEffect(model) { PlaybackCoordinator.prewarm(target) }
    LaunchedEffect(model) { model.loadLibrary() }
    LaunchedEffect(model) { if (!target.isMovie) SpoilerSettings.refresh() }
    DisposableEffect(trailer) { onDispose { trailer.release() } }
    // A title action taken elsewhere (a menu over this page, another page): show its new state.
    val actions = TitleActions.changes
    val actionsAtOpen = remember { actions }
    LaunchedEffect(actions) { if (actions != actionsAtOpen) model.load() }
    // Saved, so coming back to the page doesn't play it again.
    var autoplayed by rememberSaveable { mutableStateOf(!autoplay) }
    LaunchedEffect(sessionsEnded) {
        // The track picks were for the prequeue that just played.
        if (sessionsEnded > 0) state.prequeueTracks.clear()
        model.load()
        if (!autoplayed && state.primary != null) {
            autoplayed = true
            state.playPrimary()
            return@LaunchedEffect
        }
        autoplayed = true
        // A phone plays the trailer full screen from its button (as the iPhone page); no backdrop trailer.
        trailer.prepare(model.trailers?.primary)
        trailer.dismiss("no backdrop trailer on phones")
    }

    Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
        LazyColumn(
            Modifier.fillMaxSize(),
            state = list,
            contentPadding = PaddingValues(bottom = 32.dp),
            verticalArrangement = Arrangement.spacedBy(24.dp),
        ) {
            item(key = "hero") { DetailsHero(model) }
            item(key = "info") {
                Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.TopCenter) {
                    Column(Modifier.widthIn(max = 760.dp).padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(20.dp)) {
                        DetailsInfo(model, model.loadError ?: model.watchedError ?: trailer.errorMessage)
                        PrimaryActions(state)
                        SecondaryActions(state, trailer, extras)
                        DetailsSourceLine(state, warm)
                    }
                }
            }
            if (!target.isMovie) {
                if (model.seasons.isNotEmpty()) {
                    item(key = "episodes") { EpisodesSection(state) }
                } else if (model.title == null && model.loadError == null) {
                    item(key = "loading") { Box(Modifier.fillMaxWidth().padding(24.dp), contentAlignment = Alignment.Center) { CircularProgressIndicator() } }
                }
            }
            relatedRows(model)
        }
        val scrolled by remember { derivedStateOf { list.firstVisibleItemIndex > 0 } }
        DetailsTopBar(target.name, scrolled, extras) { state.more = true }
    }

    DetailsPresentations(state, warm, scope, extras, onHidden = { navigator.pop() })
}

/** Transparent over the hero, the page's colour with the title once scrolled past it. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun DetailsTopBar(title: String, scrolled: Boolean, extras: DetailsActionContext, onMore: () -> Unit) {
    val navigator = LocalNavigator.current
    val onArt = if (scrolled) MaterialTheme.colorScheme.onSurface else Color.White
    TopAppBar(
        title = { if (scrolled) Text(title, maxLines = 1, overflow = TextOverflow.Ellipsis) },
        navigationIcon = { IconButton(onClick = { navigator.pop() }) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back") } },
        actions = {
            DetailsExtras.providers.forEach { it.run { TopBar(extras) } }
            IconButton(onClick = onMore) { Icon(Icons.Filled.MoreVert, contentDescription = "More Options") }
        },
        colors = TopAppBarDefaults.topAppBarColors(
            containerColor = if (scrolled) MaterialTheme.colorScheme.surfaceContainer else Color.Transparent,
            navigationIconContentColor = onArt, actionIconContentColor = onArt,
        ),
        windowInsets = TopAppBarDefaults.windowInsets,
    )
}

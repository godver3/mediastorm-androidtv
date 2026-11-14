package com.strmr.tv.ui

import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.platform.LocalView
import android.view.ViewTreeObserver
import com.strmr.tv.home.SourceRecheckEvents
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.findViewTreeLifecycleOwner
import com.strmr.tv.player.PlaybackCoordinator
import com.strmr.tv.app.AppSession
import com.strmr.tv.api.DetailsTarget
import com.strmr.tv.api.Episode
import com.strmr.tv.api.SourceAvailability
import com.strmr.tv.home.EpisodeSourceObserver
import com.strmr.tv.home.ServerEpisodeSourceGateway
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged

/** Only clipped-in-window, resumed rows advertise the items actually intersecting their viewport. */
@OptIn(FlowPreview::class)
@Composable
fun rememberEpisodeSourceHints(target: DetailsTarget, episodes: List<Episode>, list: LazyListState, inWindow: Boolean): Map<String, SourceAvailability> {
    val profile by AppSession.profile.collectAsState()
    val server by AppSession.serverUrl.collectAsState()
    val phase by AppSession.phase.collectAsState()
    val wakes by AppSession.wakes.collectAsState()
    val rechecks by SourceRecheckEvents.generation.collectAsState()
    val playerOpen by PlaybackCoordinator.playerVisibility.collectAsState()
    val resolving by PlaybackCoordinator.resolving.collectAsState()
    val uncovered = !playerOpen && resolving == null
    val view = LocalView.current
    var resumed by remember(view) { mutableStateOf(false) }
    var focused by remember(view) { mutableStateOf(view.hasWindowFocus()) }
    DisposableEffect(view) {
        val lifecycle = view.findViewTreeLifecycleOwner()?.lifecycle
        val observer = LifecycleEventObserver { _, _ -> resumed = lifecycle?.currentState?.isAtLeast(Lifecycle.State.RESUMED) == true }
        resumed = lifecycle?.currentState?.isAtLeast(Lifecycle.State.RESUMED) == true
        lifecycle?.addObserver(observer)
        val tree = view.viewTreeObserver
        val focus = ViewTreeObserver.OnWindowFocusChangeListener { focused = it }
        tree.addOnWindowFocusChangeListener(focus)
        onDispose { lifecycle?.removeObserver(observer); if (tree.isAlive) tree.removeOnWindowFocusChangeListener(focus) }
    }
    val observer = remember(target, list, profile?.id, server, phase) { EpisodeSourceObserver(ServerEpisodeSourceGateway(), target) }
    val results = remember(observer) { mutableStateOf<Map<String, SourceAvailability>>(emptyMap()) }
    LaunchedEffect(observer, episodes, inWindow, resumed, wakes, phase, uncovered, focused, rechecks) {
        if (!inWindow || !resumed || !focused || !uncovered || phase != AppSession.Phase.Ready) { results.value = emptyMap(); return@LaunchedEffect }
        snapshotFlow {
            val info = list.layoutInfo
            info.visibleItemsInfo.filter { it.offset < info.viewportEndOffset && it.offset + it.size > info.viewportStartOffset }
                .mapNotNull { episodes.getOrNull(it.index) }.take(12)
        }.distinctUntilChanged().debounce(150).collectLatest { visible ->
            observer.states(visible).collect { results.value = it }
        }
    }
    return if (inWindow && resumed && focused && uncovered && phase == AppSession.Phase.Ready) results.value else emptyMap()
}

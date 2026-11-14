package com.strmr.tv.ui

import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.findViewTreeLifecycleOwner
import android.view.ViewTreeObserver
import com.strmr.tv.api.SourceAvailability
import com.strmr.tv.app.AppSession
import com.strmr.tv.home.*
import com.strmr.tv.player.PlaybackCoordinator
import java.util.UUID
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob

private object VisibleTitleSources {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private var current: TitleSourceCoordinator? = null
    fun current(): TitleSourceCoordinator {
        val old = current
        if (old != null && old.isCurrent()) return old
        old?.close()
        return TitleSourceCoordinator(scope, ServerTitleSourceGateway()).also { current = it }
    }
}

data class VisibleTitleSource(val modifier: Modifier, val source: SourceAvailability?)

/** A composed/preloaded card is not demand until its clipped bounds intersect the window. */
@Composable
fun rememberVisibleTitleSource(card: HomeCard): VisibleTitleSource {
    val profile by AppSession.profile.collectAsState()
    val server by AppSession.serverUrl.collectAsState()
    val phase by AppSession.phase.collectAsState()
    val wakes by AppSession.wakes.collectAsState()
    val playerOpen by PlaybackCoordinator.playerVisibility.collectAsState()
    val resolving by PlaybackCoordinator.resolving.collectAsState()
    val coordinator = remember(profile?.id, server, phase, wakes) { VisibleTitleSources.current() }
    val hints by coordinator.hints.collectAsState()
    val title = card.titleSourceCoordinate()
    val owner = remember { UUID.randomUUID().toString() }
    val view = LocalView.current
    var inWindow by remember(card.id) { mutableStateOf(false) }
    var resumed by remember(view) { mutableStateOf(false) }
    var focused by remember(view) { mutableStateOf(view.hasWindowFocus()) }
    DisposableEffect(view) {
        val lifecycle = view.findViewTreeLifecycleOwner()?.lifecycle
        val observer = LifecycleEventObserver { _, _ -> resumed = lifecycle?.currentState?.isAtLeast(Lifecycle.State.RESUMED) == true }
        val focus = ViewTreeObserver.OnWindowFocusChangeListener { focused = it }
        resumed = lifecycle?.currentState?.isAtLeast(Lifecycle.State.RESUMED) == true
        lifecycle?.addObserver(observer)
        val tree = view.viewTreeObserver
        tree.addOnWindowFocusChangeListener(focus)
        onDispose { lifecycle?.removeObserver(observer); if (tree.isAlive) tree.removeOnWindowFocusChangeListener(focus) }
    }
    val active = titleSourceActive(inWindow, resumed, focused, playerOpen, resolving != null, phase == AppSession.Phase.Ready)
    DisposableEffect(coordinator, owner, title, active) {
        coordinator.setVisible(owner, title.takeIf { active })
        onDispose { coordinator.setVisible(owner, null) }
    }
    val source = title?.let { hints[titleSourceKey(it)] }
    return VisibleTitleSource(Modifier.onGloballyPositioned { val bounds = it.boundsInWindow(); inWindow = bounds.width > 0 && bounds.height > 0 }, source)
}

/** Menus read the shared evidence without creating a second visible demand. */
@Composable
fun rememberTitleSourceEvidence(card: HomeCard): SourceAvailability? {
    val hints by VisibleTitleSources.current().lastKnown.collectAsState()
    return card.titleSourceCoordinate()?.let { hints[titleSourceKey(it)] }
}

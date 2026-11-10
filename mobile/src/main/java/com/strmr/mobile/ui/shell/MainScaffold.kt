package com.strmr.mobile.ui.shell

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.NavigationRailItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import com.strmr.tv.api.ApiClient
import com.strmr.tv.app.AppSession
import com.strmr.tv.app.MenuTabs
import com.strmr.tv.home.HomeViewStore
import com.strmr.tv.launcher.DeepLink
import com.strmr.tv.launcher.DeepLinkRouter
import com.strmr.tv.library.CustomListsStore
import com.strmr.tv.library.ListsTabStore
import com.strmr.tv.library.WatchlistStore
import com.strmr.tv.ui.settings.BadgeSettings

/**
 * The signed-in app: a navigation bar (phones) or rail (tablets) over each section's own
 * back stack. Which sections show follows the profile's menu, as on the TV and iPhone apps.
 */
@Composable
fun MainScaffold() {
    val profile by AppSession.profile.collectAsState()
    remember(profile?.id) { HomeViewStore.activate(ApiClient.profileId) }
    val navigator = remember { Navigator() }
    val compact = LocalConfiguration.current.smallestScreenWidthDp < 600
    val sections = Section.visible(kids = profile?.isKidsProfile == true)
    val split = TabSplit.of(sections, compact)

    LaunchedEffect(Unit) { MenuTabs.refresh(ApiClient.profileId) }
    LaunchedEffect(Unit) { WatchlistStore.refresh() }
    LaunchedEffect(Unit) { ListsTabStore.refresh() }
    LaunchedEffect(Unit) { runCatching { CustomListsStore.refresh() } }
    LaunchedEffect(Unit) { runCatching { BadgeSettings.refresh() } }
    // A hidden section (menu changed) falls back to Home.
    LaunchedEffect(sections) {
        if (navigator.section != Section.MORE && navigator.section !in sections) navigator.select(Section.HOME)
    }
    val link by DeepLinkRouter.pending.collectAsState()
    LaunchedEffect(link) {
        val taken = DeepLinkRouter.take(profile?.id) ?: return@LaunchedEffect
        navigator.select(Section.HOME)
        navigator.openDetails(taken.target, autoplay = taken.action == DeepLink.Action.PLAY)
    }
    // Back pops the section's stack, then returns to Home, then leaves the app.
    BackHandler(enabled = navigator.current.isNotEmpty() || navigator.section != Section.HOME) {
        if (!navigator.pop()) navigator.select(Section.HOME)
    }

    CompositionLocalProvider(LocalNavigator provides navigator) {
        if (compact) {
            Scaffold(
                bottomBar = {
                    NavigationBar {
                        split.tabs.forEach { s -> SectionBarItem(s, navigator) }
                        if (split.more.isNotEmpty()) SectionBarItem(Section.MORE, navigator)
                    }
                },
                contentWindowInsets = androidx.compose.foundation.layout.WindowInsets(0),
            ) { padding ->
                Box(Modifier.fillMaxSize().padding(padding)) {
                    SectionContent(navigator, split.more)
                }
            }
        } else {
            Row(Modifier.fillMaxSize()) {
                NavigationRail {
                    sections.forEach { s ->
                        NavigationRailItem(
                            selected = navigator.section == s,
                            onClick = { navigator.select(s) },
                            icon = { Icon(if (navigator.section == s) s.selectedIcon else s.icon, null) },
                            label = { Text(s.title) },
                        )
                    }
                }
                Box(Modifier.weight(1f).fillMaxSize()) { SectionContent(navigator, emptyList()) }
            }
        }
    }
}

@Composable
private fun androidx.compose.foundation.layout.RowScope.SectionBarItem(s: Section, navigator: Navigator) {
    NavigationBarItem(
        selected = navigator.section == s,
        onClick = { navigator.select(s) },
        icon = { Icon(if (navigator.section == s) s.selectedIcon else s.icon, null) },
        label = { Text(s.title) },
    )
}

/** The section's top page (or its root), each kept in a saveable state slot so scroll survives. */
@Composable
private fun SectionContent(navigator: Navigator, more: List<Section>) {
    val holder = rememberSaveableStateHolder()
    val section = navigator.section
    val stack = navigator.current
    val depth = stack.size
    val key = "$section/$depth"
    AnimatedContent(
        targetState = Triple(section, depth, stack.lastOrNull()),
        transitionSpec = {
            val (fromSection, fromDepth, _) = initialState
            val (toSection, toDepth, _) = targetState
            when {
                fromSection != toSection -> fadeIn(tween(150)) togetherWith fadeOut(tween(150))
                toDepth > fromDepth -> slideInHorizontally(tween(260)) { it / 3 } + fadeIn(tween(260)) togetherWith fadeOut(tween(160))
                else -> fadeIn(tween(200)) togetherWith slideOutHorizontally(tween(240)) { it / 3 } + fadeOut(tween(200))
            }
        },
        label = "page",
    ) { (s, d, route) ->
        holder.SaveableStateProvider("$s/$d") {
            if (route == null) SectionRoot(s, more) else RouteScreen(route)
        }
    }
    // Pages popped off the stack forget their saved state.
    LaunchedEffect(key) {
        for (d in depth + 1..depth + 8) holder.removeState("$section/$d")
    }
}

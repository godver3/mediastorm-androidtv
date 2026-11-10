package com.strmr.mobile.ui.components

import androidx.compose.runtime.Composable
import com.strmr.mobile.ui.library.rememberLibraryOpener
import com.strmr.mobile.ui.shell.LocalNavigator
import com.strmr.mobile.ui.shell.Route
import com.strmr.tv.home.HomeAction
import com.strmr.tv.home.HomeCard
import com.strmr.tv.home.HomeLink
import com.strmr.tv.player.PlaybackCoordinator

/**
 * What tapping any card does, shared by every screen: details, a whole list, a person, a
 * favorite channel (tunes it), a library title, or a Watch Something action ([onAction]).
 */
@Composable
fun rememberCardOpener(onAction: (HomeAction) -> Unit = {}): (HomeCard) -> Unit {
    val navigator = LocalNavigator.current
    val openLibrary = rememberLibraryOpener()
    return { card ->
        when (val link = card.link) {
            is HomeLink.Details -> navigator.openDetails(link.target)
            is HomeLink.OpenList -> navigator.openList(link.list)
            is HomeLink.Person -> navigator.push(Route.Person(link.person))
            is HomeLink.Channel -> PlaybackCoordinator.playLive(link.channel, link.program?.title)
            is HomeLink.Library -> openLibrary(link.entry)
            is HomeLink.Action -> onAction(link.action)
        }
    }
}

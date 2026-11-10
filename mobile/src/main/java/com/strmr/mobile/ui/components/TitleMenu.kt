package com.strmr.mobile.ui.components

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import com.strmr.mobile.ui.details.WatchStateLoader
import com.strmr.mobile.ui.library.rememberLibraryOpener
import com.strmr.tv.api.DetailsTarget
import com.strmr.tv.home.HomeCard
import com.strmr.tv.home.HomeLink
import com.strmr.tv.library.CustomListsStore

/**
 * A title's long-press menu (Expo's long-press actions: watchlist, watched, add to list, hide,
 * set aside, …), shown as a bottom sheet over whichever screen asked. Any screen calls
 * [show]; [TitleMenuHost] in the shell draws it, and the sheets it opens. Main thread only.
 */
object TitleMenu {
    var card by mutableStateOf<HomeCard?>(null)
        private set
    /** The card came from Continue Watching: it offers Hide from Continue Watching and Set Aside. */
    var continueWatching by mutableStateOf(false)
        private set
    /** The series whose bulk watch-state sheet is up (from the menu's Watch State…). */
    var watchStateFor by mutableStateOf<DetailsTarget?>(null)

    fun show(card: HomeCard, continueWatching: Boolean = false) {
        if (card.target != null || card.link is HomeLink.Library) {
            this.card = card
            this.continueWatching = continueWatching
        }
    }

    fun dismiss() {
        card = null
        continueWatching = false
    }
}

/**
 * The shell's host for the title menu and the sheets any screen opens through shared state:
 * Manage Lists ([CustomListsStore.picking]) and a series' Watch State ([TitleMenu.watchStateFor]).
 * Actions run in this host's scope, which outlives the sheets.
 */
@Composable
fun TitleMenuHost() {
    val scope = rememberCoroutineScope()
    // Here, not in the sheet: a library title's copy choice and resume prompt outlive the sheet.
    val openLibrary = rememberLibraryOpener()
    TitleMenu.card?.let { card -> TitleMenuSheet(card, TitleMenu.continueWatching, scope, openLibrary, onDismiss = TitleMenu::dismiss) }
    CustomListsStore.picking?.let { target -> AddToListSheet(target, onDismiss = { CustomListsStore.picking = null }) }
    TitleMenu.watchStateFor?.let { target -> WatchStateLoader(target, onDismiss = { TitleMenu.watchStateFor = null }) }
}

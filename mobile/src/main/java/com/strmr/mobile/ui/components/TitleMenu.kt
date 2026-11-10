package com.strmr.mobile.ui.components

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.strmr.tv.home.HomeCard

/**
 * A title's long-press menu (Expo's long-press actions: watchlist, watched, add to list, hide,
 * set aside, …), shown as a bottom sheet over whichever screen asked. Any screen calls
 * [show]; [TitleMenuHost] in the shell draws it. Main thread only.
 */
object TitleMenu {
    var card by mutableStateOf<HomeCard?>(null)
        private set

    fun show(card: HomeCard) {
        if (card.target != null || card.link is com.strmr.tv.home.HomeLink.Library) this.card = card
    }

    fun dismiss() {
        card = null
    }
}

/** Placeholder until the menu sheet is built. */
@Composable
fun TitleMenuHost() {
    TitleMenu.card ?: return
    TitleMenu.dismiss()
}

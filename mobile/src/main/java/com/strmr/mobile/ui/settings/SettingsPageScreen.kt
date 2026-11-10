package com.strmr.mobile.ui.settings

import androidx.compose.runtime.Composable

/** The settings sub pages' keys, for [com.strmr.mobile.ui.shell.Route.SettingsPage]. */
object SettingsKeys {
    const val PLAYBACK = "playback"
    const val SUBTITLES = "subtitles"
    const val HOME = "home"
    const val CARDS = "cards"
    const val MENU = "menu"
    const val SERVER = "server"
    const val UPDATES = "updates"
    const val ABOUT = "about"
}

/** A settings sub page, by its key. */
@Composable
fun SettingsPageScreen(key: String, title: String) {
    when (key) {
        SettingsKeys.PLAYBACK -> PlaybackSettingsPage()
        SettingsKeys.SUBTITLES -> SubtitleSettingsPage()
        SettingsKeys.HOME -> HomeSettingsPage()
        SettingsKeys.CARDS -> CardSettingsPage()
        SettingsKeys.MENU -> MenuSettingsPage()
        SettingsKeys.SERVER -> ServerSettingsPage()
        SettingsKeys.UPDATES -> UpdateSettingsPage()
        SettingsKeys.ABOUT -> AboutSettingsPage()
        else -> SettingsPage(title) {}
    }
}

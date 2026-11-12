package com.strmr.mobile.ui.settings

import androidx.compose.runtime.Composable

/** The settings sub pages' keys, for [com.strmr.mobile.ui.shell.Route.SettingsPage]. */
object SettingsKeys {
    const val PLAYBACK = "playback"
    const val SUBTITLES = "subtitles"
    const val HOME = "home"
    const val CARDS = "cards"
    const val MENU = "menu"
    const val ROW_ORDER = "rowOrder"
    const val TAB_ORDER = "tabOrder"
    const val SERVER = "server"
    const val UPDATES = "updates"
    const val ABOUT = "about"
    const val CONNECTION = "connection"
    const val DOWNLOADS = "downloads"
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
        SettingsKeys.ROW_ORDER -> RowOrderPage()
        SettingsKeys.TAB_ORDER -> TabOrderPage()
        SettingsKeys.SERVER -> ServerSettingsPage()
        SettingsKeys.UPDATES -> UpdateSettingsPage()
        SettingsKeys.ABOUT -> AboutSettingsPage()
        SettingsKeys.CONNECTION -> com.strmr.mobile.ui.connection.ConnectionSettingsPage()
        SettingsKeys.DOWNLOADS -> com.strmr.mobile.ui.downloads.DownloadSettingsPage()
        else -> SettingsPage(title) {}
    }
}

package com.strmr.mobile.ui.shell

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.DownloadForOffline
import androidx.compose.material.icons.filled.EmojiEvents
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.LiveTv
import androidx.compose.material.icons.filled.MoreHoriz
import androidx.compose.material.icons.filled.People
import androidx.compose.material.icons.filled.RadioButtonChecked
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.ViewList
import androidx.compose.material.icons.outlined.BookmarkBorder
import androidx.compose.material.icons.outlined.CalendarMonth
import androidx.compose.material.icons.outlined.DownloadForOffline
import androidx.compose.material.icons.outlined.EmojiEvents
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.LiveTv
import androidx.compose.material.icons.outlined.MoreHoriz
import androidx.compose.material.icons.outlined.People
import androidx.compose.material.icons.outlined.RadioButtonChecked
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.outlined.ViewList
import androidx.compose.ui.graphics.vector.ImageVector
import com.strmr.tv.app.MenuTab
import com.strmr.tv.app.MenuTabs

/** A top-level section, in the Expo mobile app's tab order (as the iPhone app). */
enum class Section(val title: String, val icon: ImageVector, val selectedIcon: ImageVector, val menuTab: MenuTab?) {
    HOME("Home", Icons.Outlined.Home, Icons.Filled.Home, null),
    WATCHLIST("Watchlist", Icons.Outlined.BookmarkBorder, Icons.Filled.Bookmark, MenuTab.WATCHLIST),
    SEARCH("Search", Icons.Outlined.Search, Icons.Filled.Search, MenuTab.SEARCH),
    LISTS("Lists", Icons.Outlined.ViewList, Icons.Filled.ViewList, MenuTab.LISTS),
    CALENDAR("Calendar", Icons.Outlined.CalendarMonth, Icons.Filled.CalendarMonth, MenuTab.CALENDAR),
    RECORDINGS("Recordings", Icons.Outlined.RadioButtonChecked, Icons.Filled.RadioButtonChecked, MenuTab.RECORDINGS),
    LIVE("Live", Icons.Outlined.LiveTv, Icons.Filled.LiveTv, MenuTab.LIVE),
    SPORTS("Sports", Icons.Outlined.EmojiEvents, Icons.Filled.EmojiEvents, MenuTab.SPORTS),
    PROFILES("Profiles", Icons.Outlined.People, Icons.Filled.People, null),
    DOWNLOADS("Downloads", Icons.Outlined.DownloadForOffline, Icons.Filled.DownloadForOffline, null),
    SETTINGS("Settings", Icons.Outlined.Settings, Icons.Filled.Settings, null),
    /** Phones: the bar's last slot, listing the sections that don't fit. Never in [visible]. */
    MORE("More", Icons.Outlined.MoreHoriz, Icons.Filled.MoreHoriz, null);

    /** Its key in Settings › Menu › Tab Order ([MenuTabs.tabOrder]); a menu tab's own key for those that have one. */
    val orderKey: String get() = menuTab?.key ?: name.lowercase()

    companion object {
        /**
         * The sections the profile's menu shows, Home first and the rest in this device's Tab
         * Order (by default the Expo order); kids profiles get no Live TV (as Expo).
         */
        fun visible(kids: Boolean): List<Section> = MenuTabs.ordered(
            entries.filter { s ->
                if (s == MORE) return@filter false
                val tab = s.menuTab ?: return@filter true
                if (s == LIVE && kids) return@filter false
                MenuTabs.isVisible(tab)
            },
            Section::orderKey,
            first = { it == HOME },
        )

        /** The sections Tab Order can move, in their current order. */
        fun orderable(kids: Boolean): List<Section> = visible(kids).filter { it != HOME }
    }
}

/**
 * Phones: the navigation bar holds the first four sections and More (the rest, as a list).
 * Tablets: a navigation rail with every section.
 */
data class TabSplit(val tabs: List<Section>, val more: List<Section>) {
    companion object {
        const val PHONE_SLOTS = 4

        fun of(sections: List<Section>, compact: Boolean): TabSplit =
            if (!compact || sections.size <= PHONE_SLOTS + 1) TabSplit(sections, emptyList())
            else TabSplit(sections.take(PHONE_SLOTS), sections.drop(PHONE_SLOTS))
    }
}

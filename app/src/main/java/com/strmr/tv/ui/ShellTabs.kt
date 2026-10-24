package com.strmr.tv.ui

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.List
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.ui.graphics.vector.ImageVector
import com.strmr.tv.api.Profile
import com.strmr.tv.app.MenuTab
import com.strmr.tv.app.MenuTabs
import com.strmr.tv.home.HomeViewStore
import com.strmr.tv.home.HomeViews

internal enum class MainTab(val title: String, val icon: ImageVector, val menu: MenuTab? = null) {
    HOME("Home", Icons.Outlined.Home),
    LIVE("Live", AppIcons.LiveTv, MenuTab.LIVE),
    RECORDINGS("Recordings", AppIcons.Recordings, MenuTab.RECORDINGS),
    SPORTS("Sports", AppIcons.Sports, MenuTab.SPORTS),
    WATCHLIST("Watchlist", AppIcons.BookmarkBorder, MenuTab.WATCHLIST),
    LISTS("Lists", Icons.AutoMirrored.Outlined.List, MenuTab.LISTS),
    CALENDAR("Calendar", AppIcons.Event, MenuTab.CALENDAR),
    SEARCH("Search", Icons.Outlined.Search),
    SETTINGS("Settings", Icons.Outlined.Settings),
}

/** An entry of the menu (top bar or side menu): a page, or a Home view shown as its own entry (the Home page in that view). */
internal data class ShellTab(val key: String, val title: String, val icon: ImageVector, val page: MainTab, val view: String? = null)

/** The menu's entries, the same in both menu styles. */
internal object ShellTabs {
    /** Visible entries in order: Home, the views with their own tab, then the pages. Kids profiles don't get Live TV (as in Expo). */
    fun build(profile: Profile?, viewTabs: List<String>): List<ShellTab> = MainTab.entries.filter { tab ->
        val menu = tab.menu ?: return@filter true
        MenuTabs.isVisible(menu) && !(menu == MenuTab.LIVE && profile?.isKidsProfile == true)
    }.flatMap { tab ->
        val own = ShellTab(tab.name, if (tab == MainTab.HOME) HomeViewTabs.homeTitle(viewTabs) else tab.title, tab.icon, tab)
        if (tab != MainTab.HOME) listOf(own)
        else listOf(own) + viewTabs.map { ShellTab("view:$it", HomeViewStore.label(it), HomeViewTabs.icon(it), MainTab.HOME, it) }
    }

    /** The entry for [key]; a view tab turned off shows its page on the Home tab. */
    fun indexOf(tabs: List<ShellTab>, key: String): Int = tabs.indexOfFirst { it.key == key }.takeIf { it >= 0 }
        ?: tabs.indexOfFirst { it.page.name == key || (key.startsWith("view:") && it.page == MainTab.HOME) }.coerceAtLeast(0)

    /**
     * Shows [tab]'s Home view: a view's own tab shows Home in it; the Home tab its own view (one
     * without a tab), else All. A view tab leaves Home's remembered view as it is.
     */
    fun showView(tab: ShellTab, viewTabs: List<String>) {
        val homeView = HomeViewStore.home
        if (tab.view != null) HomeViewStore.show(tab.view)
        else if (tab.page == MainTab.HOME) HomeViewStore.select(if (homeView in viewTabs) HomeViews.ALL else homeView)
    }
}

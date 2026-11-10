package com.strmr.mobile.ui.settings

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.strmr.tv.app.MenuTab
import com.strmr.tv.app.MenuTabs
import com.strmr.tv.home.CarouselPick
import com.strmr.tv.home.HomeLayoutStore
import com.strmr.tv.home.ShelfLayoutInfo
import com.strmr.tv.home.ShelfStyle
import com.strmr.tv.ui.settings.BadgeSettings
import com.strmr.tv.ui.settings.SpoilerSettings

/**
 * Settings › Home Layout on the phone (as the iPhone's): the Top Carousel and its row, then
 * each Home row's card style or Hidden, saved on this device in [HomeLayoutStore]. The TV's
 * focus, hero and card size choices have no phone Home to apply to.
 */
@Composable
fun HomeSettingsPage() {
    val s = HomeLayoutStore
    SettingsPage("Home Layout") {
        SettingsSection("Top of Home", "A carousel of one row's titles at the top of Home; that row isn't repeated below.") {
            SwitchRow("Top Carousel", s.topCarousel) { s.chooseTopCarousel(it) }
            val rows = s.shelves
            if (s.topCarousel && rows.isNotEmpty()) {
                val chosen = rows.indexOfFirst { it.id == s.carouselRow }.takeIf { it >= 0 }
                OverrideRow("Carousel Row", rows.map { it.title }, chosen, defaultCarouselRow(rows)) { i ->
                    s.chooseCarouselRow(i?.let { rows[it].id })
                }
            }
        }
        SettingsSection("Rows", "Hidden rows are left off Home on this device.") {
            if (s.shelves.isEmpty()) {
                Text(
                    "Open Home to list its rows.", color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(16.dp),
                )
            }
            s.shelves.forEach { shelf -> ShelfRow(shelf) }
        }
        if (s.shelfStyles.isNotEmpty() || s.hiddenShelves.isNotEmpty()) {
            SettingsSection {
                SettingsRow("Reset All Rows to Default") {
                    s.shelves.forEach { s.setHidden(false, it.id); s.setStyle(null, it.id) }
                }
            }
        }
    }
}

/**
 * One row's setting: Default (the style it gets unless set), Poster, Landscape or Hidden. The
 * phone draws the TV's expanding posters as posters, so they read as Poster.
 */
@Composable
private fun ShelfRow(shelf: ShelfLayoutInfo) {
    val s = HomeLayoutStore
    val choices = listOf("Poster", "Landscape", "Hidden")
    val selected = when {
        s.isHidden(shelf.id) -> 2
        else -> s.shelfStyles[shelf.id]?.let { if (it == ShelfStyle.LANDSCAPE) 1 else 0 }
    }
    val default = if (s.defaultStyle(shelf.id) == ShelfStyle.LANDSCAPE) "Landscape" else "Poster"
    OverrideRow(shelf.title, choices, selected, default) { i ->
        s.setHidden(i == 2, shelf.id)
        when (i) {
            0 -> s.setStyle(ShelfStyle.POSTER, shelf.id)
            1 -> s.setStyle(ShelfStyle.LANDSCAPE, shelf.id)
            2 -> Unit
            else -> s.setStyle(null, shelf.id)
        }
    }
}

/** What Default shows: Top 10, else Trending, else the first row. */
private fun defaultCarouselRow(rows: List<ShelfLayoutInfo>): String =
    (rows.firstOrNull { it.id == CarouselPick.TOP_TEN } ?: rows.firstOrNull { it.id.startsWith("trending") } ?: rows.firstOrNull())?.title ?: "Top 10"

/** Settings › Card Badges and Spoilers: each Default (the server's value) or this device's own. */
@Composable
fun CardSettingsPage() {
    SettingsPage("Cards & Spoilers") {
        SettingsSection("Card Badges", "Progress bars always show.") {
            BadgeSettings.Option.entries.forEach { option ->
                OverrideRow(
                    option.title, listOf("Off", "On"), BadgeSettings.override(option)?.let { if (it) 1 else 0 },
                    onOff(BadgeSettings.serverValue(option)), option.detail,
                ) { i -> BadgeSettings.set(option, i?.let { it == 1 }) }
            }
        }
        SettingsSection("Spoilers", "Blurs the artwork and hides the descriptions of episodes you haven't reached.") {
            SpoilerSettings.Option.entries.forEach { option ->
                OverrideRow(
                    option.title, listOf("Off", "On"), SpoilerSettings.override(option)?.let { if (it) 1 else 0 },
                    SpoilerSettings.serverValue(option)?.let(::onOff), option.detail,
                ) { i -> SpoilerSettings.set(option, i?.let { it == 1 }) }
            }
        }
    }
}

/**
 * Settings › Menu: which sections the navigation bar (and More) shows on this device, each
 * starting on the profile's menu ("Default"), as the TV's Settings › Menu entries.
 */
@Composable
fun MenuSettingsPage() {
    SettingsPage("Menu") {
        SettingsSection(
            footer = "Home, Profiles, Downloads and Settings always show; Live never shows on kids profiles. On phones the first four sections are in the navigation bar and the rest under More.",
        ) {
            MenuTab.entries.forEach { tab ->
                val override = MenuTabs.overrides[tab.key]
                OverrideRow(
                    tab.label, listOf("Shown", "Hidden"), override?.let { if (it) 0 else 1 },
                    if (MenuTabs.profileShows(tab)) "Shown" else "Hidden",
                ) { i -> MenuTabs.setOverride(tab, i?.let { it == 0 }) }
            }
        }
        if (MenuTabs.overrides.isNotEmpty()) {
            SettingsSection { SettingsRow("Reset All to Default") { MenuTab.entries.forEach { MenuTabs.setOverride(it, null) } } }
        }
    }
}

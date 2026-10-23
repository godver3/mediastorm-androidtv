package com.strmr.tv.ui.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.tv.material3.ListItem
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import com.strmr.tv.app.MenuStyle
import com.strmr.tv.app.MenuTab
import com.strmr.tv.app.MenuTabs
import com.strmr.tv.home.HomeViewStore
import com.strmr.tv.ui.pageBackground

/**
 * Settings › Menu: the menu style (top bar or side menu, device only; the side menu's rail can
 * hide while closed), then each hideable entry, which follows the profile ("Default") or is on
 * or off on this device. The Home views (Movies, Shows, custom pages) can also show as their
 * own entries, off by default. Both styles show the same entries.
 */
@Composable
fun TopMenuSettings() {
    val first = remember { FocusRequester() }
    LaunchedEffect(Unit) { runCatching { first.requestFocus() } }
    Box(Modifier.fillMaxSize().pageBackground(), contentAlignment = Alignment.TopCenter) {
        LazyColumn(
            Modifier.widthIn(max = 640.dp),
            contentPadding = PaddingValues(horizontal = 24.dp, vertical = 36.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            item { Text("Menu", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.SemiBold) }
            item(key = "style") {
                ListItem(
                    selected = false,
                    onClick = { MenuStyle.chooseSide(!MenuStyle.side) },
                    headlineContent = { Text("Menu Style") },
                    supportingContent = { Text(if (MenuStyle.side) "Side Menu" else "Top Bar") },
                    modifier = Modifier.focusRequester(first),
                )
            }
            if (MenuStyle.side) item(key = "rail") { RailChoice() }
            item {
                Text("Entries", style = MaterialTheme.typography.titleSmall, color = Color.White.copy(alpha = 0.6f),
                     modifier = Modifier.padding(top = 14.dp, start = 4.dp))
            }
            items(MenuTab.entries, key = { it.key }) { tab -> TabChoice(tab.key, tab.label) }
            val views = HomeViewStore.pageIds
            if (views.isNotEmpty()) {
                item {
                    Text("Home Views", style = MaterialTheme.typography.titleSmall, color = Color.White.copy(alpha = 0.6f),
                         modifier = Modifier.padding(top = 14.dp, start = 4.dp))
                }
                items(views, key = { "view:$it" }) { view -> TabChoice(view, HomeViewStore.label(view)) }
            }
        }
    }
}

/** "Show Rail When Closed": the profile's choice (Expo's `hideTvDrawerRail`), or on or off here. Default → Shown → Hidden. */
@Composable
private fun RailChoice() {
    val override = MenuStyle.railOverride
    val value = when (override) {
        null -> "Default (${if (MenuStyle.profileHidesRail) "Hidden" else "Shown"})"
        true -> "Shown"
        false -> "Hidden"
    }
    ListItem(
        selected = false,
        onClick = { MenuStyle.chooseRail(when (override) { null -> true; true -> false; false -> null }) },
        headlineContent = { Text("Show Rail When Closed") },
        supportingContent = { Text(value) },
    )
}

/** One tab's choice. Select steps Default → Shown → Hidden → Default. */
@Composable
private fun TabChoice(key: String, label: String, modifier: Modifier = Modifier) {
    val override = MenuTabs.overrides[key]
    val value = when (override) {
        null -> "Default (${if (MenuTabs.profileShows(key)) "Shown" else "Hidden"})"
        true -> "Shown"
        false -> "Hidden"
    }
    ListItem(
        selected = false,
        onClick = { MenuTabs.setOverride(key, when (override) { null -> true; true -> false; false -> null }) },
        headlineContent = { Text(label) },
        supportingContent = { Text(value) },
        modifier = modifier,
    )
}

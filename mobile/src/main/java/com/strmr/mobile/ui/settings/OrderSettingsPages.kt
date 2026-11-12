package com.strmr.mobile.ui.settings

import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.strmr.mobile.ui.shell.Section
import com.strmr.tv.app.AppSession
import com.strmr.tv.app.DeviceOrder
import com.strmr.tv.app.MenuTabs
import com.strmr.tv.home.HomeLayoutStore

/** Settings › Home Layout › Row Order: Home's rows in this device's order (hidden rows too, marked). */
@Composable
fun RowOrderPage() {
    val s = HomeLayoutStore
    val rows = s.ordered(s.shelves) { it.id }
    ReorderPage(
        title = "Row Order",
        footer = "Saved on this device. Reset follows the profile's order.",
        empty = "Open Home to list its rows.",
        items = rows.map { Triple(it.id, it.title, if (s.isHidden(it.id)) "Hidden" else null) },
        canReset = s.shelfOrder.isNotEmpty(),
        onReset = { s.chooseShelfOrder(emptyList()) },
        onMove = { ids -> s.chooseShelfOrder(DeviceOrder.save(s.shelfOrder, ids)) },
    )
}

/** Settings › Menu › Tab Order: the sections after Home, in this device's order. */
@Composable
fun TabOrderPage() {
    val profile by AppSession.profile.collectAsState()
    val sections = Section.orderable(kids = profile?.isKidsProfile == true)
    ReorderPage(
        title = "Tab Order",
        footer = "Home stays first. On phones the first four sections are in the navigation bar and the rest under More. Hidden sections keep their place for when they're shown.",
        empty = null,
        items = sections.map { Triple(it.orderKey, it.title, null) },
        canReset = MenuTabs.tabOrder.isNotEmpty(),
        onReset = { MenuTabs.chooseTabOrder(emptyList()) },
        onMove = { keys -> MenuTabs.chooseTabOrder(DeviceOrder.save(MenuTabs.tabOrder, keys)) },
    )
}

/** A list reordered with each row's up and down buttons; [onMove] gets the ids in their new order. */
@Composable
private fun ReorderPage(
    title: String,
    footer: String,
    empty: String?,
    items: List<Triple<String, String, String?>>,
    canReset: Boolean,
    onReset: () -> Unit,
    onMove: (List<String>) -> Unit,
) {
    val ids = items.map { it.first }
    SettingsPage(title) {
        SettingsSection(footer = footer) {
            if (items.isEmpty() && empty != null) {
                Text(empty, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(16.dp))
            }
            items.forEachIndexed { index, (_, name, note) ->
                if (index > 0) RowDivider()
                SettingsRow(name, supporting = note, trailing = {
                    Row {
                        IconButton(onClick = { onMove(DeviceOrder.move(ids, index, index - 1)) }, enabled = index > 0) {
                            Icon(Icons.Filled.KeyboardArrowUp, "Move $name up")
                        }
                        IconButton(onClick = { onMove(DeviceOrder.move(ids, index, index + 1)) }, enabled = index < items.lastIndex) {
                            Icon(Icons.Filled.KeyboardArrowDown, "Move $name down")
                        }
                    }
                })
            }
        }
        if (canReset) SettingsSection { SettingsRow("Reset to Default", onClick = onReset) }
    }
}

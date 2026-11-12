package com.strmr.tv.ui.settings

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.tv.material3.ListItem
import androidx.tv.material3.LocalContentColor
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import com.strmr.tv.app.AppSession
import com.strmr.tv.app.DeviceOrder
import com.strmr.tv.app.MenuTabs
import com.strmr.tv.home.HomeLayoutStore
import com.strmr.tv.ui.HomeViewTabs
import com.strmr.tv.ui.ShellTabs
import com.strmr.tv.ui.pageBackground

/** Settings › Home Layout › Row Order: Home's rows in this device's order (hidden rows too, marked). */
@Composable
fun RowOrderSettings() {
    val s = HomeLayoutStore
    val rows = s.ordered(s.shelves) { it.id }
    ReorderPage(
        title = "Row Order",
        footer = if (rows.isEmpty()) "Open Home to list its rows." else
            "Select a row to pick it up, then select the row whose place it takes. Back puts it down unmoved. Saved on this device; Reset follows the profile's order.",
        items = rows.map { OrderItem(it.id, it.title, if (s.isHidden(it.id)) "Hidden" else null) },
        canReset = s.shelfOrder.isNotEmpty(),
        onReset = { s.chooseShelfOrder(emptyList()) },
        onMove = { ids -> s.chooseShelfOrder(DeviceOrder.save(s.shelfOrder, ids)) },
    )
}

/** Settings › Menu › Tab Order: the menu's entries between Home and Settings, in this device's order. */
@Composable
fun TabOrderSettings() {
    val tabs = ShellTabs.orderable(AppSession.profile.value, HomeViewTabs.shown())
    ReorderPage(
        title = "Tab Order",
        footer = "Select an entry to pick it up, then select the entry whose place it takes. Back puts it down unmoved. Home stays first and Settings last; hidden entries keep their place for when they're shown.",
        items = tabs.map { OrderItem(it.orderKey, it.title) },
        canReset = MenuTabs.tabOrder.isNotEmpty(),
        onReset = { MenuTabs.chooseTabOrder(emptyList()) },
        onMove = { keys -> MenuTabs.chooseTabOrder(DeviceOrder.save(MenuTabs.tabOrder, keys)) },
    )
}

private data class OrderItem(val id: String, val title: String, val note: String? = null)

/**
 * A list the remote reorders: Select picks an item up (marked "Moving"), Select on another
 * item puts it in that item's place, and Back while one is up cancels. [onMove] gets the ids in
 * their new order.
 */
@Composable
private fun ReorderPage(
    title: String,
    footer: String,
    items: List<OrderItem>,
    canReset: Boolean,
    onReset: () -> Unit,
    onMove: (List<String>) -> Unit,
) {
    var moving by remember { mutableStateOf<String?>(null) }
    val first = remember { FocusRequester() }
    LaunchedEffect(Unit) { runCatching { first.requestFocus() } }
    // Back with an item up puts it down instead of closing the page.
    BackHandler(enabled = moving != null) { moving = null }
    Box(Modifier.fillMaxSize().pageBackground(), contentAlignment = Alignment.TopCenter) {
        LazyColumn(
            Modifier.widthIn(max = 640.dp),
            contentPadding = PaddingValues(horizontal = 24.dp, vertical = 36.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            item { Text(title, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.SemiBold) }
            item {
                Text(footer, style = MaterialTheme.typography.bodySmall, color = Color.White.copy(alpha = 0.6f),
                     modifier = Modifier.padding(start = 4.dp, bottom = 8.dp))
            }
            itemsIndexed(items, key = { _, item -> item.id }) { index, item ->
                val picked = moving == item.id
                ListItem(
                    selected = picked,
                    onClick = {
                        val from = items.indexOfFirst { it.id == moving }
                        if (from < 0) moving = item.id
                        else {
                            moving = null
                            if (from != index) onMove(DeviceOrder.move(items.map { it.id }, from, index))
                        }
                    },
                    headlineContent = { Text(item.title) },
                    supportingContent = when {
                        picked -> { { Text("Moving — select its new place") } }
                        moving != null -> { { Text(listOfNotNull("Move here", item.note).joinToString(" · ")) } }
                        item.note != null -> { { Text(item.note) } }
                        else -> null
                    },
                    leadingContent = { Text("${index + 1}", color = LocalContentColor.current.copy(alpha = 0.6f)) },
                    modifier = if (index == 0) Modifier.focusRequester(first) else Modifier,
                )
            }
            if (canReset) item(key = "reset") {
                ListItem(
                    selected = false,
                    onClick = { moving = null; onReset() },
                    headlineContent = { Text("Reset to Default") },
                    modifier = Modifier.padding(top = 14.dp),
                )
            }
        }
    }
}

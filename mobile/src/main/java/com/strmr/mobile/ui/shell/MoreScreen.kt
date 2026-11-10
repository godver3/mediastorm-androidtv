package com.strmr.mobile.ui.shell

import androidx.compose.foundation.clickable
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier

/** Phones: the sections that don't fit the navigation bar, each opening as a page. */
@Composable
fun MoreScreen(sections: List<Section>) {
    val navigator = LocalNavigator.current
    PageScaffold("More", showBack = false) { padding ->
        LazyColumn(contentPadding = padding) {
            items(sections, key = { it.name }) { s ->
                ListItem(
                    headlineContent = { Text(s.title) },
                    leadingContent = { Icon(s.icon, null) },
                    trailingContent = { Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, null) },
                    modifier = Modifier.clickable { navigator.push(Route.OpenSection(s)) },
                )
            }
        }
    }
}

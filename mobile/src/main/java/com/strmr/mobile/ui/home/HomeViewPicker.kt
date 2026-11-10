package com.strmr.mobile.ui.home

import androidx.compose.foundation.layout.Box
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.outlined.Dashboard
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.Movie
import androidx.compose.material.icons.outlined.Tv
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.vector.ImageVector
import com.strmr.tv.home.HomeViewStore
import com.strmr.tv.home.HomeViews

/**
 * The top bar's Home view menu (the Expo and iPhone apps' view picker): Home, Movies, Shows and
 * the profile's custom pages, the one on show checked. The choice is remembered per profile.
 */
@Composable
fun HomeViewPicker() {
    val ids = HomeViewStore.ids
    if (ids.size < 2) return
    var open by remember { mutableStateOf(false) }
    val current = HomeViewStore.current
    Box {
        IconButton(onClick = { open = true }) { Icon(viewIcon(current), contentDescription = "Home View") }
        DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            ids.forEach { view ->
                DropdownMenuItem(
                    text = { Text(HomeViewStore.label(view)) },
                    leadingIcon = { Icon(viewIcon(view), contentDescription = null) },
                    trailingIcon = if (view == current) { { Icon(Icons.Filled.Check, contentDescription = "Selected") } } else null,
                    onClick = {
                        open = false
                        HomeViewStore.select(view)
                    },
                )
            }
        }
    }
}

private fun viewIcon(view: String): ImageVector = when (view) {
    HomeViews.ALL -> Icons.Outlined.Home
    HomeViews.MOVIES -> Icons.Outlined.Movie
    HomeViews.SHOWS -> Icons.Outlined.Tv
    else -> Icons.Outlined.Dashboard
}

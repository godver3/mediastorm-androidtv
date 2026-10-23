package com.strmr.tv.ui

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.FavoriteBorder
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.Star
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.tv.material3.ClickableSurfaceDefaults
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Surface
import androidx.tv.material3.Text
import androidx.compose.ui.graphics.vector.ImageVector
import com.strmr.tv.app.MenuTabs
import com.strmr.tv.home.HomeViewStore
import com.strmr.tv.home.HomeViews

/**
 * The Home tab and the Home views shown as their own tabs. The Home tab carries its own
 * view's name ("Movies"; "Home" for All) and shows only views without their own tab; the view
 * switcher pill ([HomeViewSwitcher], a device setting) opens the picker over those.
 */
internal object HomeViewTabs {
    /** The views the profile or this device shows as their own tabs, in picker order. */
    fun shown(): List<String> = HomeViewStore.pageIds.filter { MenuTabs.isVisible(it) }

    /** The views without their own tab, which the Home tab can show besides All. */
    fun untabbed(viewTabs: List<String>): List<String> = HomeViewStore.pageIds.filter { it !in viewTabs }

    /** The Home tab's name, from Home's own view (kept while a view tab is open). */
    fun homeTitle(viewTabs: List<String>): String {
        val view = HomeViewStore.home
        return if (view == HomeViews.ALL || view in viewTabs) "Home" else HomeViewStore.label(view)
    }

    /** The view's icon (`homeViewIcon`), for its tab. */
    fun icon(view: String): ImageVector = when (HomeViewStore.views[view]?.icon) {
        "movie-open" -> AppIcons.Film
        "television-classic" -> AppIcons.Tv
        "star" -> Icons.Outlined.Star
        "heart" -> Icons.Outlined.FavoriteBorder
        "home-variant" -> Icons.Outlined.Home
        else -> when (view) {
            HomeViews.MOVIES -> AppIcons.Film
            HomeViews.SHOWS -> AppIcons.Tv
            else -> Icons.Outlined.Home
        }
    }
}

/** The view switcher pill under the tab bar: the current view ("All ▾", "Movies ▾"); Select opens the picker. */
@Composable
internal fun HomeViewSwitcher(onClick: () -> Unit, modifier: Modifier = Modifier) {
    val view = HomeViewStore.current
    Surface(
        onClick = onClick,
        modifier = modifier,
        shape = ClickableSurfaceDefaults.shape(RoundedCornerShape(50)),
        // The tab bar's glass pill, white when focused as a focused tab is.
        colors = ClickableSurfaceDefaults.colors(
            containerColor = Color.Black.copy(alpha = 0.45f),
            contentColor = Color.White.copy(alpha = 0.85f),
            focusedContainerColor = Color.White,
            focusedContentColor = Color.Black,
        ),
        scale = ClickableSurfaceDefaults.scale(focusedScale = 1f),
    ) {
        Text(
            "${if (view == HomeViews.ALL) "All" else HomeViewStore.label(view)}  ▾",
            style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.SemiBold,
            modifier = Modifier.padding(horizontal = 18.dp, vertical = 7.dp),
        )
    }
}

/** "Home View": All, then [views] (those without their own tab); the current one checked and focused. */
@Composable
internal fun HomeViewPicker(views: List<String>, onPick: (String) -> Unit, onDismiss: () -> Unit) {
    val ids = listOf(HomeViews.ALL) + views
    PickerDialog(
        title = "Home View",
        options = ids.map { if (it == HomeViews.ALL) "All" else HomeViewStore.label(it) },
        selected = ids.indexOf(HomeViewStore.current),
        onPick = { onPick(ids[it]) },
        onDismiss = onDismiss,
    )
}

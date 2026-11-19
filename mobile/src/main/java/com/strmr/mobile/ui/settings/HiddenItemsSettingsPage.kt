package com.strmr.mobile.ui.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.key
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.strmr.tv.api.Artwork
import com.strmr.tv.library.HiddenItem
import com.strmr.tv.library.HiddenItemsSettings
import com.strmr.tv.library.HiddenItemsSettings.Load
import com.strmr.tv.library.HiddenItemsSettings.Section
import kotlinx.coroutines.launch

/**
 * Settings › Hidden Items: titles hidden from Continue Watching, then titles hidden from this
 * profile, each with its undo. A row leaves as soon as its undo succeeds; a failure shows the
 * app's toast and keeps it.
 */
@Composable
fun HiddenItemsSettingsPage() {
    val model = remember { HiddenItemsSettings() }
    val scope = rememberCoroutineScope()
    LaunchedEffect(Unit) { model.load() }

    SettingsPage("Hidden Items") {
        HiddenSection(
            "Continue Watching", "Shown again in Continue Watching if you're still watching them.",
            model.continueLoad, model.continueItems, Section.ContinueWatching, model.busy,
            empty = "Nothing hidden from Continue Watching", action = "Show",
            onRetry = { scope.launch { model.loadContinueWatching() } },
        ) { scope.launch { model.showInContinueWatching(it) } }
        HiddenSection(
            "Hidden from Profile", "Unhidden titles show again across the app.",
            model.profileLoad, model.profileItems, Section.Profile, model.busy,
            empty = "Nothing hidden from this profile", action = "Unhide",
            onRetry = { scope.launch { model.loadProfile() } },
        ) { scope.launch { model.unhide(it) } }
    }
}

@Suppress("LongParameterList")
@Composable
private fun HiddenSection(
    header: String,
    footer: String,
    load: Load,
    items: List<HiddenItem>,
    section: Section,
    busy: Set<String>,
    empty: String,
    action: String,
    onRetry: () -> Unit,
    onUndo: (HiddenItem) -> Unit,
) {
    SettingsSection(header, footer.takeIf { items.isNotEmpty() }) {
        when {
            load == Load.Loading && items.isEmpty() -> Box(Modifier.fillMaxWidth().padding(20.dp), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(Modifier.size(28.dp))
            }
            load is Load.Failed && items.isEmpty() ->
                SettingsRow("Couldn't load", load.message, trailing = { TextButton(onClick = onRetry) { Text("Retry") } })
            items.isEmpty() -> SettingsRow(empty)
            else -> items.forEachIndexed { index, item ->
                val rowKey = HiddenItemsSettings.key(section, item)
                key(rowKey) {
                    if (index > 0) RowDivider()
                    HiddenRow(item, action, working = rowKey in busy) { onUndo(item) }
                }
            }
        }
    }
}

@Composable
private fun HiddenRow(item: HiddenItem, action: String, working: Boolean, onUndo: () -> Unit) {
    ListItem(
        headlineContent = { Text(item.displayName, maxLines = 2, overflow = TextOverflow.Ellipsis) },
        supportingContent = { Text(listOfNotNull(item.year?.toString(), if (item.isMovie) "Movie" else "Show").joinToString(" · ")) },
        leadingContent = {
            AsyncImage(
                model = Artwork.url(item.posterUrl, "w185"), contentDescription = null, contentScale = ContentScale.Crop,
                modifier = Modifier.size(44.dp, 66.dp).clip(RoundedCornerShape(6.dp))
                    .background(MaterialTheme.colorScheme.surfaceContainerHighest),
            )
        },
        trailingContent = {
            if (working) CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp)
            else TextButton(onClick = onUndo) { Text(action) }
        },
        colors = ListItemDefaults.colors(containerColor = Color.Transparent),
    )
}

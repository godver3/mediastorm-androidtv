package com.strmr.mobile.ui.downloads

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PlayCircle
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Download
import androidx.compose.material.icons.outlined.ErrorOutline
import androidx.compose.material.icons.outlined.Movie
import androidx.compose.material.icons.outlined.PauseCircle
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material.icons.outlined.Schedule
import androidx.compose.material.icons.outlined.Tv
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SwipeToDismissBox
import androidx.compose.material3.SwipeToDismissBoxValue
import androidx.compose.material3.Text
import androidx.compose.material3.rememberSwipeToDismissBoxState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.strmr.mobile.downloads.DownloadFormat
import com.strmr.mobile.downloads.DownloadItem
import com.strmr.mobile.downloads.DownloadItem.Status
import com.strmr.mobile.downloads.DownloadStorage
import com.strmr.mobile.downloads.Downloads
import com.strmr.tv.api.Artwork

/**
 * One download in the list (the iPhone app's `MobileDownloadRow`): the poster, the title (an
 * episode's name under its show), then progress with bytes, speed and time left while it
 * runs, its size once done, or why it waits or failed. The trailing icon says what a tap does.
 * Swipe left to delete; long press for its menu.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DownloadRow(
    item: DownloadItem,
    prompts: DownloadPrompts,
    showsSeries: Boolean = true,
    selection: Set<String>? = null,
    onToggle: (String) -> Unit = {},
) {
    var menu by remember { mutableStateOf(false) }
    val swipe = rememberSwipeToDismissBoxState(confirmValueChange = {
        if (it == SwipeToDismissBoxValue.EndToStart) prompts.confirmDelete(item)
        false
    })
    SwipeToDismissBox(
        state = swipe,
        enableDismissFromStartToEnd = false,
        gesturesEnabled = selection == null,
        backgroundContent = { DeleteBackground() },
    ) {
        Box {
            DownloadListItem(
                item, showsSeries, checking = prompts.checkingResume == item.id,
                selected = selection?.contains(item.id),
                onClick = { if (selection != null) onToggle(item.id) else prompts.primary(item) },
                onLongClick = { if (selection == null) menu = true },
            )
            DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                DownloadMenuItems(item, prompts) { menu = false }
            }
        }
    }
}

/** A row's menu: what a tap does, then the release and Delete / Cancel. */
@Composable
fun DownloadMenuItems(item: DownloadItem, prompts: DownloadPrompts, close: () -> Unit) {
    @Composable
    fun entry(label: String, icon: ImageVector, action: () -> Unit) =
        DropdownMenuItem(text = { Text(label) }, leadingIcon = { Icon(icon, null) }, onClick = { close(); action() })
    when (item.status) {
        Status.COMPLETED -> entry("Play", Icons.Filled.PlayCircle) { prompts.play(item) }
        Status.DOWNLOADING, Status.PENDING -> entry("Pause", Icons.Outlined.PauseCircle) { Downloads.pause(item.id) }
        Status.PAUSED -> entry("Resume", Icons.Outlined.Download) { Downloads.resume(item.id) }
        Status.FAILED -> entry("Retry", Icons.Outlined.Refresh) { Downloads.resume(item.id) }
    }
    item.releaseName?.let {
        DropdownMenuItem(text = { Text(it, style = MaterialTheme.typography.bodySmall, maxLines = 2) }, enabled = false, onClick = {})
    }
    entry(if (item.isActive) "Cancel Download" else "Delete Download", Icons.Outlined.Delete) { prompts.confirmDelete(item) }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun DownloadListItem(
    item: DownloadItem, showsSeries: Boolean, checking: Boolean, selected: Boolean?, onClick: () -> Unit, onLongClick: () -> Unit,
) {
    val title = if (item.isEpisode && !showsSeries) item.rowTitle else item.name
    val subtitle = when {
        item.isMovie -> item.year?.toString()
        !showsSeries -> item.episodeCode
        else -> listOfNotNull(item.episodeCode, item.episodeName?.ifEmpty { null }).joinToString(" · ")
    }
    ListItem(
        modifier = Modifier.combinedClickable(onClick = onClick, onLongClick = onLongClick),
        leadingContent = {
            if (selected != null) Checkbox(checked = selected, onCheckedChange = { onClick() }) else DownloadPoster(item)
        },
        headlineContent = { Text(title, maxLines = 2, overflow = TextOverflow.Ellipsis) },
        supportingContent = {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                subtitle?.takeIf { it.isNotEmpty() }?.let { Text(it, maxLines = 1, overflow = TextOverflow.Ellipsis) }
                StatusLine(item)
            }
        },
        trailingContent = {
            if (checking) CircularProgressIndicator(Modifier.size(24.dp), strokeWidth = 2.dp)
            else if (selected == null) Icon(symbol(item), null, tint = symbolTint(item))
        },
    )
}

@Composable
private fun StatusLine(item: DownloadItem) {
    when (item.status) {
        Status.COMPLETED -> Text("✓ " + DownloadFormat.bytes(item.expectedBytes), style = MaterialTheme.typography.bodySmall)
        Status.FAILED -> Text(item.errorMessage ?: "Download failed", style = MaterialTheme.typography.bodySmall,
                              color = MaterialTheme.colorScheme.error, maxLines = 2)
        else -> Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            LinearProgressIndicator(
                progress = { item.progress }, modifier = Modifier.fillMaxWidth(),
                color = if (item.status == Status.DOWNLOADING) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline,
            )
            Text(DownloadFormat.progressLine(item), style = MaterialTheme.typography.bodySmall, maxLines = 1)
        }
    }
}

private fun symbol(item: DownloadItem) = when (item.status) {
    Status.DOWNLOADING -> Icons.Outlined.PauseCircle
    Status.PAUSED -> Icons.Outlined.Download
    Status.PENDING -> Icons.Outlined.Schedule
    Status.COMPLETED -> Icons.Filled.PlayCircle
    Status.FAILED -> Icons.Outlined.ErrorOutline
}

@Composable
private fun symbolTint(item: DownloadItem) = when (item.status) {
    Status.DOWNLOADING, Status.COMPLETED -> MaterialTheme.colorScheme.primary
    Status.FAILED -> MaterialTheme.colorScheme.error
    else -> MaterialTheme.colorScheme.onSurfaceVariant
}

/** The poster saved with the download (so it shows offline), else the remote one. */
@Composable
fun DownloadPoster(item: DownloadItem) {
    val shape = RoundedCornerShape(6.dp)
    Box(
        Modifier.size(48.dp, 72.dp).clip(shape).background(MaterialTheme.colorScheme.surfaceContainerHighest),
        contentAlignment = Alignment.Center,
    ) {
        Icon(if (item.isMovie) Icons.Outlined.Movie else Icons.Outlined.Tv, null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
        AsyncImage(
            model = DownloadStorage.poster(item.id) ?: Artwork.url(item.posterUrl, "w342"),
            contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize(),
        )
    }
}

@Composable
private fun DeleteBackground() {
    Box(
        Modifier.fillMaxSize().background(MaterialTheme.colorScheme.errorContainer).padding(horizontal = 24.dp),
        contentAlignment = Alignment.CenterEnd,
    ) {
        Icon(Icons.Outlined.Delete, "Delete", tint = MaterialTheme.colorScheme.onErrorContainer)
    }
}


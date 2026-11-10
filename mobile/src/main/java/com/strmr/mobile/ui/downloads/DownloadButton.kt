package com.strmr.mobile.ui.downloads

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DownloadDone
import androidx.compose.material.icons.outlined.Download
import androidx.compose.material.icons.outlined.ErrorOutline
import androidx.compose.material.icons.outlined.Pause
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.strmr.mobile.downloads.DownloadActions
import com.strmr.mobile.downloads.DownloadItem
import com.strmr.mobile.downloads.DownloadItem.Status
import com.strmr.mobile.downloads.DownloadRequest
import com.strmr.mobile.downloads.Downloads
import com.strmr.tv.api.DetailsTarget
import com.strmr.tv.api.Episode

/**
 * A movie's or episode's download as a button: Download (opens the source picker), a progress
 * ring while it runs, Downloaded once done. With a download, a tap opens its menu (Play
 * Download, Pause / Resume / Retry, Cancel / Delete). [target] should carry the art and
 * overview the page loaded: the download keeps them for the offline list.
 */
@Composable
fun DownloadButton(target: DetailsTarget, episode: Episode?, modifier: Modifier = Modifier) {
    val item by remember(target.titleId, episode?.id) { Downloads.state(target, episode) }.collectAsState(Downloads.find(target, episode))
    val prompts = rememberDownloadPrompts()
    var menu by remember { mutableStateOf(false) }
    Box(modifier) {
        IconButton(onClick = { if (item == null) pick(target, episode) else menu = true },
                   enabled = target.isMovie || episode != null) {
            DownloadGlyph(item)
        }
        item?.let { DropdownMenu(menu, onDismissRequest = { menu = false }) { DownloadMenuItems(it, prompts) { menu = false } } }
    }
    DownloadPromptDialogs(prompts)
}

/** The details page's round Download action (beside Watchlist and Trailer), with the same states. */
@Composable
fun RowScope.DownloadActionItem(target: DetailsTarget, episode: Episode?) {
    val item by remember(target.titleId, episode?.id) { Downloads.state(target, episode) }.collectAsState(Downloads.find(target, episode))
    val prompts = rememberDownloadPrompts()
    var menu by remember { mutableStateOf(false) }
    Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Box {
            FilledTonalIconButton(onClick = { if (item == null) pick(target, episode) else menu = true },
                                  enabled = target.isMovie || episode != null, modifier = Modifier.size(52.dp)) {
                DownloadGlyph(item)
            }
            item?.let { DropdownMenu(menu, onDismissRequest = { menu = false }) { DownloadMenuItems(it, prompts) { menu = false } } }
        }
        Text(caption(item), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
             maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.clearAndSetSemantics { })
    }
    DownloadPromptDialogs(prompts)
}

private fun pick(target: DetailsTarget, episode: Episode?) =
    DownloadActions.pick(DownloadRequest(target, if (target.isMovie) null else episode))

private fun caption(item: DownloadItem?): String = when (item?.status) {
    null -> "Download"
    Status.COMPLETED -> "Downloaded"
    Status.DOWNLOADING -> "${Math.round(item.progress * 100)}%"
    Status.PENDING -> "Queued"
    Status.PAUSED -> "Paused"
    Status.FAILED -> "Failed"
}

/** Download / ring with pause / done / failed. */
@Composable
private fun DownloadGlyph(item: DownloadItem?) {
    val icon: ImageVector = when (item?.status) {
        null -> Icons.Outlined.Download
        Status.COMPLETED -> Icons.Filled.DownloadDone
        Status.FAILED -> Icons.Outlined.ErrorOutline
        Status.DOWNLOADING -> Icons.Outlined.Pause
        Status.PENDING, Status.PAUSED -> Icons.Outlined.Download
    }
    Box(contentAlignment = Alignment.Center) {
        if (item != null && item.isActive) {
            CircularProgressIndicator(
                progress = { item.progress }, modifier = Modifier.size(32.dp), strokeWidth = 2.5.dp,
                color = if (item.status == Status.DOWNLOADING) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline,
            )
        }
        Icon(icon, contentDescription = caption(item), modifier = Modifier.size(if (item?.isActive == true) 16.dp else 24.dp))
    }
}

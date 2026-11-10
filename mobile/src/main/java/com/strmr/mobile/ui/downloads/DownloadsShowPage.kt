package com.strmr.mobile.ui.downloads

import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.PhoneAndroid
import androidx.compose.material3.Checkbox
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.strmr.mobile.downloads.DownloadFormat
import com.strmr.mobile.downloads.DownloadItem
import com.strmr.mobile.downloads.DownloadSorting
import com.strmr.mobile.downloads.DownloadStorage
import com.strmr.mobile.downloads.Downloads

/**
 * One show's finished downloads (the iPhone app's `MobileDownloadsShowView`): a section per
 * season with the episodes in order. Tap plays, swipe or long press deletes; the bar's Delete
 * removes the whole show. Closes itself once the show has no downloads left.
 */
@Composable
fun DownloadsShowPage(titleId: String, onBack: () -> Unit) {
    val items by Downloads.items.collectAsState()
    val episodes = items.filter { it.isEpisode && it.isCompleted && it.showKey == titleId }
    val prompts = rememberDownloadPrompts()
    LaunchedEffect(episodes.isEmpty()) { if (episodes.isEmpty()) onBack() }
    if (episodes.isEmpty()) return
    DownloadPage(
        title = episodes[0].name, onBack = onBack,
        actions = { IconButton(onClick = { prompts.confirmDelete(episodes.map { it.id }) }) { Icon(Icons.Outlined.Delete, "Delete Show") } },
    ) { padding ->
        LazyColumn(Modifier.fillMaxSize(), contentPadding = padding) {
            DownloadSorting.seasons(episodes).forEach { (season, eps) ->
                item(key = "s$season") {
                    SectionHeader((if (season == 0) "Specials" else "Season $season") + " · " +
                                      DownloadFormat.bytes(eps.sumOf { it.expectedBytes }))
                }
                items(eps, key = { it.id }) { DownloadRow(it, prompts, showsSeries = false) }
            }
        }
    }
    DownloadPromptDialogs(prompts)
}

/** A show in the Downloads list: its poster, name, how many episodes and their size. */
@Composable
fun DownloadShowRow(episodes: List<DownloadItem>, prompts: DownloadPrompts, selected: Boolean?, onClick: () -> Unit) {
    val first = episodes[0]
    ListItem(
        modifier = Modifier.combinedClickable(onClick = onClick, onLongClick = { prompts.confirmDelete(episodes.map { it.id }) }),
        leadingContent = { if (selected != null) Checkbox(selected, onCheckedChange = { onClick() }) else DownloadPoster(first) },
        headlineContent = { Text(first.name, maxLines = 2, overflow = TextOverflow.Ellipsis) },
        supportingContent = {
            Text((if (episodes.size == 1) "1 episode" else "${episodes.size} episodes") + " · " +
                     DownloadFormat.bytes(episodes.sumOf { it.expectedBytes }))
        },
        trailingContent = { if (selected == null) Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, null) },
    )
}

/** Storage: what downloads take and what is left on the device, as a capacity bar. */
@Composable
fun DownloadsStorage() {
    val used by Downloads.usedBytes.collectAsState()
    val free = DownloadStorage.availableBytes
    val total = DownloadStorage.totalBytes.coerceAtLeast(1)
    ListItem(
        leadingContent = { Icon(Icons.Outlined.PhoneAndroid, null) },
        headlineContent = { Text("Storage") },
        trailingContent = { Text("${DownloadFormat.bytes(used)} used") },
        supportingContent = {
            Column(Modifier.padding(top = 6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                LinearProgressIndicator(progress = { ((total - free).toFloat() / total).coerceIn(0f, 1f) }, Modifier.fillMaxWidth())
                Text("${DownloadFormat.bytes(free)} available on this ${deviceKind()}", style = MaterialTheme.typography.bodySmall)
            }
        },
    )
}

private fun deviceKind(): String =
    if (com.strmr.tv.app.App.context.resources.configuration.smallestScreenWidthDp >= 600) "tablet" else "phone"


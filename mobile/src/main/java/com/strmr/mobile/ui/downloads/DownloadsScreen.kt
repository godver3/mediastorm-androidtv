package com.strmr.mobile.ui.downloads

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Sort
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.outlined.Checklist
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.DownloadForOffline
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.strmr.mobile.downloads.DownloadActions
import com.strmr.mobile.downloads.DownloadItem
import com.strmr.mobile.downloads.DownloadSettings
import com.strmr.mobile.downloads.DownloadSorting
import com.strmr.mobile.downloads.Downloads
import com.strmr.mobile.downloads.OfflineProgress
import com.strmr.mobile.ui.shell.LocalNavigator

/**
 * Downloads (the iPhone app's `MobileDownloadsView`): storage used, the downloads still in
 * progress, then the finished movies and shows (each a page of its seasons and episodes),
 * sorted by the bar's Sort menu. A tap plays a finished download or pauses / resumes a running
 * one; swipe or long press to delete, Select to delete several. Needs no server: everything
 * listed is on the device, so it also shows offline before sign-in ([onBack] then closes it).
 * [asPage]: opened from More, with a back arrow.
 */
@Composable
fun DownloadsScreen(asPage: Boolean, onBack: (() -> Unit)? = null, topInset: @Composable () -> Unit = {}) {
    val back = onBack ?: if (asPage) LocalNavigator.current.let { n -> { n.pop(); Unit } } else null
    var openShow by rememberSaveable { mutableStateOf<String?>(null) }
    var settingsOpen by rememberSaveable { mutableStateOf(false) }
    val items by Downloads.items.collectAsState()
    LaunchedEffect(Unit) {
        Downloads.refreshUsage()
        DownloadActions.deleteWatchedIfEnabled()
        OfflineProgress.flush()
    }
    when {
        settingsOpen -> {
            BackHandler { settingsOpen = false }
            DownloadSettingsPage(onBack = { settingsOpen = false })
        }
        openShow != null -> {
            BackHandler { openShow = null }
            DownloadsShowPage(openShow!!, onBack = { openShow = null })
        }
        else -> DownloadsList(items, back, topInset, onOpenShow = { openShow = it }, onSettings = { settingsOpen = true })
    }
}

@Composable
private fun DownloadsList(
    items: List<DownloadItem>, onBack: (() -> Unit)?, topInset: @Composable () -> Unit,
    onOpenShow: (String) -> Unit, onSettings: () -> Unit,
) {
    val prompts = rememberDownloadPrompts()
    var selection by remember { mutableStateOf<Set<String>?>(null) }
    var sortMenu by remember { mutableStateOf(false) }
    val selecting = selection != null
    if (selecting) BackHandler { selection = null }
    LaunchedEffect(items.isEmpty()) { if (items.isEmpty()) selection = null }
    DownloadPage(
        title = if (selecting) "${selection!!.size} selected" else "Downloads",
        onBack = if (selecting) ({ selection = null }) else onBack,
        large = !selecting && onBack == null,
        backIcon = { if (selecting) Icon(Icons.Filled.Close, "Done") else Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back") },
        topInset = topInset,
        actions = {
            if (selecting) {
                IconButton(onClick = { prompts.confirmDelete(selectedIds(items, selection!!)) }, enabled = selection!!.isNotEmpty()) {
                    Icon(Icons.Outlined.Delete, "Delete")
                }
            } else {
                if (items.isNotEmpty()) {
                    IconButton(onClick = { selection = emptySet() }) { Icon(Icons.Outlined.Checklist, "Select") }
                    Box {
                        IconButton(onClick = { sortMenu = true }) { Icon(Icons.AutoMirrored.Filled.Sort, "Sort") }
                        DropdownMenu(expanded = sortMenu, onDismissRequest = { sortMenu = false }) {
                            DownloadSettings.Sort.entries.forEach { s ->
                                DropdownMenuItem(
                                    text = { Text(s.label) },
                                    leadingIcon = { RadioButton(selected = DownloadSettings.sort == s, onClick = null) },
                                    onClick = { DownloadSettings.sort = s; sortMenu = false },
                                )
                            }
                        }
                    }
                }
                IconButton(onClick = onSettings) { Icon(Icons.Outlined.Settings, "Download Settings") }
            }
        },
    ) { padding ->
        if (items.isEmpty()) {
            EmptyDownloads(padding)
        } else {
            val toggle: (String) -> Unit = { id -> selection = selection?.let { if (id in it) it - id else it + id } }
            LazyColumn(Modifier.fillMaxSize(), contentPadding = padding) {
                item(key = "storage") { DownloadsStorage() }
                listSections(items, prompts, selection, toggle, onOpenShow)
            }
        }
    }
    DownloadPromptDialogs(prompts) { selection = null }
}

private fun LazyListScope.listSections(
    items: List<DownloadItem>, prompts: DownloadPrompts, selection: Set<String>?, toggle: (String) -> Unit, onOpenShow: (String) -> Unit,
) {
    // Queue order, the running ones first, whatever the sort.
    val active = items.filter { !it.isCompleted }
        .sortedWith(compareBy<DownloadItem> { if (it.status == DownloadItem.Status.DOWNLOADING) 0 else 1 }.thenBy { it.createdAt })
    if (active.isNotEmpty()) {
        item(key = "h-active") { SectionHeader("In Progress") }
        items(active, key = { it.id }) { DownloadRow(it, prompts, selection = selection, onToggle = toggle) }
    }
    val movies = DownloadSorting.sorted(items.filter { it.isCompleted && it.isMovie }, DownloadSettings.sort)
    if (movies.isNotEmpty()) {
        item(key = "h-movies") { SectionHeader("Movies") }
        items(movies, key = { it.id }) { DownloadRow(it, prompts, selection = selection, onToggle = toggle) }
    }
    val shows = DownloadSorting.shows(items, DownloadSettings.sort)
    if (shows.isNotEmpty()) {
        item(key = "h-shows") { SectionHeader("Shows") }
        items(shows, key = { "show:" + it.first }) { (key, episodes) ->
            DownloadShowRow(episodes, prompts, selected = selection?.contains("show:$key"),
                            onClick = { if (selection != null) toggle("show:$key") else onOpenShow(key) })
        }
    }
}

/** The selection's download IDs (a selected show stands for all of its finished episodes). */
private fun selectedIds(items: List<DownloadItem>, selection: Set<String>): List<String> = selection.flatMap { tag ->
    if (!tag.startsWith("show:")) listOf(tag)
    else items.filter { it.isEpisode && it.isCompleted && it.showKey == tag.removePrefix("show:") }.map { it.id }
}

@Composable
fun SectionHeader(text: String) {
    Text(text, style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.primary,
         modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 20.dp, bottom = 4.dp))
}

@Composable
private fun EmptyDownloads(padding: PaddingValues) {
    androidx.compose.foundation.layout.Column(
        Modifier.fillMaxSize().padding(padding).padding(32.dp),
        verticalArrangement = androidx.compose.foundation.layout.Arrangement.Center,
        horizontalAlignment = androidx.compose.ui.Alignment.CenterHorizontally,
    ) {
        Icon(Icons.Outlined.DownloadForOffline, null, Modifier.padding(bottom = 16.dp).padding(4.dp),
             tint = MaterialTheme.colorScheme.onSurfaceVariant)
        Text("No Downloads", style = MaterialTheme.typography.titleLarge)
        Text(
            "Movies and episodes you download can be watched without a connection. Download from a title's page.",
            style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = androidx.compose.ui.text.style.TextAlign.Center, modifier = Modifier.padding(top = 8.dp),
        )
    }
}

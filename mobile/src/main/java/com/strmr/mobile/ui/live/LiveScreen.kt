package com.strmr.mobile.ui.live

import android.content.Context
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.strmr.mobile.ui.shell.LocalNavigator
import com.strmr.mobile.ui.shell.PageScaffold
import com.strmr.mobile.ui.shell.Section
import com.strmr.mobile.ui.shell.Route
import com.strmr.tv.live.LiveFilter
import com.strmr.tv.live.LiveStore
import com.strmr.tv.player.PlaybackCoordinator
import com.strmr.tv.ui.AppToast
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * The phone's Live tab (the iPhone's `MobileLiveView`): a channel list with what is on now, or
 * (when the server has guide data) a time grid, switched with a segmented control. Filter chips
 * (All, Favorites, categories), a search field, and in the top bar the playlist source and a
 * menu with hidden channels, refresh and Recordings. Tapping a channel tunes it; a programme
 * (or a long press) opens its sheet. No multiscreen on phones.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LiveScreen(asPage: Boolean) {
    val scope = rememberCoroutineScope()
    val store = remember { LiveStore(scope) }
    val navigator = LocalNavigator.current
    val prefs = LocalContext.current.getSharedPreferences("strmr", Context.MODE_PRIVATE)
    var guideMode by rememberSaveable { mutableStateOf(prefs.getBoolean("live.mobile.guide", false)) }
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    var search by rememberSaveable { mutableStateOf("") }
    var sheet by remember { mutableStateOf<LiveSheetTarget?>(null) }
    var refreshing by remember { mutableStateOf(false) }
    val showsGuide = guideMode && store.epgEnabled == true

    LaunchedEffect(store.query) { store.start() }
    LaunchedEffect(Unit) { store.recordings.poll() }
    LaunchedEffect(Unit) {
        while (true) {
            delay(30_000)
            now = System.currentTimeMillis()
        }
    }
    // The store's confirmations ("Recording scheduled…") as the shell's snackbars.
    LaunchedEffect(store.notice) { store.notice?.let { AppToast.show(it) } }

    PageScaffold(
        "Live TV", showBack = asPage, large = false,
        actions = {
            LiveSourceMenu(store)
            LiveMoreMenu(store, onRefresh = { scope.launch { store.refresh() } },
                         onRecordings = { navigator.push(Route.OpenSection(Section.RECORDINGS)) })
        },
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding)) {
            if (store.epgEnabled == true) {
                SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp)) {
                    listOf(false to "Channels", true to "Guide").forEachIndexed { i, (guide, label) ->
                        SegmentedButton(
                            selected = guideMode == guide,
                            onClick = { guideMode = guide; prefs.edit().putBoolean("live.mobile.guide", guide).apply() },
                            shape = SegmentedButtonDefaults.itemShape(i, 2),
                        ) { Text(label) }
                    }
                }
            }
            OutlinedTextField(
                value = search,
                onValueChange = { search = it; store.setSearchText(it) },
                placeholder = { Text("Search Channels") },
                leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null) },
                trailingIcon = if (search.isNotEmpty()) {
                    { IconButton(onClick = { search = ""; store.setSearchText("") }) { Icon(Icons.Filled.Close, "Clear") } }
                } else null,
                singleLine = true,
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp),
            )
            FilterChips(store)
            store.actionError?.let {
                Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall,
                     modifier = Modifier.padding(horizontal = 16.dp))
            }
            PullToRefreshBox(
                isRefreshing = refreshing,
                onRefresh = { scope.launch { refreshing = true; store.reload(force = true); refreshing = false } },
                modifier = Modifier.weight(1f),
            ) {
                when {
                    store.visibleChannels.isNotEmpty() ->
                        if (showsGuide) LiveGuideGrid(store, now, onProgram = { sheet = it }, modifier = Modifier.fillMaxSize())
                        else LiveChannelList(store, now, onMenu = { sheet = it }, modifier = Modifier.fillMaxSize())
                    store.loadError != null -> Message("Couldn't load channels.\n${store.loadError}")
                    store.total != null && !store.isLoading -> Message(
                        when {
                            store.search.isNotEmpty() -> "No channels match “${store.search}”."
                            store.filter == LiveFilter.Favorites -> "No favorite channels yet. Long press a channel to add it."
                            else -> "No channels. Add a Live TV source on the server to watch channels here."
                        },
                    )
                    else -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
                }
            }
        }
    }

    sheet?.let { LiveProgramSheet(store, it, now) { sheet = null } }
    store.sourceChoice?.let { choice ->
        LiveSourceSheet(choice, onDismiss = { store.sourceChoice = null }) { stream ->
            store.sourceChoice = null
            val channel = choice.channel.copy(stremioStreamIndex = stream.index)
            PlaybackCoordinator.playLive(channel, store.current(channel, now)?.title)
        }
    }
}

/** All Channels and Favorites pick one; categories toggle (several at once), as the TV filter. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun FilterChips(store: LiveStore) {
    LazyRow(contentPadding = PaddingValues(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        item {
            FilterChip(selected = store.filter == LiveFilter.All, onClick = { store.filter = LiveFilter.All }, label = { Text("All") })
        }
        item {
            FilterChip(
                selected = store.filter == LiveFilter.Favorites, onClick = { store.toggleFavoritesOnly() },
                label = { Text("Favorites") },
                leadingIcon = { Icon(Icons.Filled.Star, contentDescription = null, tint = FavoriteYellow) },
            )
        }
        items(store.categories, key = { it }) { name ->
            FilterChip(selected = name in store.filter.categories, onClick = { store.toggleCategory(name) }, label = { Text(name) })
        }
    }
}

@Composable
private fun Message(text: String) {
    // Scrollable, so pull to refresh works on an empty page too.
    androidx.compose.foundation.lazy.LazyColumn(Modifier.fillMaxSize()) {
        item {
            Text(text, color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Center,
                 modifier = Modifier.fillMaxWidth().padding(horizontal = 32.dp, vertical = 64.dp))
        }
    }
}

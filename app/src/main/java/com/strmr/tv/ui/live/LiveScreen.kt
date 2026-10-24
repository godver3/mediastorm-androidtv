package com.strmr.tv.ui.live

import android.content.Context
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.CircularProgressIndicator
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
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.focusRestorer
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.tv.material3.Icon
import androidx.tv.material3.IconButton
import androidx.tv.material3.OutlinedButton
import androidx.tv.material3.Text
import androidx.compose.ui.text.font.FontWeight
import androidx.tv.material3.MaterialTheme
import com.strmr.tv.api.ApiClient
import com.strmr.tv.live.LiveChannel
import com.strmr.tv.live.LiveFilter
import com.strmr.tv.live.LiveStore
import com.strmr.tv.live.MultiscreenPrefs
import com.strmr.tv.player.PlaybackCoordinator
import com.strmr.tv.ui.AppIcons
import com.strmr.tv.ui.ChoiceDialog
import com.strmr.tv.ui.DialogChoice
import com.strmr.tv.ui.LocalNavigator
import com.strmr.tv.ui.MultiPickerDialog
import com.strmr.tv.ui.Page
import com.strmr.tv.ui.PageMargin
import com.strmr.tv.ui.PickerDialog
import com.strmr.tv.ui.live.multiscreen.MultiscreenLauncher
import com.strmr.tv.ui.onboarding.FormField
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

private enum class Picker { FILTER, SOURCE, MORE }

/**
 * The Live tab reused as a channel picker (multiscreen's Add / Change channel): [title] over
 * the same controls and guide or grid, selecting a channel calls [onPick] instead of playing
 * it, and [marked] (the channel on the screen being changed) is flagged.
 */
class LivePick(val title: String, val marked: LiveChannel?, val error: String?, val onPick: (LiveChannel) -> Unit)

/**
 * The Live tab: the channel guide (when the server has EPG data) or a grid of channel cards,
 * under one row of controls: Guide / Channels, Favorites or categories, a Favorites star, the
 * source, search and More. Back inside the guide or grid moves focus up to the control it was last on, as up does
 * and as Menu does on tvOS.
 */
@Composable
fun LiveScreen(pick: LivePick? = null, sharedStore: LiveStore? = null) {
    val scope = rememberCoroutineScope()
    val store = sharedStore ?: remember { LiveStore(scope) }
    val navigator = LocalNavigator.current
    val prefs = LocalContext.current.getSharedPreferences("strmr", Context.MODE_PRIVATE)
    var guideMode by rememberSaveable { mutableStateOf(prefs.getBoolean("live.guide", true)) }
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    var picker by remember { mutableStateOf<Picker?>(null) }
    // A shared store (the multiscreen picker) keeps its search between picks.
    var search by rememberSaveable { mutableStateOf(store.search) }
    var contentFocused by remember { mutableStateOf(false) }
    val filterFocus = remember { FocusRequester() }
    val controlsFocus = remember { FocusRequester() }
    val contentEntry = remember { FocusRequester() }
    val showsGuide = guideMode && store.epgEnabled == true

    // The first run loads favorites and the EPG status; every filter change reloads.
    LaunchedEffect(store.query) { store.start() }
    // Moves progress bars and the guide's now line along.
    LaunchedEffect(Unit) {
        while (true) {
            delay(30_000)
            now = System.currentTimeMillis()
        }
    }
    BackHandler(enabled = contentFocused) { runCatching { controlsFocus.requestFocus() } }
    // A picker opens in its own window: focus the first channel once it is there, else the filters.
    if (pick != null) {
        val hasChannels = store.visibleChannels.isNotEmpty()
        var placed by remember { mutableStateOf(false) }
        LaunchedEffect(hasChannels) {
            if (placed) return@LaunchedEffect
            repeat(10) {
                if (runCatching { (if (hasChannels) contentEntry else filterFocus).requestFocus() }.isSuccess) {
                    placed = hasChannels
                    return@LaunchedEffect
                }
                delay(50)
            }
        }
    }

    Column(Modifier.fillMaxSize().padding(top = if (pick != null) 28.dp else 76.dp).clipToBounds()) {
        pick?.let {
            Text(it.title, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold,
                 modifier = Modifier.padding(horizontal = PageMargin).padding(bottom = 14.dp))
        }
        // Entering the row (up from the guide or grid, or Back) returns to the control focus left from.
        Row(
            Modifier.fillMaxWidth().padding(horizontal = PageMargin).focusRequester(controlsFocus).focusRestorer(filterFocus),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (store.epgEnabled == true) {
                OutlinedButton(onClick = {
                    guideMode = !guideMode
                    prefs.edit().putBoolean("live.guide", guideMode).apply()
                }) { Text(if (guideMode) "Guide" else "Channels") }
            }
            OutlinedButton(onClick = { picker = Picker.FILTER }, modifier = Modifier.focusRequester(filterFocus)) {
                Text(store.filter.label, maxLines = 1)
            }
            val favoritesOnly = store.filter == LiveFilter.Favorites
            // Pressed in place: focus stays on the star.
            IconButton(onClick = { store.toggleFavoritesOnly() }) {
                Icon(
                    if (favoritesOnly) Icons.Filled.Star else AppIcons.StarBorder,
                    contentDescription = if (favoritesOnly) "Show All Channels" else "Show Favorites Only",
                    tint = if (favoritesOnly) Color(0xFFFFD60A) else Color.Unspecified,
                )
            }
            if (store.sources.size > 1) {
                OutlinedButton(onClick = { picker = Picker.SOURCE }) {
                    Text(store.sources.firstOrNull { it.id == store.sourceId }?.name ?: "All Sources", maxLines = 1)
                }
            }
            Box(Modifier.width(220.dp)) {
                FormField(search, { search = it; store.setSearchText(it) }, label = "Search Channels", imeAction = ImeAction.Search)
            }
            Spacer(Modifier.weight(1f))
            store.total?.let { Text("$it channels", color = Color.White.copy(alpha = 0.6f), maxLines = 1) }
            if (pick == null) IconButton(onClick = { picker = Picker.MORE }) { Icon(Icons.Filled.MoreVert, contentDescription = "More Options") }
        }
        val error = pick?.error ?: store.actionError
        if (error != null) {
            Text(error, color = Color(0xFFFF6B6B), modifier = Modifier.padding(horizontal = PageMargin, vertical = 4.dp))
        } else {
            store.notice?.let { Text(it, color = Color.White.copy(alpha = 0.6f), modifier = Modifier.padding(horizontal = PageMargin, vertical = 4.dp)) }
        }

        // Down from the controls enters the content where it was left (or its top).
        val content = Modifier.weight(1f).onFocusChanged { contentFocused = it.hasFocus }
        when {
            store.visibleChannels.isNotEmpty() ->
                if (showsGuide) LiveGuide(store, now, contentEntry, content, pick) else LiveChannelGrid(store, now, contentEntry, content, pick)
            store.loadError != null -> Message(store.loadError!!)
            store.total != null && !store.isLoading -> Message(
                when {
                    store.search.isNotEmpty() -> "No channels match “${store.search}”."
                    store.filter == LiveFilter.Favorites -> "No favorite channels yet. Long press a channel to add it."
                    else -> "No channels."
                },
            )
            else -> Box(Modifier.fillMaxWidth().padding(top = 60.dp), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(Modifier.size(28.dp), color = Color.White, strokeWidth = 2.dp)
            }
        }
    }

    store.sourceChoice?.let { choice ->
        LiveSourceChoiceDialog(choice, onDismiss = { store.sourceChoice = null }) { stream ->
            store.sourceChoice = null
            val channel = choice.channel.copy(stremioStreamIndex = stream.index)
            pick?.onPick?.invoke(channel) ?: PlaybackCoordinator.playLive(channel, store.current(channel, now)?.title)
        }
    }

    when (picker) {
        Picker.FILTER -> {
            // All Channels and Favorites pick and close; categories toggle with the panel left up,
            // and All Channels clears them. Focus stays on the filter control; down enters the new
            // list at its top.
            val fixed = listOf(LiveFilter.All, LiveFilter.Favorites)
            val names = fixed.map { it.label } + store.categories
            val selected = (fixed.indexOf(store.filter).takeIf { it >= 0 }?.let { setOf(it) }
                ?: store.categories.indices.filter { store.categories[it] in store.filter.categories }.map { it + fixed.size }.toSet())
            MultiPickerDialog("Channels", names, selected, onToggle = { i ->
                if (i < fixed.size) {
                    picker = null
                    if (fixed[i] != store.filter) store.filter = fixed[i]
                } else {
                    store.toggleCategory(store.categories[i - fixed.size])
                }
            }, onDismiss = { picker = null })
        }
        Picker.SOURCE -> {
            val ids = listOf<String?>(null) + store.sources.map { it.id }
            PickerDialog("Source", listOf("All Sources") + store.sources.map { it.name }, ids.indexOf(store.sourceId),
                         onPick = { store.sourceId = ids[it] }, onDismiss = { picker = null })
        }
        Picker.MORE -> {
            // The last multiscreen on this device, for this profile.
            val saved = remember { MultiscreenPrefs.saved(ApiClient.profileId) }
            ChoiceDialog(
                title = "Live TV",
                choices = listOfNotNull(
                    DialogChoice(if (store.showHidden) "Hide Hidden Channels" else "Show Hidden Channels") { store.showHidden = !store.showHidden },
                    DialogChoice("Refresh Channels") { scope.launch { store.refresh() } },
                    DialogChoice("Recordings") { navigator.open(Page.Recordings) },
                    saved?.let { DialogChoice("Resume multiscreen") { MultiscreenLauncher.resume(it) } },
                    DialogChoice("Multiscreen") { MultiscreenLauncher.open() },
                    DialogChoice("Cancel") {},
                ),
                onDismiss = { picker = null },
            )
        }
        null -> Unit
    }
}

@Composable
private fun Message(text: String) {
    Text(text, color = Color.White.copy(alpha = 0.6f), modifier = Modifier.padding(horizontal = PageMargin, vertical = 60.dp))
}

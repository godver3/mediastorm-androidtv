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
import com.strmr.tv.live.LiveFilter
import com.strmr.tv.live.LiveStore
import com.strmr.tv.ui.AppIcons
import com.strmr.tv.ui.ChoiceDialog
import com.strmr.tv.ui.DialogChoice
import com.strmr.tv.ui.LocalNavigator
import com.strmr.tv.ui.Page
import com.strmr.tv.ui.PageMargin
import com.strmr.tv.ui.PickerDialog
import com.strmr.tv.ui.onboarding.FormField
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

private enum class Picker { FILTER, SOURCE, MORE }

/**
 * The Live tab: the channel guide (when the server has EPG data) or a grid of channel cards,
 * under one row of controls: Guide / Channels, Favorites or a category, a Favorites star, the
 * source, search and More. Back inside the guide or grid moves focus up to the control it was last on, as up does
 * and as Menu does on tvOS.
 */
@Composable
fun LiveScreen() {
    val scope = rememberCoroutineScope()
    val store = remember { LiveStore(scope) }
    val navigator = LocalNavigator.current
    val prefs = LocalContext.current.getSharedPreferences("strmr", Context.MODE_PRIVATE)
    var guideMode by rememberSaveable { mutableStateOf(prefs.getBoolean("live.guide", true)) }
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    var picker by remember { mutableStateOf<Picker?>(null) }
    var search by rememberSaveable { mutableStateOf("") }
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

    Column(Modifier.fillMaxSize().padding(top = 76.dp).clipToBounds()) {
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
            IconButton(onClick = { picker = Picker.MORE }) { Icon(Icons.Filled.MoreVert, contentDescription = "More Options") }
        }
        store.actionError?.let { Text(it, color = Color(0xFFFF6B6B), modifier = Modifier.padding(horizontal = PageMargin, vertical = 4.dp)) }

        // Down from the controls enters the content where it was left (or its top).
        val content = Modifier.weight(1f).onFocusChanged { contentFocused = it.hasFocus }
        when {
            store.visibleChannels.isNotEmpty() ->
                if (showsGuide) LiveGuide(store, now, contentEntry, content) else LiveChannelGrid(store, now, contentEntry, content)
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

    when (picker) {
        Picker.FILTER -> {
            val options = listOf<LiveFilter>(LiveFilter.All, LiveFilter.Favorites) + store.categories.map { LiveFilter.Category(it) }
            // Focus stays on the category control; down enters the new list at its top.
            PickerDialog("Channels", options.map { it.label }, options.indexOf(store.filter), onPick = { i ->
                if (options[i] != store.filter) store.filter = options[i]
            }, onDismiss = { picker = null })
        }
        Picker.SOURCE -> {
            val ids = listOf<String?>(null) + store.sources.map { it.id }
            PickerDialog("Source", listOf("All Sources") + store.sources.map { it.name }, ids.indexOf(store.sourceId),
                         onPick = { store.sourceId = ids[it] }, onDismiss = { picker = null })
        }
        Picker.MORE -> ChoiceDialog(
            title = "Live TV",
            choices = listOf(
                DialogChoice(if (store.showHidden) "Hide Hidden Channels" else "Show Hidden Channels") { store.showHidden = !store.showHidden },
                DialogChoice("Refresh Channels") { scope.launch { store.refresh() } },
                DialogChoice("Recordings") { navigator.open(Page.Recordings) },
                DialogChoice("Cancel") {},
            ),
            onDismiss = { picker = null },
        )
        null -> Unit
    }
}

@Composable
private fun Message(text: String) {
    Text(text, color = Color.White.copy(alpha = 0.6f), modifier = Modifier.padding(horizontal = PageMargin, vertical = 60.dp))
}

package com.strmr.tv.ui.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.tv.material3.ListItem
import androidx.tv.material3.LocalContentColor
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import coil.compose.AsyncImage
import com.strmr.tv.api.Artwork
import com.strmr.tv.library.HiddenItem
import com.strmr.tv.library.HiddenItemsSettings
import com.strmr.tv.library.HiddenItemsSettings.Load
import com.strmr.tv.library.HiddenItemsSettings.Section
import com.strmr.tv.ui.pageBackground
import kotlinx.coroutines.launch

/**
 * Settings › Hidden Items: titles hidden from Continue Watching, then titles hidden from this
 * profile, each row undoing its hide on Select. A row that leaves hands focus to the row that
 * took its place, else the one above, else its section's empty row.
 */
@Composable
fun HiddenItemsPage() {
    val model = remember { HiddenItemsSettings() }
    val scope = rememberCoroutineScope()
    // The row that should take focus once it is composed; each row claims it itself.
    var focusKey by remember { mutableStateOf<String?>(LOADING_KEY) }
    var loaded by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        model.load()
        loaded = true
        focusKey = sectionFirstKey(model, Section.ContinueWatching)
    }

    fun undo(section: Section, items: List<HiddenItem>, index: Int) {
        val item = items[index]
        scope.launch {
            val removed = when (section) {
                Section.ContinueWatching -> model.showInContinueWatching(item)
                Section.Profile -> model.unhide(item)
            }
            if (!removed) return@launch
            val left = if (section == Section.ContinueWatching) model.continueItems else model.profileItems
            focusKey = HiddenItemsSettings.focusIndexAfterRemoval(index, left.size)
                ?.let { HiddenItemsSettings.key(section, left[it]) }
                ?: HiddenItemsSettings.statusKey(section)
        }
    }

    fun retry(section: Section) {
        scope.launch {
            if (section == Section.ContinueWatching) model.loadContinueWatching() else model.loadProfile()
            focusKey = sectionFirstKey(model, section)
        }
    }

    Box(Modifier.fillMaxSize().pageBackground(), contentAlignment = Alignment.TopCenter) {
        LazyColumn(
            Modifier.widthIn(max = 720.dp),
            contentPadding = PaddingValues(horizontal = 24.dp, vertical = 36.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            item { Text("Hidden Items", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.SemiBold) }
            if (!loaded) {
                item(key = LOADING_KEY) { StatusRow(LOADING_KEY, "Loading…", focusKey, { focusKey = null }) }
                return@LazyColumn
            }
            section(
                Section.ContinueWatching, "Continue Watching", model.continueLoad, model.continueItems, model.busy,
                empty = "Nothing hidden from Continue Watching", action = "Show in Continue Watching",
                focusKey = focusKey, onFocused = { focusKey = null }, onUndo = ::undo, onRetry = ::retry,
            )
            section(
                Section.Profile, "Hidden from Profile", model.profileLoad, model.profileItems, model.busy,
                empty = "Nothing hidden from this profile", action = "Unhide",
                focusKey = focusKey, onFocused = { focusKey = null }, onUndo = ::undo, onRetry = ::retry,
            )
        }
    }
}

private const val LOADING_KEY = "loading"

private fun sectionFirstKey(model: HiddenItemsSettings, section: Section): String {
    val items = if (section == Section.ContinueWatching) model.continueItems else model.profileItems
    return items.firstOrNull()?.let { HiddenItemsSettings.key(section, it) } ?: HiddenItemsSettings.statusKey(section)
}

@Suppress("LongParameterList")
private fun LazyListScope.section(
    section: Section,
    title: String,
    load: Load,
    items: List<HiddenItem>,
    busy: Set<String>,
    empty: String,
    action: String,
    focusKey: String?,
    onFocused: () -> Unit,
    onUndo: (Section, List<HiddenItem>, Int) -> Unit,
    onRetry: (Section) -> Unit,
) {
    item(key = "header:${section.prefix}") {
        Text(title, style = MaterialTheme.typography.titleSmall, color = Color.White.copy(alpha = 0.6f),
             modifier = Modifier.padding(top = 14.dp, start = 4.dp))
    }
    val status = HiddenItemsSettings.statusKey(section)
    when {
        load == Load.Loading -> item(key = status) { StatusRow(status, "Loading…", focusKey, onFocused) }
        load is Load.Failed && items.isEmpty() -> item(key = status) {
            StatusRow(status, "Couldn't load · Select to try again", focusKey, onFocused) { onRetry(section) }
        }
        items.isEmpty() -> item(key = status) { StatusRow(status, empty, focusKey, onFocused) }
        else -> itemsIndexed(items, key = { _, it -> HiddenItemsSettings.key(section, it) }) { index, item ->
            val key = HiddenItemsSettings.key(section, item)
            HiddenRow(item, if (key in busy) "Working…" else action, key == focusKey, onFocused) { onUndo(section, items, index) }
        }
    }
}

/** A placeholder row: focusable, so the section always has somewhere for focus to land. */
@Composable
private fun StatusRow(key: String, text: String, focusKey: String?, onFocused: () -> Unit, onClick: () -> Unit = {}) {
    val requester = rememberClaim(key == focusKey, onFocused)
    ListItem(
        selected = false, onClick = onClick,
        headlineContent = { Text(text, color = LocalContentColor.current.copy(alpha = 0.7f)) },
        modifier = Modifier.focusRequester(requester),
    )
}

@Composable
private fun HiddenRow(item: HiddenItem, action: String, claim: Boolean, onFocused: () -> Unit, onClick: () -> Unit) {
    val requester = rememberClaim(claim, onFocused)
    ListItem(
        selected = false, onClick = onClick,
        headlineContent = { Text(item.displayName, maxLines = 1, overflow = TextOverflow.Ellipsis) },
        supportingContent = { Text(listOfNotNull(item.year?.toString(), if (item.isMovie) "Movie" else "Show").joinToString(" · ")) },
        leadingContent = {
            AsyncImage(
                model = Artwork.url(item.posterUrl, "w185"), contentDescription = null, contentScale = ContentScale.Crop,
                modifier = Modifier.size(40.dp, 60.dp).clip(RoundedCornerShape(4.dp)).background(Color(0xFF1C1C1E)),
            )
        },
        trailingContent = { Text(action) },
        modifier = Modifier.focusRequester(requester),
    )
}

/** A requester that takes focus once [claim] turns true (after a frame, so the row is placed). */
@Composable
private fun rememberClaim(claim: Boolean, onFocused: () -> Unit): FocusRequester {
    val requester = remember { FocusRequester() }
    LaunchedEffect(claim) {
        if (!claim) return@LaunchedEffect
        withFrameNanos { }
        if (runCatching { requester.requestFocus() }.isSuccess) onFocused()
    }
    return requester
}

package com.strmr.tv.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.tv.material3.Button
import androidx.tv.material3.ListItem
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import com.strmr.tv.ui.onboarding.FormField
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@Composable
fun TasteSetupDialog() {
    val editor = currentTasteSetupEditor() ?: return
    val state by editor.state.collectAsState()
    val scope = rememberCoroutineScope()
    var query by remember(editor) { mutableStateOf("") }
    var movie by remember(editor) { mutableStateOf(true) }
    var showSelected by remember(editor) { mutableStateOf(false) }
    val first = remember(editor) { FocusRequester() }
    LaunchedEffect(state.mediaTypes) { if (state.mediaTypes.isNotEmpty() && (if (movie) "movie" else "series") !in state.mediaTypes) movie = !movie }
    LaunchedEffect(editor, query, movie, state.available, state.intro) { if (state.available && !state.intro) { delay(300); editor.search(query, movie) } }
    LaunchedEffect(editor, state.loading, state.intro) { withFrameNanos { }; runCatching { first.requestFocus() } }
    Dialog(onDismissRequest = TasteSetupPrompt::dismiss, properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false)) {
        Column(Modifier.fillMaxSize().background(Color(0xFF11151D)).padding(horizontal = 40.dp, vertical = 24.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            Text("Starting interests", style = MaterialTheme.typography.headlineMedium)
            Text("Choose a few titles you enjoy. Your ratings, history and lists stay as they are.")
            if (state.loading || state.saving) Text(if (state.saving) "Saving…" else "Loading…")
            state.error?.let { Text(it) }
            state.searchError?.let { Text(it); Button(onClick = { scope.launch { editor.search(query, movie) } }, enabled = !state.saving) { Text("Retry search") } }
            when {
                state.loading -> Spacer(Modifier.weight(1f))
                !state.available -> {
                    Text(if (state.error == null) "Starting interests are unavailable for this profile." else "Try again when the server is available.")
                    Button(onClick = { scope.launch { editor.load() } }, modifier = Modifier.focusRequester(first)) { Text("Retry") }
                    Spacer(Modifier.weight(1f))
                }
                state.intro -> {
                    Text("Make your first suggestions personal, or skip and set this up later in Settings.")
                    Button(onClick = editor::chooseFavorites, modifier = Modifier.focusRequester(first)) { Text("Choose favorites") }
                    if (state.canUseHistory) Button(onClick = { scope.launch { editor.finish("history") } }, enabled = !state.saving) { Text("Use my existing profile history") }
                    Button(onClick = { scope.launch { editor.finish("skip") } }, enabled = !state.saving) { Text("Skip") }
                    Spacer(Modifier.weight(1f))
                }
                else -> {
                    FormField(query, { if (!state.saving) { query = it; showSelected = false } }, "Search titles", imeAction = ImeAction.Search, onSubmit = {}, modifier = Modifier.fillMaxWidth().focusRequester(first))
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        if ("movie" in state.mediaTypes) Button(onClick = { movie = true; showSelected = false }, enabled = !state.saving) { Text("Movies") }
                        if ("series" in state.mediaTypes) Button(onClick = { movie = false; showSelected = false }, enabled = !state.saving) { Text("Series") }
                        Button(onClick = { showSelected = !showSelected }, enabled = !state.saving) { Text(if (showSelected) "Browse" else "Selected (${state.selected.size})") }
                        if (state.selected.isNotEmpty()) Button(onClick = editor::clearSelection, enabled = !state.saving) { Text("Clear selection") }
                    }
                    if (state.searching && !showSelected) Text("Finding titles…")
                    val titles = if (showSelected) state.selected else state.candidates
                    LazyColumn(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(5.dp)) {
                        if (titles.isEmpty()) item { Text(if (showSelected) "No titles selected" else "Search for a title you enjoy.") }
                        items(titles, key = { (if (it.isMovie) "movie" else "series") + ":" + it.id }) { title ->
                            ListItem(
                                selected = editor.selected(title), enabled = !state.saving,
                                onClick = { editor.toggle(title) },
                                headlineContent = { Text(title.name.ifBlank { "Untitled" }) },
                                supportingContent = { Text(listOfNotNull(title.year?.toString(), if (title.personalRating == "dislike") "Your Not for me rating takes priority" else null).joinToString(" · ")) },
                                trailingContent = { Text(if (editor.selected(title)) "Selected" else "Choose") },
                            )
                        }
                    }
                }
            }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(14.dp), verticalAlignment = Alignment.CenterVertically) {
                if (state.available && !state.loading && !state.intro) Button(onClick = { scope.launch { editor.finish() } }, enabled = !state.saving) { Text("Done (${state.selected.size})") }
                Button(onClick = TasteSetupPrompt::dismiss) { Text("Cancel") }
            }
        }
    }
}

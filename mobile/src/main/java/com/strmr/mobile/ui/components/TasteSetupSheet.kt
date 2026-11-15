package com.strmr.mobile.ui.components

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.selection.toggleable
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import com.strmr.tv.ui.TasteSetupPrompt
import com.strmr.tv.ui.currentTasteSetupEditor
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TasteSetupSheet() {
    val editor = currentTasteSetupEditor() ?: return
    val state by editor.state.collectAsState()
    val scope = rememberCoroutineScope()
    var query by remember(editor) { mutableStateOf("") }
    var movie by remember(editor) { mutableStateOf(true) }
    var selectedOnly by remember(editor) { mutableStateOf(false) }
    LaunchedEffect(state.mediaTypes) { if (state.mediaTypes.isNotEmpty() && (if (movie) "movie" else "series") !in state.mediaTypes) movie = !movie }
    LaunchedEffect(editor, query, movie, state.available, state.intro) { if (state.available && !state.intro) { delay(300); editor.search(query, movie) } }
    ModalBottomSheet(onDismissRequest = TasteSetupPrompt::dismiss, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)) {
        Column(Modifier.fillMaxWidth().fillMaxHeight(.94f).navigationBarsPadding().padding(horizontal = 20.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text("Starting interests", style = MaterialTheme.typography.headlineSmall)
            Text("Choose a few titles you enjoy. Your ratings, history and lists stay as they are.")
            if (state.loading || state.saving) LinearProgressIndicator(Modifier.fillMaxWidth())
            state.error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
            state.searchError?.let { Text(it, color = MaterialTheme.colorScheme.error); TextButton(onClick = { scope.launch { editor.search(query, movie) } }, enabled = !state.saving) { Text("Retry search") } }
            when {
                state.loading -> Spacer(Modifier.weight(1f))
                !state.available -> {
                    Text("Starting interests are unavailable for this profile right now.")
                    TextButton(onClick = { scope.launch { editor.load() } }) { Text("Retry") }
                    Spacer(Modifier.weight(1f))
                }
                state.intro -> {
                    Text("Make your first suggestions personal, or skip and set this up later in Settings.")
                    Button(onClick = editor::chooseFavorites, enabled = !state.saving, modifier = Modifier.fillMaxWidth()) { Text("Choose favorites") }
                    if (state.canUseHistory) OutlinedButton(onClick = { scope.launch { editor.finish("history") } }, enabled = !state.saving, modifier = Modifier.fillMaxWidth()) { Text("Use my existing profile history") }
                    OutlinedButton(onClick = { scope.launch { editor.finish("skip") } }, enabled = !state.saving, modifier = Modifier.fillMaxWidth()) { Text("Skip") }
                    Spacer(Modifier.weight(1f))
                }
                else -> {
                    OutlinedTextField(value = query, onValueChange = { query = it; selectedOnly = false }, label = { Text("Search titles") }, singleLine = true, enabled = !state.saving, modifier = Modifier.fillMaxWidth())
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        if ("movie" in state.mediaTypes) FilterChip(selected = movie && !selectedOnly, onClick = { movie = true; selectedOnly = false }, label = { Text("Movies") }, enabled = !state.saving)
                        if ("series" in state.mediaTypes) FilterChip(selected = !movie && !selectedOnly, onClick = { movie = false; selectedOnly = false }, label = { Text("Series") }, enabled = !state.saving)
                        FilterChip(selected = selectedOnly, onClick = { selectedOnly = !selectedOnly }, label = { Text("Selected (${state.selected.size})") }, enabled = !state.saving)
                    }
                    if (state.searching && !selectedOnly) LinearProgressIndicator(Modifier.fillMaxWidth())
                    val titles = if (selectedOnly) state.selected else state.candidates
                    LazyColumn(Modifier.weight(1f)) {
                        if (titles.isEmpty()) item { Text(if (selectedOnly) "No titles selected" else "Search for a title you enjoy.") }
                        items(titles, key = { (if (it.isMovie) "movie" else "series") + ":" + it.id }) { title ->
                            ListItem(
                                headlineContent = { Text(title.name.ifBlank { "Untitled" }) },
                                supportingContent = { Text(listOfNotNull(title.year?.toString(), if (title.personalRating == "dislike") "Your Not for me rating takes priority" else null).joinToString(" · ")) },
                                leadingContent = { Checkbox(checked = editor.selected(title), onCheckedChange = null) },
                                modifier = Modifier.toggleable(value = editor.selected(title), enabled = !state.saving, role = Role.Checkbox) { editor.toggle(title) },
                            )
                        }
                    }
                    if (state.selected.isNotEmpty()) TextButton(onClick = editor::clearSelection, enabled = !state.saving) { Text("Clear selection") }
                }
            }
            Row(Modifier.fillMaxWidth().padding(bottom = 16.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                if (state.available && !state.loading && !state.intro) Button(onClick = { scope.launch { editor.finish() } }, enabled = !state.saving) { Text("Done (${state.selected.size})") }
                TextButton(onClick = TasteSetupPrompt::dismiss) { Text("Cancel") }
            }
        }
    }
}

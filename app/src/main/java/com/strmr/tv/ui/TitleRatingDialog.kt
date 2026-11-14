package com.strmr.tv.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.tv.material3.Button
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import com.strmr.tv.ratings.PersonalRating
import kotlinx.coroutines.launch

@Composable
fun TitleRatingDialog() {
    val editor = currentRatingEditor() ?: return
    val state by editor.state.collectAsState()
    val scope = rememberCoroutineScope()
    val first = remember(editor) { FocusRequester() }
    LaunchedEffect(editor, state.loading, state.saving, state.error) { withFrameNanos { }; runCatching { first.requestFocus() } }
    Dialog(onDismissRequest = TitleRatingPrompt::dismiss) {
        Column(Modifier.width(420.dp).background(Color(0xFF1C1C1E)).verticalScroll(rememberScrollState()).padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(editor.target.name, style = MaterialTheme.typography.titleLarge)
            Text("Your rating for this profile", style = MaterialTheme.typography.bodyMedium)
            if (state.loading || state.saving) Text(if (state.saving) "Saving…" else "Loading…")
            state.error?.let { Text(it) }
            if (!state.loading && !state.saving) {
                if (state.rating == null) {
                    Button(onClick = { scope.launch { editor.load() } }, modifier = Modifier.focusRequester(first)) { Text("Retry") }
                } else {
                    PersonalRating.entries.forEachIndexed { index, rating ->
                        Button(onClick = { scope.launch { editor.save(rating) } }, modifier = Modifier.fillMaxWidth().then(if (index == 0) Modifier.focusRequester(first) else Modifier)) {
                            Text(rating.label + if (state.rating == rating) " (selected)" else "")
                        }
                    }
                }
            }
            Button(onClick = TitleRatingPrompt::dismiss, modifier = Modifier.then(if (state.loading || state.saving) Modifier.focusRequester(first) else Modifier)) { Text("Done") }
        }
    }
}

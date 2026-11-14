package com.strmr.mobile.ui.components

import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.ui.semantics.Role
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.strmr.tv.ratings.PersonalRating
import com.strmr.tv.ui.TitleRatingPrompt
import com.strmr.tv.ui.currentRatingEditor
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TitleRatingSheet() {
    val editor = currentRatingEditor() ?: return
    val state by editor.state.collectAsState()
    val scope = rememberCoroutineScope()
    ModalBottomSheet(onDismissRequest = TitleRatingPrompt::dismiss) {
        Column(Modifier.fillMaxWidth().selectableGroup().verticalScroll(rememberScrollState()).navigationBarsPadding().padding(bottom = 16.dp)) {
            Text(editor.target.name, style = MaterialTheme.typography.titleLarge, modifier = Modifier.padding(horizontal = 24.dp))
            Text("Your rating for this profile", modifier = Modifier.padding(horizontal = 24.dp, vertical = 8.dp))
            if (state.loading || state.saving) LinearProgressIndicator(Modifier.fillMaxWidth())
            state.error?.let { Text(it, color = MaterialTheme.colorScheme.error, modifier = Modifier.padding(24.dp)) }
            if (!state.loading && state.rating == null) TextButton(onClick = { scope.launch { editor.load() } }) { Text("Retry") }
            if (state.rating != null) PersonalRating.entries.forEach { rating ->
                ListItem(
                    headlineContent = { Text(rating.label) },
                    leadingContent = { RadioButton(selected = rating == state.rating, onClick = null) },
                    modifier = Modifier.selectable(selected = rating == state.rating, enabled = !state.saving, role = Role.RadioButton) { scope.launch { editor.save(rating) } },
                )
            }
            TextButton(onClick = TitleRatingPrompt::dismiss, modifier = Modifier.padding(horizontal = 16.dp)) { Text("Done") }
        }
    }
}

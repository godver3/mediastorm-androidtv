package com.strmr.mobile.ui.home

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import com.strmr.mobile.ui.components.rememberCardOpener
import com.strmr.mobile.ui.shell.LocalNavigator
import com.strmr.tv.api.ApiClient
import com.strmr.tv.api.DetailsTarget
import com.strmr.tv.home.BrowseList
import com.strmr.tv.home.HomeAction
import com.strmr.tv.home.HomeCard
import com.strmr.tv.home.HomeLink
import com.strmr.tv.home.HomeMediaFilter
import com.strmr.tv.home.HomeSettings
import com.strmr.tv.home.surprise
import kotlinx.coroutines.launch

/**
 * What tapping a Home card does: the shared opener, with a list opened in the Home view's media
 * type, and the Watch Something actions (as the TV app's): Ask AI asks for a description and
 * opens the server's picks as a list; Surprise Me opens the server's random pick of the view's
 * kind. The prompt and any error are drawn here.
 */
@Composable
fun rememberHomeCardOpener(settings: () -> HomeSettings?): (HomeCard) -> Unit {
    val navigator = LocalNavigator.current
    val scope = rememberCoroutineScope()
    var asking by remember { mutableStateOf(false) }
    var finding by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }

    if (asking) AskAIDialog(onAsk = { navigator.openList(BrowseList.askAI(it)) }, onDismiss = { asking = false })
    error?.let {
        AlertDialog(
            onDismissRequest = { error = null },
            title = { Text("Couldn't Pick a Title") },
            text = { Text(it) },
            confirmButton = { TextButton(onClick = { error = null }) { Text("OK") } },
        )
    }

    val open = rememberCardOpener { action ->
        when (action) {
            HomeAction.ASK_AI -> asking = true
            HomeAction.SURPRISE -> if (!finding) {
                finding = true
                scope.launch {
                    try {
                        val pick = ApiClient.surprise(settings()?.filter ?: HomeMediaFilter.ALL)
                        navigator.openDetails(DetailsTarget.from(pick))
                    } catch (e: Exception) {
                        error = e.message ?: "Something went wrong."
                    } finally {
                        finding = false
                    }
                }
            }
        }
    }
    return { card ->
        when (val link = card.link) {
            is HomeLink.OpenList -> navigator.openList(link.list, settings()?.viewQuery)
            else -> open(card)
        }
    }
}

/** "Describe what you're in the mood for": a text field in an alert. */
@Composable
private fun AskAIDialog(onAsk: (String) -> Unit, onDismiss: () -> Unit) {
    var prompt by remember { mutableStateOf("") }
    val field = remember { FocusRequester() }
    LaunchedEffect(Unit) { runCatching { field.requestFocus() } }
    fun ask() {
        val text = prompt.trim()
        if (text.isEmpty()) return
        onDismiss()
        onAsk(text)
    }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Ask AI") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text("Describe what you're in the mood for.")
                OutlinedTextField(
                    prompt, { prompt = it },
                    placeholder = { Text("A cozy mystery from the 90s") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                    keyboardActions = KeyboardActions(onSearch = { ask() }),
                    modifier = Modifier.fillMaxWidth().focusRequester(field),
                )
            }
        },
        confirmButton = { TextButton(onClick = ::ask, enabled = prompt.isNotBlank()) { Text("Ask") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

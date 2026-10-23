package com.strmr.tv.ui.home

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.tv.material3.Button
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.OutlinedButton
import androidx.tv.material3.Text
import com.strmr.tv.api.ApiClient
import com.strmr.tv.api.DetailsTarget
import com.strmr.tv.home.BrowseList
import com.strmr.tv.home.HomeAction
import com.strmr.tv.home.HomeCard
import com.strmr.tv.home.HomeLink
import com.strmr.tv.home.HomeMediaFilter
import com.strmr.tv.home.HomeSettings
import com.strmr.tv.home.surprise
import com.strmr.tv.player.PlaybackCoordinator
import com.strmr.tv.ui.LocalNavigator
import com.strmr.tv.ui.MessageDialog
import com.strmr.tv.ui.Page
import com.strmr.tv.ui.onboarding.FormField
import kotlinx.coroutines.launch

/**
 * What selecting a Home card does: details, a list, a live channel, or a Watch Something action
 * (Ask AI asks for a description and opens the server's picks as a list; Surprise Me opens the
 * server's random pick). Returns the handler; any prompt or error it needs is drawn here.
 */
@Composable
fun rememberCardHandler(settings: () -> HomeSettings? = { null }): (HomeCard) -> Unit {
    val navigator = LocalNavigator.current
    val scope = rememberCoroutineScope()
    var asking by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var finding by remember { mutableStateOf(false) }

    if (asking) AskAIDialog(onAsk = { navigator.openList(BrowseList.askAI(it)) }, onDismiss = { asking = false })
    error?.let { MessageDialog("Couldn't Pick a Title", it) { error = null } }

    return { card ->
        when (val link = card.link) {
            is HomeLink.Details -> navigator.openDetails(link.target)
            is HomeLink.OpenList -> navigator.openList(link.list, settings()?.viewQuery)
            is HomeLink.Channel -> PlaybackCoordinator.playLive(link.channel, link.program?.title)
            is HomeLink.Person -> navigator.open(Page.Person(link.person))
            is HomeLink.Action -> when (link.action) {
                HomeAction.ASK_AI -> asking = true
                HomeAction.SURPRISE -> if (!finding) {
                    finding = true
                    scope.launch {
                        try {
                            navigator.openDetails(DetailsTarget.from(ApiClient.surprise(settings()?.filter ?: HomeMediaFilter.ALL)))
                        } catch (e: Exception) {
                            error = e.message
                        } finally {
                            finding = false
                        }
                    }
                }
            }
        }
    }
}

/** "Describe what you're in the mood for": a text field over the page. */
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
    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.6f)), contentAlignment = Alignment.Center) {
            Column(
                Modifier.width(480.dp).background(Color(0xFF1C1C1E), RoundedCornerShape(20.dp)).padding(28.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                Text("Ask AI", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
                Text("Describe what you're in the mood for.", color = Color.White.copy(alpha = 0.7f))
                FormField(prompt, { prompt = it }, label = "Description", placeholder = "A cozy mystery from the 90s",
                          imeAction = ImeAction.Search, onSubmit = ::ask, modifier = Modifier.focusRequester(field))
                Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                    Button(onClick = ::ask, enabled = prompt.isNotBlank()) { Text("Ask") }
                    OutlinedButton(onClick = onDismiss) { Text("Cancel") }
                }
            }
        }
    }
}

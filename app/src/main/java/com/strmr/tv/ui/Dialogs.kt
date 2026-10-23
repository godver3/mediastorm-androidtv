package com.strmr.tv.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.tv.material3.Button
import androidx.tv.material3.ButtonDefaults
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.OutlinedButton
import androidx.tv.material3.OutlinedButtonDefaults
import androidx.tv.material3.Text

/**
 * Ignores Select until it is pressed again inside the dialog. A long press opens its menu while
 * Select is still held, and the held key's repeats and release would otherwise click whatever
 * the menu focuses first.
 */
@Composable
fun Modifier.ignoreHeldSelect(): Modifier {
    var armed by remember { mutableStateOf(false) }
    return onPreviewKeyEvent { e ->
        if (armed || e.key !in SelectKeys) return@onPreviewKeyEvent false
        // A fresh press arms the dialog; anything before it is the held key from outside.
        if (e.type == KeyEventType.KeyDown && e.nativeKeyEvent.repeatCount == 0) {
            armed = true
            false
        } else true
    }
}

private val SelectKeys = setOf(Key.DirectionCenter, Key.Enter, Key.NumPadEnter)

/** One choice in a [ChoiceDialog]. */
data class DialogChoice(val label: String, val action: () -> Unit)

/**
 * A centred prompt with stacked choices, the first focused, in a platform dialog so focus and
 * Back stay in it (Back dismisses): the counterpart of a tvOS confirmation dialog or alert.
 * [steady]: the focused choice doesn't grow, so every choice keeps the same width.
 */
@Composable
fun ChoiceDialog(title: String, message: String? = null, choices: List<DialogChoice>, onDismiss: () -> Unit, steady: Boolean = false) {
    val first = remember { FocusRequester() }
    LaunchedEffect(Unit) { runCatching { first.requestFocus() } }
    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Box(Modifier.fillMaxSize().ignoreHeldSelect().background(Color.Black.copy(alpha = 0.6f)), contentAlignment = Alignment.Center) {
            Column(
                Modifier.width(420.dp).background(Color(0xFF1C1C1E), RoundedCornerShape(20.dp)).padding(28.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Text(title, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold, textAlign = TextAlign.Center)
                message?.let { Text(it, color = Color.White.copy(alpha = 0.7f), textAlign = TextAlign.Center) }
                choices.forEachIndexed { i, choice ->
                    val modifier = Modifier.fillMaxWidth().then(if (i == 0) Modifier.focusRequester(first) else Modifier)
                    val onClick = { onDismiss(); choice.action() }
                    if (i == 0) {
                        Button(onClick = onClick, modifier = modifier,
                               scale = if (steady) ButtonDefaults.scale(focusedScale = 1f) else ButtonDefaults.scale()) { Text(choice.label, Modifier.fillMaxWidth(), textAlign = TextAlign.Center) }
                    } else {
                        OutlinedButton(onClick = onClick, modifier = modifier,
                                       scale = if (steady) OutlinedButtonDefaults.scale(focusedScale = 1f) else OutlinedButtonDefaults.scale()) { Text(choice.label, Modifier.fillMaxWidth(), textAlign = TextAlign.Center) }
                    }
                }
            }
        }
    }
}

/** An alert with one OK. */
@Composable
fun MessageDialog(title: String, message: String, onDismiss: () -> Unit) =
    ChoiceDialog(title, message, listOf(DialogChoice("OK") {}), onDismiss)

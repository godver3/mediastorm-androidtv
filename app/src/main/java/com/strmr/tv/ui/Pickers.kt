package com.strmr.tv.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.tv.material3.Icon
import androidx.tv.material3.ListItem
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text

/** A side panel listing choices, the current one checked and focused (the tvOS menu pickers' counterpart). */
@Composable
fun PickerDialog(title: String, options: List<String>, selected: Int, onPick: (Int) -> Unit, onDismiss: () -> Unit) =
    SidePanel(title, options, { it == selected }, selected.coerceAtLeast(0), onDismiss) { index -> onDismiss(); onPick(index) }

/** Like [PickerDialog], but each choice toggles in place and the panel stays up until Back. */
@Composable
fun MultiPickerDialog(title: String, options: List<String>, selected: Set<Int>, onToggle: (Int) -> Unit, onDismiss: () -> Unit) =
    SidePanel(title, options, { it in selected }, selected.minOrNull() ?: 0, onDismiss, onToggle)

@Composable
private fun SidePanel(
    title: String,
    options: List<String>,
    isSelected: (Int) -> Boolean,
    focusIndex: Int,
    onDismiss: () -> Unit,
    onClick: (Int) -> Unit,
) {
    val current = remember { FocusRequester() }
    LaunchedEffect(Unit) { runCatching { current.requestFocus() } }
    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false)) {
        Box(Modifier.fillMaxSize().ignoreHeldSelect().background(Color.Black.copy(alpha = 0.5f)), contentAlignment = Alignment.CenterEnd) {
            LazyColumn(
                Modifier.fillMaxHeight().width(360.dp).background(Color(0xF01C1C1E)),
                contentPadding = PaddingValues(20.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                item { Text(title, style = MaterialTheme.typography.titleLarge, modifier = Modifier.padding(bottom = 8.dp)) }
                itemsIndexed(options) { index, option ->
                    val selected = isSelected(index)
                    ListItem(
                        selected = selected,
                        onClick = { onClick(index) },
                        headlineContent = { Text(option, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                        trailingContent = if (selected) { { Icon(Icons.Filled.Check, contentDescription = "Selected") } } else null,
                        modifier = if (index == focusIndex) Modifier.focusRequester(current) else Modifier,
                    )
                }
            }
        }
    }
}

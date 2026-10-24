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
import androidx.compose.foundation.lazy.LazyListScope
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
import androidx.compose.ui.unit.sp
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
    SidePanelDialog(title, onDismiss = onDismiss) {
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

/**
 * The side panel's frame with free-form rows, for panels that mix sections ([PanelSection]),
 * actions that close it and toggles that leave it up (Watch State, Manage Lists, More).
 * Back dismisses; the caller focuses its first row.
 */
@Composable
fun SidePanelDialog(title: String, subtitle: String? = null, onDismiss: () -> Unit, content: LazyListScope.() -> Unit) {
    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false)) {
        Box(Modifier.fillMaxSize().ignoreHeldSelect().background(Color.Black.copy(alpha = 0.5f)), contentAlignment = Alignment.CenterEnd) {
            LazyColumn(
                Modifier.fillMaxHeight().width(360.dp).background(Color(0xF01C1C1E)),
                contentPadding = PaddingValues(20.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                item {
                    Text(title, style = MaterialTheme.typography.titleLarge, modifier = Modifier.padding(bottom = if (subtitle == null) 8.dp else 0.dp))
                    subtitle?.let {
                        Text(it, fontSize = 14.sp, color = Color.White.copy(alpha = 0.6f), maxLines = 1, overflow = TextOverflow.Ellipsis,
                             modifier = Modifier.padding(bottom = 8.dp))
                    }
                }
                content()
            }
        }
    }
}

/** A grey section heading in a [SidePanelDialog]. */
@Composable
fun PanelSection(title: String) {
    Text(title, fontSize = 14.sp, color = Color.White.copy(alpha = 0.6f), maxLines = 1, overflow = TextOverflow.Ellipsis,
         modifier = Modifier.padding(top = 16.dp, bottom = 4.dp))
}

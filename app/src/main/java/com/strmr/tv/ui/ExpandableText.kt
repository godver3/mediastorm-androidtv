package com.strmr.tv.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.tv.material3.ClickableSurfaceDefaults
import androidx.tv.material3.Surface
import androidx.tv.material3.Text

/**
 * Text cut off after `maxLines`. When it doesn't fit it becomes focusable, with "More" under
 * it, and select opens the whole text over the page (the tvOS details overview and person
 * biography). Text that fits stays plain, so focus skips it.
 */
@Composable
fun ExpandableText(
    text: String,
    title: String,
    maxLines: Int,
    fontSize: TextUnit,
    modifier: Modifier = Modifier,
    color: Color = Color.White.copy(alpha = 0.85f),
) {
    var cutOff by remember(text) { mutableStateOf(false) }
    var open by remember { mutableStateOf(false) }
    val body = @Composable {
        Column {
            Text(text, fontSize = fontSize, color = color, maxLines = maxLines, overflow = TextOverflow.Ellipsis,
                 onTextLayout = { if (it.hasVisualOverflow) cutOff = true })
            if (cutOff) Text("More", fontSize = fontSize, fontWeight = FontWeight.SemiBold, color = Color.White.copy(alpha = 0.6f))
        }
    }
    if (!cutOff) {
        Box(modifier) { body() }
    } else {
        Surface(
            onClick = { open = true },
            // The focus highlight's padding sits outside the text's own edge, so it lines up with plain text.
            modifier = modifier.offset(x = (-8).dp, y = (-6).dp),
            shape = ClickableSurfaceDefaults.shape(RoundedCornerShape(10.dp)),
            colors = ClickableSurfaceDefaults.colors(
                containerColor = Color.Transparent,
                focusedContainerColor = Color.White.copy(alpha = 0.12f),
                contentColor = Color.White,
                focusedContentColor = Color.White,
            ),
            scale = ClickableSurfaceDefaults.scale(focusedScale = 1.02f),
        ) {
            Box(Modifier.padding(horizontal = 8.dp, vertical = 6.dp)) { body() }
        }
    }
    if (open) FullTextDialog(title, text) { open = false }
}

/** A long text whole, over a dimmed page; scrolls with the D-pad, Back closes it. */
@Composable
fun FullTextDialog(title: String, text: String, onDismiss: () -> Unit) {
    val focus = remember { FocusRequester() }
    LaunchedEffect(Unit) { runCatching { focus.requestFocus() } }
    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.85f)), contentAlignment = Alignment.Center) {
            LazyColumn(Modifier.widthIn(max = 620.dp).focusRequester(focus), contentPadding = PaddingValues(vertical = 60.dp)) {
                item { Text(title, fontSize = 26.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(bottom = 18.dp)) }
                item { Text(text, fontSize = 17.sp, color = Color.White.copy(alpha = 0.9f)) }
            }
        }
    }
}

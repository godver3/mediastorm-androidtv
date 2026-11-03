package com.strmr.tv.ui.details

import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.tv.material3.Icon
import androidx.tv.material3.ListItem
import androidx.tv.material3.Text
import com.strmr.tv.api.SeriesOrdering
import com.strmr.tv.ui.SidePanelDialog
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

/**
 * Alternate episode orderings (TVDB season types: Aired, DVD, Absolute, …), the Expo details
 * page's "Episode Order" picker, as a side panel opened from More: the current one checked and
 * focused. Picking one saves it per series for the profile and reloads the page in it; episodes
 * then carry their ordering (`Episode.numbering`) into prequeue and source searches. A
 * non-default ordering turns off watch-history sync for the series, which the panel says, as
 * Expo's picker warning does.
 */
@Composable
fun EpisodeOrderDialog(model: DetailsModel, scope: CoroutineScope, onDismiss: () -> Unit) {
    val orderings = model.orderings
    val current = model.currentOrdering
    val focusIndex = orderings.indexOfFirst { it.type == current?.type }.coerceAtLeast(0)
    val focus = remember { FocusRequester() }
    LaunchedEffect(Unit) { runCatching { focus.requestFocus() } }
    SidePanelDialog("Episode Order", model.target.name, onDismiss) {
        item {
            Text("Non-default orders pause watch-history sync for this show.",
                 fontSize = 14.sp, color = Color.White.copy(alpha = 0.6f), modifier = Modifier.padding(bottom = 8.dp))
        }
        itemsIndexed(orderings) { index, ordering ->
            val selected = ordering.type == current?.type
            ListItem(
                selected = selected,
                onClick = { onDismiss(); scope.launch { model.pickOrdering(ordering) } },
                headlineContent = { Text(ordering.label, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                trailingContent = if (selected) { { Icon(Icons.Filled.Check, contentDescription = "Selected") } } else null,
                modifier = if (index == focusIndex) Modifier.focusRequester(focus) else Modifier,
            )
        }
    }
}

private val SeriesOrdering.label: String get() = if (isOfficial) "$name (Default)" else name

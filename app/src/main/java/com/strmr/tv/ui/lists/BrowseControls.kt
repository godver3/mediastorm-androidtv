package com.strmr.tv.ui.lists

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.tv.material3.ExperimentalTvMaterial3Api
import androidx.tv.material3.FilterChip
import androidx.tv.material3.Icon
import androidx.tv.material3.OutlinedButton
import androidx.tv.material3.Text
import com.strmr.tv.home.BrowseKind
import com.strmr.tv.home.BrowseList
import com.strmr.tv.home.BrowseOptions
import com.strmr.tv.home.BrowseSort
import com.strmr.tv.home.RatingSource
import com.strmr.tv.home.WatchFilter
import com.strmr.tv.home.filters
import com.strmr.tv.home.sorts
import com.strmr.tv.ui.ChoiceDialog
import com.strmr.tv.ui.DialogChoice
import com.strmr.tv.ui.MultiPickerDialog
import com.strmr.tv.ui.PickerDialog

private enum class Panel { SORT, RATING, KIND, WATCH, GENRES, PART, MANAGE }

/**
 * Sort, filter and random-pick buttons over a list grid; each shows its current choice and
 * opens a side-panel picker (the tvOS menus' counterpart). Rename / Delete are the profile's
 * own lists only; `parts` switches a streaming service between its lists.
 */
@Composable
fun BrowseControls(
    list: BrowseList,
    options: BrowseOptions,
    onOptions: (BrowseOptions) -> Unit,
    genres: List<String>,
    parts: List<String>,
    part: Int,
    onPart: (Int) -> Unit,
    picking: Boolean,
    onRandom: (() -> Unit)?,
    onRename: (() -> Unit)?,
    onDelete: (() -> Unit)?,
) {
    var panel by remember { mutableStateOf<Panel?>(null) }
    Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
        if (parts.size > 1) Control(parts.getOrElse(part) { "" }) { panel = Panel.PART }
        if (list.sorts.isNotEmpty()) {
            val arrow = if (options.sort == BrowseSort.DEFAULT) "" else if (options.ascending) " ↑" else " ↓"
            Control("Sort: ${options.sort.label}$arrow") { panel = Panel.SORT }
            if (options.sort == BrowseSort.RATING && list.filters) Control(options.ratingSource.label) { panel = Panel.RATING }
        }
        if (list.filters) {
            Control(options.kind?.noun ?: "Movies & Shows") { panel = Panel.KIND }
            Control(options.watch.label) { panel = Panel.WATCH }
            if (genres.isNotEmpty()) {
                Control(when (options.genres.size) { 0 -> "Any Genre"; 1 -> options.genres.first(); else -> "${options.genres.size} Genres" }) {
                    panel = Panel.GENRES
                }
            }
        }
        onRandom?.let { random ->
            OutlinedButton(onClick = random) {
                if (picking) CircularProgressIndicator(Modifier.size(16.dp), color = Color.White, strokeWidth = 2.dp)
                else Icon(Icons.Filled.Refresh, contentDescription = null, modifier = Modifier.size(16.dp))
                Text("Random", Modifier.padding(start = 8.dp))
            }
        }
        if (onRename != null || onDelete != null) {
            OutlinedButton(onClick = { panel = Panel.MANAGE }) { Icon(Icons.Filled.MoreVert, contentDescription = "List Actions") }
        }
    }

    when (panel) {
        Panel.SORT -> PickerDialog("Sort By", list.sorts.map { it.label }, list.sorts.indexOf(options.sort),
            onPick = { onOptions(options.choose(list.sorts[it])) }, onDismiss = { panel = null })
        Panel.RATING -> PickerDialog("Rating Source", RatingSource.entries.map { it.label }, options.ratingSource.ordinal,
            onPick = { onOptions(options.copy(ratingSource = RatingSource.entries[it])) }, onDismiss = { panel = null })
        Panel.KIND -> {
            val kinds = listOf(null) + BrowseKind.entries
            PickerDialog("Show", kinds.map { it?.noun ?: "Movies & Shows" }, kinds.indexOf(options.kind),
                onPick = { onOptions(options.copy(kind = kinds[it])) }, onDismiss = { panel = null })
        }
        Panel.WATCH -> PickerDialog("Watch Status", WatchFilter.entries.map { it.label }, options.watch.ordinal,
            onPick = { onOptions(options.copy(watch = WatchFilter.entries[it])) }, onDismiss = { panel = null })
        Panel.GENRES -> MultiPickerDialog(
            "Genres", genres, genres.indices.filter { genres[it] in options.genres }.toSet(),
            onToggle = { i ->
                val g = genres[i]
                onOptions(options.copy(genres = if (g in options.genres) options.genres - g else options.genres + g))
            },
            onDismiss = { panel = null },
        )
        Panel.PART -> PickerDialog("List", parts, part, onPick = onPart, onDismiss = { panel = null })
        Panel.MANAGE -> ChoiceDialog(
            title = list.title,
            choices = listOfNotNull(
                onRename?.let { DialogChoice("Rename…", it) },
                onDelete?.let { DialogChoice("Delete List…", it) },
                DialogChoice("Cancel") {},
            ),
            onDismiss = { panel = null },
        )
        null -> Unit
    }
}

@Composable
private fun Control(label: String, onClick: () -> Unit) {
    OutlinedButton(onClick = onClick) { Text(label) }
}

/** The A–Z index of a list sorted by name: "All" and the first letters the list has. */
@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
fun LetterIndex(letters: List<String>, selected: String?, onSelect: (String?) -> Unit) {
    LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp), contentPadding = PaddingValues(vertical = 4.dp)) {
        item { FilterChip(selected = selected == null, onClick = { onSelect(null) }) { Text("All") } }
        items(letters) { letter ->
            FilterChip(selected = selected == letter, onClick = { onSelect(letter) }) { Text(letter) }
        }
    }
}

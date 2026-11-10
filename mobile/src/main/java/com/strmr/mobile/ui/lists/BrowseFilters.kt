package com.strmr.mobile.ui.lists

import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.Checkbox
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.strmr.tv.home.BrowseKind
import com.strmr.tv.home.BrowseList
import com.strmr.tv.home.BrowseOptions
import com.strmr.tv.home.BrowseSort
import com.strmr.tv.home.RatingSource
import com.strmr.tv.home.WatchFilter
import com.strmr.tv.home.filters
import com.strmr.tv.home.sorts
import com.strmr.tv.ui.lists.ListGridModel

/** Whether a list shows the filter chip row at all. */
fun BrowseList.hasChips(): Boolean =
    filters || (this as? BrowseList.Service)?.parts.orEmpty().size > 1

/**
 * The list's filters as chips, each opening a menu: a streaming service's lists, the rating
 * source under a Rating sort, then (the profile's own lists) Movies / Shows, watch status and
 * genres. The sort itself is in the top bar.
 */
@Composable
fun BrowseFilterChips(model: ListGridModel, modifier: Modifier = Modifier) {
    val list = model.list
    val options = model.options
    val set: (BrowseOptions) -> Unit = { model.options = it }
    Row(
        modifier.horizontalScroll(rememberScrollState()).padding(horizontal = 16.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        val parts = (list as? BrowseList.Service)?.parts.orEmpty()
        if (parts.size > 1) {
            MenuChip(parts.getOrElse(model.part) { "" }, selected = false, parts, model.part) { model.part = it }
        }
        if (options.sort == BrowseSort.RATING && list.filters) {
            val sources = RatingSource.entries
            MenuChip(options.ratingSource.label, selected = false, sources.map { it.label }, options.ratingSource.ordinal) {
                set(options.copy(ratingSource = sources[it]))
            }
        }
        if (list.filters) {
            val kinds = listOf(null) + BrowseKind.entries
            MenuChip(options.kind?.noun ?: "Movies & Shows", options.kind != null, kinds.map { it?.noun ?: "Movies & Shows" }, kinds.indexOf(options.kind)) {
                set(options.copy(kind = kinds[it]))
            }
            val watch = WatchFilter.entries
            MenuChip(options.watch.label, options.watch != WatchFilter.ALL, watch.map { it.label }, options.watch.ordinal) {
                set(options.copy(watch = watch[it]))
            }
            if (model.genres.isNotEmpty()) GenreChip(model.genres, options.genres) { set(options.copy(genres = it)) }
        }
    }
}

/** A chip naming the current choice, opening a menu of [choices]. */
@Composable
private fun MenuChip(label: String, selected: Boolean, choices: List<String>, current: Int, onPick: (Int) -> Unit) {
    var open by remember { mutableStateOf(false) }
    Box {
        FilterChip(
            selected = selected, onClick = { open = true }, label = { Text(label) },
            trailingIcon = { Icon(Icons.Filled.ArrowDropDown, null) },
        )
        DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            choices.forEachIndexed { i, choice ->
                DropdownMenuItem(
                    text = { Text(choice) },
                    onClick = { open = false; onPick(i) },
                    leadingIcon = { if (i == current) Icon(Icons.Filled.Check, null) },
                )
            }
        }
    }
}

/** Genres, several at once, on a bottom sheet. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun GenreChip(genres: List<String>, chosen: Set<String>, onChange: (Set<String>) -> Unit) {
    var open by remember { mutableStateOf(false) }
    val label = when (chosen.size) { 0 -> "Any Genre"; 1 -> chosen.first(); else -> "${chosen.size} Genres" }
    FilterChip(
        selected = chosen.isNotEmpty(), onClick = { open = true }, label = { Text(label) },
        trailingIcon = { Icon(Icons.Filled.ArrowDropDown, null) },
    )
    if (!open) return
    ModalBottomSheet(onDismissRequest = { open = false }) {
        Row(Modifier.padding(start = 24.dp, end = 12.dp), verticalAlignment = Alignment.CenterVertically) {
            Text("Genres", Modifier.weight(1f), style = MaterialTheme.typography.titleLarge)
            if (chosen.isNotEmpty()) TextButton(onClick = { onChange(emptySet()) }) { Text("Clear") }
        }
        LazyColumn(Modifier.navigationBarsPadding(), contentPadding = PaddingValues(bottom = 16.dp)) {
            items(genres, key = { it }) { genre ->
                val on = genre in chosen
                ListItem(
                    headlineContent = { Text(genre) },
                    trailingContent = { Checkbox(checked = on, onCheckedChange = null) },
                    modifier = Modifier.clickable { onChange(if (on) chosen - genre else chosen + genre) },
                )
            }
        }
    }
}

/** The A–Z index of a list sorted by name: All and the first letters the list has. */
@Composable
fun LetterStrip(modifier: Modifier, letters: List<String>, selected: String?, onSelect: (String?) -> Unit) {
    LazyRow(modifier, contentPadding = PaddingValues(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        item { FilterChip(selected = selected == null, onClick = { onSelect(null) }, label = { Text("All") }) }
        items(letters, key = { it }) { letter ->
            FilterChip(selected = selected == letter, onClick = { onSelect(letter) }, label = { Text(letter) })
        }
    }
}

/** The sort menu's entries: the current sort checked, with its direction. */
@Composable
fun SortMenuItems(list: BrowseList, options: BrowseOptions, onPick: (BrowseSort) -> Unit) {
    list.sorts.forEach { sort ->
        val current = options.sort == sort
        val arrow = if (!current || sort == BrowseSort.DEFAULT) "" else if (options.ascending) "  ↑" else "  ↓"
        DropdownMenuItem(
            text = { Text(sort.label + arrow) },
            onClick = { onPick(sort) },
            leadingIcon = { if (current) Icon(Icons.Filled.Check, null) },
        )
    }
}

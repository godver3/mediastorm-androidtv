package com.strmr.tv.player

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.key
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

/**
 * Switch Source: the title's ranked sources, as the source picker searches
 * them (`/api/indexers/search`, with debrid cache / usenet health), the playing one checked.
 * Choosing another closes the player and resumes it there at the current position; Back closes
 * the list. Nothing switches by itself.
 */
@Composable
fun SourceSwitchMenu(controller: PlayerController, onDone: () -> Unit, modifier: Modifier = Modifier) {
    val candidates = remember { SourceSwitchCandidates(controller) }
    LaunchedEffect(Unit) { candidates.load() }
    val choices = candidates.choices(onDone)
    // Re-keyed once loaded, so the menu focuses the playing source then.
    key(candidates.results != null) { ChoiceMenu("Switch Source", choices, candidates.emptyText, modifier, width = 760.dp) }
}

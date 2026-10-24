package com.strmr.tv.ui.sports

import androidx.compose.foundation.focusGroup
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.Settings
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.focusRestorer
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.tv.material3.ExperimentalTvMaterial3Api
import androidx.tv.material3.FilterChip
import androidx.tv.material3.Icon
import androidx.tv.material3.OutlinedButton
import androidx.tv.material3.Text
import com.strmr.tv.sports.SportsCycling
import com.strmr.tv.sports.SportsHubFilters
import com.strmr.tv.ui.ChoiceDialog
import com.strmr.tv.ui.DialogChoice
import com.strmr.tv.ui.Navigator
import com.strmr.tv.ui.Page
import com.strmr.tv.ui.PageMargin
import com.strmr.tv.ui.PickerDialog
import com.strmr.tv.ui.sports.game.SportsChipRow

/**
 * The hub's controls (Expo `SportsHubTV`'s TV rail and Filters panel, as native chips): All /
 * Live / Upcoming / Results, the day, the Sport and League pickers (followed and pinned first),
 * Following, Hide Scores and Customize. The row scrolls sideways when it outgrows the screen.
 */
@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
fun SportsHubControls(state: SportsHubState, navigator: Navigator) {
    var picking by remember { mutableStateOf<Picker?>(null) }
    SportsChipRow(null, Modifier.padding(horizontal = PageMargin).focusRestorer(), scrolls = true) {
        SportsFilter.entries.forEach { f ->
            FilterChip(selected = state.filter == f, onClick = { state.filter = f }) { Text(f.label) }
        }
        if (state.dated) {
            FilterChip(selected = state.day != SportsDay.TODAY, onClick = { picking = Picker.DAY }) { Text(state.day.label) }
        }
        FilterChip(selected = state.sport != SportsHubFilters.ALL, onClick = { picking = Picker.SPORT }) {
            Text(SportsHubFilters.sportLabel(state.sport))
            Icon(Icons.Filled.ArrowDropDown, contentDescription = null, modifier = Modifier.size(18.dp))
        }
        FilterChip(selected = state.league != SportsHubFilters.ALL, onClick = { picking = Picker.LEAGUE }) {
            Text(leagueLabel(state), maxLines = 1, overflow = TextOverflow.Ellipsis)
            Icon(Icons.Filled.ArrowDropDown, contentDescription = null, modifier = Modifier.size(18.dp))
        }
        FilterChip(selected = state.followingOnly, onClick = { state.followingOnly = !state.followingOnly }) { Text("Following") }
        FilterChip(selected = SportsSpoilers.hidden, onClick = { SportsSpoilers.toggle() }) { Text("Hide Scores") }
        FilterChip(selected = false, onClick = { navigator.open(Page.SportsPreferences) }) {
            Icon(Icons.Filled.Settings, contentDescription = "Customize Sports", modifier = Modifier.size(18.dp))
        }
    }

    when (picking) {
        Picker.DAY -> ChoiceDialog(
            title = "Day",
            choices = SportsDay.entries.map { d -> DialogChoice(if (d == state.day) "✓  ${d.label}" else d.label) { state.day = d } },
            onDismiss = { picking = null },
        )
        Picker.SPORT -> {
            val ids = listOf(SportsHubFilters.ALL) + state.sportOptions.map { it.id }
            PickerDialog(
                title = "Sport", options = ids.map(SportsHubFilters::sportLabel), selected = ids.indexOf(state.sport).coerceAtLeast(0),
                onPick = { state.chooseSport(ids[it]) }, onDismiss = { picking = null },
            )
        }
        Picker.LEAGUE -> {
            val options = state.leagueOptions
            val ids = listOf(SportsHubFilters.ALL) + options.map { it.id }
            PickerDialog(
                title = "League", options = listOf("All Leagues") + options.map { it.label },
                selected = ids.indexOf(state.league).coerceAtLeast(0),
                onPick = { state.league = ids[it] }, onDismiss = { picking = null },
            )
        }
        null -> Unit
    }
}

private enum class Picker { DAY, SPORT, LEAGUE }

private fun leagueLabel(state: SportsHubState): String =
    if (state.league == SportsHubFilters.ALL) "All Leagues"
    else state.leagueOptions.firstOrNull { it.id == state.league }?.label ?: state.leagueName(state.league)

/**
 * Under the controls while a sport or league is chosen (Expo TV's filter summary): what the rows
 * show, Competition Details (a league's standings and games, the race weekends, the cycling
 * calendar) and Clear Filters.
 */
@Composable
fun SportsFilterSummary(state: SportsHubState, navigator: Navigator) {
    if (state.sport == SportsHubFilters.ALL && state.league == SportsHubFilters.ALL) return
    val summary = listOfNotNull(
        SportsHubFilters.sportLabel(state.sport).takeIf { state.sport != SportsHubFilters.ALL },
        leagueLabel(state).takeIf { state.league != SportsHubFilters.ALL },
        "Following only".takeIf { state.followingOnly },
        "Scores hidden".takeIf { SportsSpoilers.hidden },
    ).joinToString(" · ")
    // Coming in from above or below, the remote lands on the row's first button, not the nearest.
    val first = remember { FocusRequester() }
    Row(
        Modifier.padding(horizontal = PageMargin).focusProperties { onEnter = { runCatching { first.requestFocus() } } }.focusGroup(),
        horizontalArrangement = Arrangement.spacedBy(18.dp), verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(summary, fontSize = 14.sp, color = SportsMuted, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f, fill = false))
        if (state.hasDetails) {
            OutlinedButton(onClick = { navigator.open(Page.SportsCompetition(state.sport, state.league)) }, modifier = Modifier.focusRequester(first)) {
                Text(if (state.sport == SportsCycling.SPORT && state.league == SportsHubFilters.ALL) "Browse All Cycling Dates" else "Competition Details")
            }
        }
        OutlinedButton(onClick = state::clearFilters, modifier = if (state.hasDetails) Modifier else Modifier.focusRequester(first)) { Text("Clear Filters") }
    }
}

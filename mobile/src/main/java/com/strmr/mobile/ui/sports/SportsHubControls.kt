package com.strmr.mobile.ui.sports

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.strmr.mobile.ui.shell.Navigator
import com.strmr.mobile.ui.shell.Route
import com.strmr.tv.sports.SportsCycling
import com.strmr.tv.sports.SportsHubFilters
import com.strmr.tv.ui.sports.SportsDay
import com.strmr.tv.ui.sports.SportsFilter
import com.strmr.tv.ui.sports.SportsHubState
import com.strmr.tv.ui.sports.SportsSpoilers
import java.time.Duration
import java.time.Instant

/**
 * The hub's filters (the iPhone's): All / Live / Upcoming / Results as a segmented button, then
 * a sideways row of chips: the day (when the server answers other days), the sport and the
 * competition as menus (pinned sports and followed competitions first), Following and My Teams.
 */
@Composable
fun SportsHubControls(state: SportsHubState, navigator: Navigator) {
    Column(Modifier.fillMaxWidth().padding(top = 4.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
            SportsFilter.entries.forEachIndexed { i, f ->
                SegmentedButton(selected = state.filter == f, onClick = { state.filter = f },
                                shape = SegmentedButtonDefaults.itemShape(i, SportsFilter.entries.size)) { Text(f.label, maxLines = 1) }
            }
        }
        Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            if (state.dated) {
                MenuChip(state.day.label, state.day != SportsDay.TODAY, SportsDay.entries.map { it.label }, SportsDay.entries.indexOf(state.day)) {
                    state.day = SportsDay.entries[it]
                }
            }
            val sportIds = listOf(SportsHubFilters.ALL) + state.sportOptions.map { it.id }
            MenuChip(SportsHubFilters.sportLabel(state.sport), state.sport != SportsHubFilters.ALL,
                     sportIds.map(SportsHubFilters::sportLabel), sportIds.indexOf(state.sport)) { state.chooseSport(sportIds[it]) }
            val leagueIds = listOf(SportsHubFilters.ALL) + state.leagueOptions.map { it.id }
            MenuChip(leagueLabel(state), state.league != SportsHubFilters.ALL,
                     listOf("All Leagues") + state.leagueOptions.map { it.label }, leagueIds.indexOf(state.league)) { state.league = leagueIds[it] }
            FilterChip(selected = state.followingOnly, onClick = { state.followingOnly = !state.followingOnly }, label = { Text("Following") })
            FilterChip(selected = state.myTeamsOnly, onClick = { state.myTeamsOnly = !state.myTeamsOnly }, label = { Text("My Teams") })
        }
    }
}

/** A chip that opens a menu of [options], the [selected] one checked. */
@Composable
private fun MenuChip(label: String, on: Boolean, options: List<String>, selected: Int, onPick: (Int) -> Unit) {
    var open by remember { mutableStateOf(false) }
    Box {
        FilterChip(
            selected = on, onClick = { open = true },
            label = { Text(label, maxLines = 1, overflow = TextOverflow.Ellipsis) },
            trailingIcon = { Icon(Icons.Filled.ArrowDropDown, contentDescription = null, modifier = Modifier.size(18.dp)) },
        )
        DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            options.forEachIndexed { i, option ->
                DropdownMenuItem(
                    text = { Text(option) },
                    leadingIcon = { if (i == selected) Icon(Icons.Filled.Check, null) },
                    onClick = { open = false; onPick(i) },
                )
            }
        }
    }
}

/** The top bar's menu: Hide Scores, Customize Sports and when the scores were updated. */
@Composable
fun SportsHubMenu(state: SportsHubState, navigator: Navigator) {
    var open by remember { mutableStateOf(false) }
    IconButton(onClick = { open = true }) {
        Icon(if (SportsSpoilers.hidden) Icons.Filled.VisibilityOff else Icons.Filled.MoreVert, contentDescription = "Options")
    }
    DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
        DropdownMenuItem(
            text = { Text("Hide Scores") },
            trailingIcon = { Checkbox(checked = SportsSpoilers.hidden, onCheckedChange = null) },
            onClick = { SportsSpoilers.toggle() },
        )
        DropdownMenuItem(
            text = { Text("Customize Sports") },
            leadingIcon = { Icon(Icons.Filled.Tune, null) },
            onClick = { open = false; navigator.push(Route.SportsPreferences) },
        )
        state.updatedAt?.let {
            HorizontalDivider()
            Text(freshness(it), style = MaterialTheme.typography.labelMedium, color = SportsMuted,
                 modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp))
        }
    }
}

/**
 * Under the controls while a sport or league is chosen: what the cards show, Competition
 * Details (a league's standings and games, the race weekends, the cycling calendar) and Clear
 * Filters.
 */
@Composable
fun SportsFilterSummary(state: SportsHubState, navigator: Navigator) {
    if (state.sport == SportsHubFilters.ALL && state.league == SportsHubFilters.ALL) return
    val summary = listOfNotNull(
        SportsHubFilters.sportLabel(state.sport).takeIf { state.sport != SportsHubFilters.ALL },
        leagueLabel(state).takeIf { state.league != SportsHubFilters.ALL },
        "Following only".takeIf { state.followingOnly },
        "My Teams only".takeIf { state.myTeamsOnly },
        "Scores hidden".takeIf { SportsSpoilers.hidden },
    ).joinToString(" · ")
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(summary, style = MaterialTheme.typography.bodySmall, color = SportsMuted)
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            if (state.hasDetails) {
                Button(onClick = { navigator.push(Route.SportsCompetition(state.sport, state.league)) }) {
                    Text(if (state.sport == SportsCycling.SPORT && state.league == SportsHubFilters.ALL) "Browse All Cycling Dates" else "Competition Details")
                }
            }
            OutlinedButton(onClick = state::clearFilters) { Text("Clear Filters") }
        }
    }
}

private fun leagueLabel(state: SportsHubState): String =
    if (state.league == SportsHubFilters.ALL) "All Leagues"
    else state.leagueOptions.firstOrNull { it.id == state.league }?.label ?: state.leagueName(state.league)

private fun freshness(updated: Instant): String {
    val minutes = Duration.between(updated, Instant.now()).toMinutes()
    return if (minutes < 1) "Updated just now" else "Updated $minutes min ago"
}

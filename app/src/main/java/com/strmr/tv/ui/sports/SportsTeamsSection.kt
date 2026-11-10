package com.strmr.tv.ui.sports

import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.items
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.unit.dp
import androidx.tv.material3.ListItem
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import com.strmr.tv.api.ApiClient
import com.strmr.tv.sports.SportsHubFilters
import com.strmr.tv.sports.SportsLeague
import com.strmr.tv.sports.SportsLeagueTable
import com.strmr.tv.sports.SportsPreferencesEnvelope
import com.strmr.tv.sports.SportsTableRow
import com.strmr.tv.sports.SportsTeamFollows
import com.strmr.tv.sports.sportsStandings
import com.strmr.tv.ui.MessageDialog
import com.strmr.tv.ui.MultiPickerDialog
import com.strmr.tv.ui.PickerDialog
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch

/** The section's rows, under Competitions. */
/** [first] takes the page's initial focus: the first followed team, else Add Team. */
fun LazyListScope.sportsTeamsSection(state: SportsTeamsSectionState, teamIds: List<String>, enabled: Boolean, first: FocusRequester? = null) {
    item(key = "teams") {
        Text("Teams", style = MaterialTheme.typography.titleSmall, color = SportsMuted, modifier = Modifier.padding(top = 14.dp, start = 4.dp))
    }
    items(teamIds, key = { "team:$it" }) { id ->
        val name = SportsTeamFollows.name(id, state.tables, SportsHub.state.games)
        val league = SportsTeamFollows.parse(id)?.first
        ListItem(
            selected = false,
            enabled = enabled,
            onClick = {
                league?.let { l ->
                    runCatching { state.addTeam.requestFocus() }
                    state.toggle(l, id.removePrefix("$l:"), name, follow = false)
                }
            },
            headlineContent = { Text(name) },
            supportingContent = league?.let { { Text(SportsHub.state.leagueName(it), color = SportsMuted) } },
            trailingContent = { Text("Unfollow", color = SportsMuted) },
            modifier = if (first != null && id == teamIds.first()) Modifier.focusRequester(first) else Modifier,
        )
    }
    item(key = "teams:add") {
        val loading = state.loadingLeague
        ListItem(
            selected = false,
            enabled = enabled && loading == null,
            onClick = { state.choosingLeague = true },
            headlineContent = { Text("Add Team") },
            modifier = Modifier.focusRequester(state.addTeam)
                .then(if (first != null && teamIds.isEmpty()) Modifier.focusRequester(first) else Modifier),
            trailingContent = loading?.let { { Text("Loading ${it.label}…", color = SportsMuted) } },
        )
    }
}

/** Add Team's dialogs (the league, then its teams, or why there are none); reads the followed teams' names. */
@Composable
fun SportsTeamsDialogs(state: SportsTeamsSectionState, teamIds: List<String>) {
    val hub = SportsHub.state
    val scope = rememberCoroutineScope()
    LaunchedEffect(teamIds) { state.loadNames(teamIds) }
    if (state.choosingLeague) {
        val leagues = remember(hub.leagues, hub.preferences) { teamLeagues(hub) }
        PickerDialog(
            title = "League", options = leagues.map { it.label }, selected = -1,
            onPick = { scope.launch { state.open(leagues[it]) } }, onDismiss = { state.choosingLeague = false },
        )
    }
    state.teams?.let { (league, rows) ->
        val followed = rows.indices.filter { SportsTeamFollows.isFollowed(teamIds, league.id, rows[it].id) }.toSet()
        MultiPickerDialog(
            title = league.label, options = rows.map { it.name }, selected = followed,
            onToggle = { i ->
                state.toggle(league.id, rows[i].id, rows[i].name, follow = i !in followed)
            },
            onDismiss = { state.teams = null },
        )
    }
    state.unavailable?.let { league ->
        MessageDialog(league.label, "Teams for this league aren't available yet — follow one from a game page.") { state.unavailable = null }
    }
}

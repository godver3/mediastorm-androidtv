package com.strmr.mobile.ui.sports

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.strmr.tv.sports.SportsTeamFollows
import com.strmr.tv.ui.sports.SportsHub
import com.strmr.tv.ui.sports.SportsTeamsSectionState
import com.strmr.tv.ui.sports.teamLeagues
import kotlinx.coroutines.launch

/** Customize's Teams: each followed team with Unfollow, then Add Team (a league, then its teams). */
fun LazyListScope.sportsTeamsSection(state: SportsTeamsSectionState, teamIds: List<String>, enabled: Boolean) {
    item(key = "teams") { Heading("Teams") }
    items(teamIds, key = { "team:$it" }) { id ->
        val name = SportsTeamFollows.name(id, state.tables, SportsHub.state.games)
        val league = SportsTeamFollows.parse(id)?.first
        ListItem(
            headlineContent = { Text(name) },
            supportingContent = league?.let { { Text(SportsHub.state.leagueName(it)) } },
            trailingContent = {
                TextButton(enabled = enabled && league != null, onClick = {
                    league?.let { l -> state.toggle(l, id.removePrefix("$l:"), name, follow = false) }
                }) { Text("Unfollow") }
            },
        )
    }
    item(key = "teams:add") {
        val loading = state.loadingLeague
        ListItem(
            headlineContent = { Text("Add Team") },
            leadingContent = { Icon(Icons.Filled.Add, null) },
            supportingContent = loading?.let { { Text("Loading ${it.label}…") } },
            modifier = Modifier.clickable(enabled = enabled && loading == null) { state.choosingLeague = true },
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
        AlertDialog(
            onDismissRequest = { state.teams = null },
            title = { Text(league.label) },
            text = {
                LazyColumn(Modifier.fillMaxWidth().heightIn(max = 460.dp)) {
                    itemsIndexed(rows, key = { _, r -> r.id }) { _, row ->
                        val followed = SportsTeamFollows.isFollowed(teamIds, league.id, row.id)
                        ListItem(
                            headlineContent = { Text(row.name) },
                            leadingContent = { Checkbox(checked = followed, onCheckedChange = null) },
                            colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                            modifier = Modifier.clickable { state.toggle(league.id, row.id, row.name, follow = !followed) },
                        )
                    }
                }
            },
            confirmButton = { TextButton(onClick = { state.teams = null }) { Text("Done") } },
        )
    }
    state.unavailable?.let { league ->
        AlertDialog(
            onDismissRequest = { state.unavailable = null },
            title = { Text(league.label) },
            text = { Text("Teams for this league aren't available yet — follow one from a game page.") },
            confirmButton = { TextButton(onClick = { state.unavailable = null }) { Text("OK") } },
        )
    }
}

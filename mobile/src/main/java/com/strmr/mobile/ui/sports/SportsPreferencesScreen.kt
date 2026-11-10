package com.strmr.mobile.ui.sports

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.strmr.mobile.ui.shell.PageScaffold
import com.strmr.tv.api.ApiClient
import com.strmr.tv.api.ApiError
import com.strmr.tv.sports.SportsLeague
import com.strmr.tv.sports.SportsPreferencesEnvelope
import com.strmr.tv.sports.saveSportsPreferences
import com.strmr.tv.sports.sportsPreferences
import com.strmr.tv.ui.sports.SportsHub
import com.strmr.tv.ui.sports.SportsTeamsSectionState
import kotlinx.coroutines.launch

/**
 * Customize Sports: follow teams, then competitions grouped by sport, each a switch. Followed
 * competitions sort first on the hub and fill its Following filter; followed teams fill My
 * Teams. Each change saves at once to the profile's sports preferences (shared with the other
 * apps); ids this page doesn't know (schools, cycling races) are kept as they are.
 */
@Composable
fun SportsPreferencesScreen() {
    val hub = SportsHub.state
    val scope = rememberCoroutineScope()
    var envelope by remember { mutableStateOf<SportsPreferencesEnvelope?>(null) }
    var loading by remember { mutableStateOf(true) }
    var saving by remember { mutableStateOf(false) }
    var message by remember { mutableStateOf<String?>(null) }
    val teams = remember { SportsTeamsSectionState { envelope = it } }
    val bySport = hub.usableLeagues.groupBy { it.sport ?: "other" }.toSortedMap()

    suspend fun reload() {
        envelope = runCatching { ApiClient.sportsPreferences() }.getOrNull()
        envelope?.let { hub.preferences = it }
        loading = false
    }
    LaunchedEffect(Unit) { reload() }

    fun toggle(league: SportsLeague) {
        val current = envelope ?: return
        val id = league.followId
        val on = id !in current.preferences.competitionIds
        val ids = current.preferences.competitionIds.filter { it != id } + if (on) listOf(id) else emptyList()
        saving = true
        scope.launch {
            try {
                val saved = ApiClient.saveSportsPreferences(current.copy(preferences = current.preferences.copy(competitionIds = ids)))
                envelope = saved
                hub.preferences = saved
                message = null
            } catch (e: ApiError) {
                if (e.statusCode == 409) {
                    message = "Another device changed these. Reloaded the saved list."
                    reload()
                } else message = e.message
            } catch (e: Exception) {
                message = e.message
            } finally {
                saving = false
            }
        }
    }

    PageScaffold("Customize Sports", showBack = true) { padding ->
        LazyColumn(Modifier.fillMaxSize().padding(padding), contentPadding = PaddingValues(bottom = 32.dp)) {
            message?.let { item { Text(it, color = SportsMuted, modifier = Modifier.padding(16.dp)) } }
            val current = envelope
            when {
                loading -> item { CircularProgressIndicator(Modifier.padding(24.dp)) }
                current == null -> item { Text("This server doesn't save sports preferences.", color = SportsMuted, modifier = Modifier.padding(16.dp)) }
                else -> {
                    sportsTeamsSection(teams, hub.teamIds, enabled = !saving)
                    bySport.entries.forEach { (sport, leagues) ->
                        item(key = "sport:$sport") { Heading(sportName(sport)) }
                        items(leagues, key = { it.id }) { league ->
                            val followed = league.followId in current.preferences.competitionIds
                            ListItem(
                                headlineContent = { Text(league.label) },
                                trailingContent = { Switch(followed, { toggle(league) }, enabled = !saving) },
                            )
                        }
                    }
                }
            }
        }
    }
    if (envelope != null) SportsTeamsDialogs(teams, hub.teamIds)
}

@Composable
internal fun Heading(text: String) {
    Text(text, style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.primary,
         modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 20.dp, bottom = 4.dp))
}

/** "mma" → "MMA", "rugby-league" → "Rugby League". */
internal fun sportName(id: String): String =
    if (id.length <= 3) id.uppercase()
    else id.split('-').joinToString(" ") { part -> part.replaceFirstChar { it.uppercase() } }

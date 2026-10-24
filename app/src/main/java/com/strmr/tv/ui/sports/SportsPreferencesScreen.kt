package com.strmr.tv.ui.sports

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.tv.material3.ListItem
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import com.strmr.tv.api.ApiClient
import com.strmr.tv.api.ApiError
import com.strmr.tv.sports.SportsLeague
import com.strmr.tv.sports.SportsPreferencesEnvelope
import com.strmr.tv.sports.saveSportsPreferences
import com.strmr.tv.sports.sportsPreferences
import com.strmr.tv.ui.pageBackground
import kotlinx.coroutines.launch

/**
 * Follow competitions, grouped by sport. Followed competitions sort first on the hub and fill its
 * Following filter. Each change saves at once to the profile's sports preferences, shared with
 * the Expo app; ids this page doesn't know (teams, schools, cycling races) are kept as they are.
 */
@Composable
fun SportsPreferencesScreen() {
    val hub = SportsHub.state
    val scope = rememberCoroutineScope()
    var envelope by remember { mutableStateOf<SportsPreferencesEnvelope?>(null) }
    var loading by remember { mutableStateOf(true) }
    var saving by remember { mutableStateOf(false) }
    var message by remember { mutableStateOf<String?>(null) }
    val first = remember { FocusRequester() }
    val bySport = hub.usableLeagues.groupBy { it.sport ?: "other" }.toSortedMap()

    suspend fun reload() {
        envelope = runCatching { ApiClient.sportsPreferences() }.getOrNull()
        loading = false
    }
    LaunchedEffect(Unit) { reload() }
    LaunchedEffect(loading) { if (!loading) runCatching { first.requestFocus() } }

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

    Box(Modifier.fillMaxSize().pageBackground(), contentAlignment = Alignment.TopCenter) {
        LazyColumn(
            Modifier.widthIn(max = 640.dp),
            contentPadding = PaddingValues(horizontal = 24.dp, vertical = 36.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            item { Text("Follow Competitions", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.SemiBold) }
            message?.let { item { Text(it, color = SportsMuted) } }
            val current = envelope
            when {
                loading -> Unit
                current == null -> item { Text("This server doesn't save sports preferences.", color = SportsMuted) }
                else -> bySport.entries.forEachIndexed { groupIndex, (sport, leagues) ->
                    item(key = "sport:$sport") {
                        Text(sportName(sport), style = MaterialTheme.typography.titleSmall, color = SportsMuted,
                             modifier = Modifier.padding(top = 14.dp, start = 4.dp))
                    }
                    items(leagues, key = { it.id }) { league ->
                        val followed = league.followId in current.preferences.competitionIds
                        ListItem(
                            selected = false,
                            enabled = !saving,
                            onClick = { toggle(league) },
                            headlineContent = { Text(league.label) },
                            trailingContent = { Text(if (followed) "On" else "Off", color = SportsMuted) },
                            modifier = if (groupIndex == 0 && league == leagues.first()) Modifier.focusRequester(first) else Modifier,
                        )
                    }
                }
            }
        }
    }
}

/** "mma" → "MMA", "rugby-league" → "Rugby League". */
private fun sportName(id: String): String =
    if (id.length <= 3) id.uppercase()
    else id.split('-').joinToString(" ") { part -> part.replaceFirstChar { it.uppercase() } }

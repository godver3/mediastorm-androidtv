package com.strmr.tv.ui.sports

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.focus.FocusRequester
import com.strmr.tv.api.ApiClient
import com.strmr.tv.sports.SportsHubFilters
import com.strmr.tv.sports.SportsLeague
import com.strmr.tv.sports.SportsLeagueTable
import com.strmr.tv.sports.SportsPreferencesEnvelope
import com.strmr.tv.sports.SportsTableRow
import com.strmr.tv.sports.SportsTeamFollows
import com.strmr.tv.sports.sportsStandings
import kotlinx.coroutines.CancellationException

/**
 * Customize's Teams: the followed teams, each with Unfollow, then Add Team (a league, then its
 * teams from the league's standings, each toggling a follow). Changes save at once through
 * [SportsTeamFollowing].
 */
class SportsTeamsSectionState(private val onSaved: (SportsPreferencesEnvelope) -> Unit) {
    /** League tables for team names, by league (the hub's, plus any read here). */
    var tables by mutableStateOf<Map<String, List<SportsLeagueTable>>>(emptyMap())
    var choosingLeague by mutableStateOf(false)
    /** The league whose teams are being read (Add Team's row says so). */
    var loadingLeague by mutableStateOf<SportsLeague?>(null)
    var teams by mutableStateOf<Pair<SportsLeague, List<SportsTableRow>>?>(null)
    var unavailable by mutableStateOf<SportsLeague?>(null)
    /** Add Team, where focus goes when the focused team is unfollowed (its row leaves the list). */
    val addTeam = FocusRequester()

    /** The tables of the followed teams' leagues the hub hasn't read, for their names. */
    suspend fun loadNames(teamIds: List<String>) {
        tables = SportsHub.state.tables + tables
        val leagues = teamIds.mapNotNull { SportsTeamFollows.parse(it)?.first }.distinct().filter { it !in tables }
        for (league in leagues) {
            val groups = read(league) ?: continue
            tables = tables + (league to groups)
        }
    }

    suspend fun open(league: SportsLeague) {
        loadingLeague = league
        val groups = read(league.id)
        loadingLeague = null
        val rows = groups?.let { SportsTeamFollows.teams(it) }.orEmpty()
        if (groups != null) tables = tables + (league.id to groups)
        if (rows.isEmpty()) unavailable = league else teams = league to rows
    }

    /** Follows or unfollows; the page takes the saved preferences as its own. */
    fun toggle(league: String, teamId: String, name: String, follow: Boolean) =
        SportsTeamFollowing.toggle(league, teamId, name, follow, onSaved)

    private suspend fun read(league: String): List<SportsLeagueTable>? = try {
        ApiClient.sportsStandings(league).groups.ifEmpty { null }
    } catch (e: CancellationException) {
        throw e
    } catch (_: Exception) {
        null
    }
}

/** The catalog's team-sport leagues turned on, followed competitions and pinned sports first. */
fun teamLeagues(hub: SportsHubState): List<SportsLeague> {
    val prefs = hub.preferences?.preferences
    val followed = prefs?.competitionIds.orEmpty()
    val pinned = prefs?.pinnedSportIds.orEmpty()
    return hub.usableLeagues.filter(SportsTeamFollows::isTeamLeague).sortedBy { SportsHubFilters.rank(it, followed, pinned) }
}

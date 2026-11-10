package com.strmr.tv.ui.sports.standings

import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.strmr.tv.api.ApiClient
import com.strmr.tv.sports.SportsLeagueStandings
import com.strmr.tv.sports.SportsLeagueTable
import com.strmr.tv.sports.sportsStandings
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay

/**
 * A league's standings while they show (Expo `SportsLeagueStandings` state): the provider's
 * answer, reloaded every 5 min and kept through failures, the chosen table and the row showing
 * every statistic.
 */
@Stable
class SportsStandingsState(val league: String) {
    var standings by mutableStateOf<SportsLeagueStandings?>(null); private set
    var failed by mutableStateOf(false); private set
    var groupId by mutableStateOf<String?>(null)
    /** The row showing every column. */
    var expandedId by mutableStateOf<String?>(null)
    var retries by mutableIntStateOf(0); private set

    /** The chosen table, else the first. */
    val table: SportsLeagueTable?
        get() = standings?.groups?.let { groups -> groups.firstOrNull { it.id == groupId } ?: groups.firstOrNull() }

    fun choose(id: String) {
        groupId = id
        expandedId = null
    }

    fun retry() {
        retries++
    }

    suspend fun poll() {
        while (true) {
            try {
                standings = ApiClient.sportsStandings(league)
                failed = false
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                failed = true
            }
            delay(300_000)
        }
    }
}

package com.strmr.tv.ui.sports

import com.strmr.tv.api.ApiClient
import com.strmr.tv.api.ApiError
import com.strmr.tv.sports.SportsPreferencesEnvelope
import com.strmr.tv.sports.SportsTeamFollows
import com.strmr.tv.sports.saveSportsPreferences
import com.strmr.tv.sports.sportsPreferences
import com.strmr.tv.ui.AppToast
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * Follows or unfollows a team in the profile's sports preferences, shown at once: the hub's
 * copy changes first (so its filters and every Follow button follow), then the change saves over
 * the revision read, one save at a time and outliving the page that asked. A 409 (another device
 * saved in between) re-reads the saved preferences and applies the change to them once more; any
 * other failure takes the change back out of the hub's copy and says so briefly. Every other
 * field is sent as it was read.
 */
object SportsTeamFollowing {
    private val scope = MainScope()
    private val saving = Mutex()
    /** Changes shown but not saved yet, kept on top of each saved answer. */
    private val pending = mutableListOf<Change>()

    private data class Change(val league: String, val teamId: String, val follow: Boolean)

    fun toggle(league: String, teamId: String, name: String, follow: Boolean, onSaved: (SportsPreferencesEnvelope) -> Unit = {}) {
        val hub = SportsHub.state
        val change = Change(league, teamId, follow)
        pending += change
        hub.preferences = hub.preferences?.let { apply(it, league, teamId, follow) }
        scope.launch {
            try {
                val saved = save(league, teamId, follow)
                pending -= change
                hub.preferences = pending.fold(saved) { e, c -> apply(e, c.league, c.teamId, c.follow) }
                onSaved(saved)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                pending -= change
                hub.preferences = hub.preferences?.let { apply(it, league, teamId, !follow) }
                AppToast.show("Couldn't ${if (follow) "follow" else "unfollow"} $name.")
            }
        }
    }

    private suspend fun save(league: String, teamId: String, follow: Boolean): SportsPreferencesEnvelope = saving.withLock {
        val hub = SportsHub.state
        val base = hub.preferences ?: ApiClient.sportsPreferences() ?: throw ApiError.NotReady("No sports preferences")
        try {
            ApiClient.saveSportsPreferences(apply(base, league, teamId, follow))
        } catch (e: ApiError) {
            if (e.statusCode != 409) throw e
            val fresh = ApiClient.sportsPreferences() ?: throw e
            ApiClient.saveSportsPreferences(apply(fresh, league, teamId, follow))
        }
    }

    private fun apply(envelope: SportsPreferencesEnvelope, league: String, teamId: String, follow: Boolean) =
        envelope.copy(preferences = SportsTeamFollows.update(envelope.preferences, league, teamId, follow))
}

package com.strmr.tv.launcher

import android.content.Context
import android.util.Log
import androidx.compose.runtime.snapshotFlow
import com.strmr.tv.api.ApiClient
import com.strmr.tv.api.ContinueWatchingItem
import com.strmr.tv.app.App
import com.strmr.tv.app.AppLog
import com.strmr.tv.app.AppSession
import com.strmr.tv.home.continueWatching
import com.strmr.tv.library.TitleActions
import com.strmr.tv.library.WatchlistStore
import com.strmr.tv.player.PlaybackCoordinator
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Keeps the Android TV home screen's rows ([TvLauncher]) on the signed-in profile's Continue
 * Watching and watchlist, as the Expo app's `TVTopShelfSync` does: after Home loads (reusing
 * its Continue Watching fetch), after playback, a watchlist change or a title action, and on
 * wake; debounced, and rewritten only when the payload changed. Signing out or switching
 * profile clears the rows. A device without the launcher's channels (Fire TV) is skipped.
 * Main thread only; provider writes run in order on one background thread.
 */
@OptIn(ExperimentalCoroutinesApi::class)
object LauncherSync {
    private const val TAG = TvLauncher.TAG
    private const val DEBOUNCE_MS = 1_500L
    private const val KEY_PROFILE = "rowsProfileId"

    private val scope = MainScope()
    private val io = Dispatchers.IO.limitedParallelism(1)
    private val prefs by lazy { App.context.getSharedPreferences("launcher_sync", Context.MODE_PRIVATE) }
    private var launcher: TvLauncher? = null
    private var started = false
    private var pending: Job? = null
    private var last: LauncherPayload? = null
    /** Bumped by every clear, so a sync begun for the previous profile never writes. */
    private var generation = 0
    /** The profile's Home "hide watched" setting, as Home last loaded it. */
    private var hideWatched = false

    fun start() {
        if (started) return
        started = true
        val reason = TvLauncher.unsupportedReason(App.context)
        if (reason != null) {
            AppLog.i(TAG, "skipped: $reason")
            return
        }
        launcher = TvLauncher(App.context)
        scope.launch { PlaybackCoordinator.sessionsEnded.drop(1).collect { request("playback ended") } }
        // On wake the watchlist may have changed on another device too; its refresh requests again.
        scope.launch {
            AppSession.wakes.drop(1).collect {
                request("wake")
                if (AppSession.phase.value == AppSession.Phase.Ready) WatchlistStore.refresh()
            }
        }
        scope.launch { snapshotFlow { TitleActions.changes }.drop(1).collect { request("title action") } }
        scope.launch {
            snapshotFlow { WatchlistStore.isLoaded to WatchlistStore.items }.filter { it.first }.collect { request("watchlist") }
        }
    }

    /** Home's All view loaded: its Continue Watching (when it has the row) and its hide-watched setting. */
    fun homeLoaded(continueWatching: List<ContinueWatchingItem>?, hideWatched: Boolean) {
        this.hideWatched = hideWatched
        request("home loaded", continueWatching)
    }

    /** Syncs shortly; a later request replaces this one. [prefetched] spares a Continue Watching fetch. */
    fun request(reason: String, prefetched: List<ContinueWatchingItem>? = null) {
        if (launcher == null || AppSession.phase.value != AppSession.Phase.Ready) return
        Log.d("strmr.$TAG", "request ($reason)")
        pending?.cancel()
        pending = scope.launch {
            delay(DEBOUNCE_MS)
            sync(reason, prefetched)
        }
    }

    /** A profile is in use: rows written for another profile go first. */
    fun profileActivated(id: String) {
        if (launcher == null) return
        val owner = prefs.getString(KEY_PROFILE, null)
        if (owner != null && owner != id) clear("profile changed")
    }

    /** Signed out, switching profile, another server: the rows go. */
    fun clear(reason: String) {
        val launcher = launcher ?: return
        pending?.cancel()
        generation++
        last = null
        prefs.edit().remove(KEY_PROFILE).apply()
        scope.launch {
            withContext(io) {
                runCatching { launcher.clear() }.onFailure { AppLog.e(TAG, "clear failed", it) }
            }
            AppLog.i(TAG, "cleared ($reason)")
        }
    }

    private suspend fun sync(reason: String, prefetched: List<ContinueWatchingItem>?) {
        val launcher = launcher ?: return
        val gen = generation
        val profileId = AppSession.profile.value?.id ?: return
        val items = prefetched ?: try {
            ApiClient.continueWatching()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            // A failed fetch keeps the rows already on the launcher.
            AppLog.w(TAG, "sync ($reason): continue watching failed: ${e.message}")
            return
        }
        if (!WatchlistStore.isLoaded) WatchlistStore.refresh()
        if (gen != generation || AppSession.profile.value?.id != profileId) return
        val payload = LauncherPayload.build(items, WatchlistStore.items, profileId, hideWatched, resolve = ::absolute)
        if (payload == last) {
            Log.d("strmr.$TAG", "sync ($reason): unchanged")
            return
        }
        prefs.edit().putString(KEY_PROFILE, profileId).apply()
        // Once begun, a write finishes even if a newer request replaces this sync: cancelled
        // halfway, the provider would hold a partial row and `last` would no longer match it.
        val state = withContext(io + NonCancellable) {
            runCatching {
                if (payload.isEmpty) launcher.clear() else launcher.write(payload)
                launcher.describe()
            }
        }
        state.onSuccess {
            if (gen == generation) last = payload
            AppLog.i(TAG, "wrote ($reason): continueWatching=${payload.continueWatching.size} watchlist=${payload.watchlist.size}; $it")
        }.onFailure {
            AppLog.e(TAG, "write failed ($reason)", it)
        }
    }

    /** The launcher fetches art itself: a server-relative path needs the server's address. */
    private fun absolute(url: String): String? =
        if (url.startsWith("/")) ApiClient.baseUrl.resolve(url)?.toString() else url
}

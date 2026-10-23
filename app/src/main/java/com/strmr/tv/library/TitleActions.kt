package com.strmr.tv.library

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.setValue
import com.strmr.tv.api.ApiClient
import com.strmr.tv.api.DetailsTarget
import com.strmr.tv.api.deleteMovieProgress
import com.strmr.tv.api.setWatched
import com.strmr.tv.app.AppLog
import com.strmr.tv.home.hideFromContinueWatching
import com.strmr.tv.ui.AppToast
import kotlinx.coroutines.CancellationException

/**
 * The title actions the long-press menus and the details page share (the Expo app's Home
 * actions): Set Aside, Hide from Continue Watching, Hide from this profile / Unhide and Mark
 * as watched / unwatched, each with the Expo app's toast. [changes] is bumped after every
 * one, so Home's playback rows, list grids and details pages refresh. Main thread only.
 */
object TitleActions {
    var changes by mutableIntStateOf(0)
        private set

    /** Runs `action`, then the success toast; a failure shows `failure` and is logged. True on success. */
    private suspend fun run(name: String, target: DetailsTarget, success: String, failure: String, action: suspend () -> Unit): Boolean =
        try {
            action()
            changes++
            AppLog.i("TitleActions", "$name ${target.titleId} (${target.name})")
            AppToast.show(success)
            true
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            AppLog.w("TitleActions", "$name ${target.titleId} failed: ${e.message}")
            AppToast.show(failure)
            false
        }

    suspend fun setAside(target: DetailsTarget) =
        run("set-aside", target, "Added to Set Aside", "Failed to add to Set Aside") { SetAsideStore.add(target) }

    suspend fun removeFromSetAside(target: DetailsTarget) =
        run("remove-set-aside", target, "Removed from Set Aside", "Failed to remove from Set Aside") { SetAsideStore.remove(target) }

    suspend fun hideFromContinueWatching(target: DetailsTarget) =
        run("hide-continue-watching", target, "Removed from Continue Watching", "Failed to remove from Continue Watching") {
            ApiClient.hideFromContinueWatching(target.titleId)
        }

    suspend fun hide(target: DetailsTarget) =
        run("hide", target, "Hidden from this profile", "Failed to hide item") { HiddenItemsStore.hide(target) }

    suspend fun unhide(target: DetailsTarget) =
        run("unhide", target, "Unhidden", "Failed to unhide item") { HiddenItemsStore.unhide(target) }

    suspend fun setMovieWatched(movie: DetailsTarget, watched: Boolean) = run(
        if (watched) "mark-watched" else "mark-unwatched", movie,
        if (watched) "Marked as watched" else "Marked as unwatched", "Failed to update watched state",
    ) {
        // As the Expo app and tvOS: unwatched also drops the resume point, after the history change.
        ApiClient.setWatched(watched, movie)
        if (!watched) ApiClient.deleteMovieProgress(movie)
    }

    /** Something else changed a title's state (a details page's watched button): refresh what shows it. */
    fun changed() { changes++ }
}

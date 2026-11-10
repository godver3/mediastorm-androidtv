package com.strmr.tv.ui.details

import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import com.strmr.tv.api.ApiClient
import com.strmr.tv.api.Trailer
import com.strmr.tv.api.playbackSettings
import com.strmr.tv.api.trailerUrl
import com.strmr.tv.app.AppLog
import com.strmr.tv.player.PlaybackCoordinator
import com.strmr.tv.ui.settings.TrailerSettings
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * The details page's trailer, as the Expo TV page runs it. The server's pick is downloaded as
 * soon as it is known. With auto-play on (the device's setting, else `autoPlayTrailersTV`) it
 * plays behind the header two seconds after the page loads, at 40% volume, and four seconds
 * in the header fades out (immersive) until a press brings it back. It stops for good on the
 * page once focus leaves the header, playback starts, one of the page's menus opens, or the
 * trailer opens full screen. Starts and stops are logged under `strmr.trailer`. The
 * Trailer button pauses / resumes the backdrop trailer, else plays it full screen.
 */
class TrailerController(private val context: Context, private val scope: CoroutineScope) {
    /** The page has a trailer; the Trailer button shows. */
    var available by mutableStateOf(false)
        private set
    /** The backdrop trailer is showing frames (the backdrop image fades out under it). */
    var showing by mutableStateOf(false)
        private set
    var paused by mutableStateOf(false)
        private set
    /** The header is faded out over the backdrop trailer. */
    var immersive by mutableStateOf(false)
        private set
    var preparingFullScreen by mutableStateOf(false)
        private set
    var errorMessage by mutableStateOf<String?>(null)
        private set
    /** The backdrop player, while one exists. */
    var player by mutableStateOf<ExoPlayer?>(null)
        private set

    private var trailer: Trailer? = null
    private var readyUrl: String? = null
    /** Stopped for good on this page (as the Expo page's `dismissTrailerAutoPlay`). */
    private var dismissed = false
    private var prepareJob: Job? = null
    private var immersiveJob: Job? = null
    private val loadedAt = System.currentTimeMillis()

    /** The page's trailer from details; a reload with the same one keeps what is under way. */
    fun prepare(next: Trailer?) {
        if (next == trailer) return
        trailer = next
        available = next != null
        readyUrl = null
        prepareJob?.cancel()
        next ?: return
        prepareJob = scope.launch {
            val url = ApiClient.trailerUrl(next)
            val autoplay = TrailerSettings.autoPlay(server = ApiClient.playbackSettings().autoPlayTrailers)
            readyUrl = url
            if (!autoplay || url == null) return@launch
            val wait = START_DELAY_MS - (System.currentTimeMillis() - loadedAt)
            if (wait > 0) delay(wait)
            startBackdrop()
        }
    }

    private fun startBackdrop() {
        val url = readyUrl ?: return
        if (dismissed || player != null || PlaybackCoordinator.resolving.value != null) return
        val p = ExoPlayer.Builder(context).build()
        p.volume = VOLUME
        p.addListener(object : Player.Listener {
            // Shown once frames flow, so a slow start keeps the backdrop image up.
            override fun onRenderedFirstFrame() {
                showing = true
                scheduleImmersive()
            }

            override fun onPlaybackStateChanged(state: Int) {
                if (state == Player.STATE_ENDED) dismiss("ended")
            }

            // A trailer that won't play just leaves the backdrop image, as in the Expo app.
            override fun onPlayerError(error: PlaybackException) = dismiss("error ${error.errorCodeName}")
        })
        p.setMediaItem(MediaItem.fromUri(url))
        p.playWhenReady = true
        p.prepare()
        player = p
        paused = false
        AppLog.i(TAG, "start backdrop trailer \"${trailer?.name}\"")
    }

    /** Any press over the trailer: the header comes back, then fades again if it keeps playing. */
    fun wake() {
        immersive = false
        scheduleImmersive()
    }

    private fun scheduleImmersive() {
        immersiveJob?.cancel()
        if (!showing || paused) return
        immersiveJob = scope.launch {
            delay(IMMERSIVE_DELAY_MS)
            if (showing && !paused) immersive = true
        }
    }

    fun togglePause() {
        val p = player ?: return
        paused = !paused
        if (paused) p.pause() else p.play()
        wake()
    }

    /** Stops the backdrop trailer for the rest of this page; [reason] goes to the log. */
    fun dismiss(reason: String) {
        if (!dismissed) AppLog.i(TAG, "dismiss: $reason${if (player != null) " (was playing)" else " (before start)"}")
        dismissed = true
        immersiveJob?.cancel()
        player?.release()
        player = null
        showing = false
        immersive = false
    }

    /** The Trailer button. */
    fun buttonPressed(titleName: String) {
        if (player != null) {
            togglePause()
            return
        }
        val t = trailer ?: return
        if (preparingFullScreen) return
        dismiss("full screen")
        preparingFullScreen = true
        errorMessage = null
        scope.launch {
            // Fetched again: the server deletes an unplayed download about a minute after it is
            // ready, and returns the same file while it is still there.
            val url = ApiClient.trailerUrl(t)
            preparingFullScreen = false
            if (url != null) PlaybackCoordinator.playTrailer(url, "$titleName · Trailer") else errorMessage = "Unable to play trailer"
        }
    }

    fun release() {
        prepareJob?.cancel()
        dismiss("page closed")
    }

    private companion object {
        const val TAG = "trailer"
        const val START_DELAY_MS = 2_000L
        const val IMMERSIVE_DELAY_MS = 4_000L
        const val VOLUME = 0.4f
    }
}

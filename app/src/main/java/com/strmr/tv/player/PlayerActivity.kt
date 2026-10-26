package com.strmr.tv.player

import android.os.Build
import android.os.Bundle
import android.view.Display
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.annotation.OptIn
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.common.util.UnstableApi
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.PlayerView
import com.strmr.tv.api.ApiClient
import com.strmr.tv.api.playbackSettings
import com.strmr.tv.live.LiveStream
import com.strmr.tv.ui.live.multiscreen.MultiscreenHandover
import com.strmr.tv.ui.live.multiscreen.MultiscreenLauncher
import com.strmr.tv.ui.StrmrTheme

/**
 * Full-screen playback, its own activity so the system handles it as a player: the window's
 * display mode follows the video's frame rate, and leaving it (Home, sleep) saves progress and
 * returns to the details page, as the tvOS app does after a sleep.
 */
@OptIn(UnstableApi::class)
class PlayerActivity : ComponentActivity() {
    private var controller: PlayerController? = null
    private var frameRate: FrameRateMatcher? = null
    /** Add Screen: the channel the multiscreen opens with once this player has let go. */
    private var handover: MultiscreenHandover? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val session = PlaybackCoordinator.pending
        if (session == null) {
            finish()
            return
        }
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        PlaybackCoordinator.playerOpen = true
        val matcher = FrameRateMatcher(window, currentDisplay())
        frameRate = matcher
        val c = PlayerController(this, session) { format -> matcher.match(format.frameRate) }
        controller = c
        setContent {
            StrmrTheme {
                val ended by c.ended.collectAsState()
                val next by c.advanceTo.collectAsState()
                val switch by c.switchTo.collectAsState()
                LaunchedEffect(ended) { if (ended) finish() }
                LaunchedEffect(Unit) { SubtitleStyleStore.adopt(ApiClient.playbackSettings().subtitleStyle) }
                // Up Next: close this episode (saved as finished) and start the next over its loading art.
                LaunchedEffect(next) {
                    next?.let {
                        finish()
                        PlaybackCoordinator.play(it)
                    }
                }
                // Switch Source: close this stream (saved at its position) and resume on the other.
                LaunchedEffect(switch) {
                    switch?.let {
                        finish()
                        PlaybackCoordinator.play(it.request, it.source, carried = it.carried)
                    }
                }
                Box(Modifier.fillMaxSize().background(Color.Black)) {
                    AndroidView(
                        factory = { context ->
                            PlayerView(context).apply {
                                useController = false
                                // Only the Compose chrome takes focus; the stock controller's
                                // hidden buttons would otherwise swallow D-pad keys.
                                isFocusable = false
                                descendantFocusability = android.view.ViewGroup.FOCUS_BLOCK_DESCENDANTS
                                player = c.player
                                resizeMode = AspectRatioFrameLayout.RESIZE_MODE_FIT
                                setShutterBackgroundColor(android.graphics.Color.BLACK)
                                // Subtitles are drawn by SubtitleOverlay, as the tvOS overlay draws them.
                                subtitleView?.visibility = android.view.View.GONE
                            }
                        },
                        modifier = Modifier.fillMaxSize().focusProperties { canFocus = false },
                    )
                    val cues by c.cues.collectAsState()
                    val aspect by c.videoAspect.collectAsState()
                    // The device's subtitle style (Settings, or the Subtitles menu's Style picks), live.
                    SubtitleOverlay(cues, SubtitleStyleStore.style, aspect, Modifier.fillMaxSize())
                    PlayerChrome(c, onExit = { finish() }, onAddScreen = { addScreen(c) }.takeIf { session.liveChannel != null })
                    SubtitleStatusToast(c, Modifier.align(androidx.compose.ui.Alignment.TopEnd))
                }
            }
        }
    }

    /**
     * Add Screen: an HLS session is handed to the multiscreen's first screen as is (the
     * retune gives it up, so closing this player doesn't stop it); a direct stream closes
     * with the player and the first screen tunes the channel again.
     */
    private fun addScreen(c: PlayerController) {
        val channel = c.session.liveChannel ?: return
        if (handover != null) return
        val url = c.player.currentMediaItem?.localConfiguration?.uri?.toString()
        val sessionId = url?.let { c.liveRetune?.handOver() }
        handover = MultiscreenHandover(channel, sessionId?.let { LiveStream(url, it) })
        finish()
    }

    override fun onResume() {
        super.onResume()
        PlaybackCoordinator.playerStarted()
    }

    override fun onStop() {
        super.onStop()
        // Home, sleep or another app: keep the position and go back to details.
        if (!isFinishing) finish()
    }

    override fun onDestroy() {
        super.onDestroy()
        val saving = controller?.release()
        controller = null
        frameRate?.reset()
        PlaybackCoordinator.playerOpen = false
        if (isFinishing) PlaybackCoordinator.playerClosed(saving)
        // After the release, so a direct stream's provider connection is closed first.
        handover?.let { MultiscreenLauncher.open(it) }
        handover = null
    }

    private fun currentDisplay(): Display? =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) display
        else @Suppress("DEPRECATION") windowManager.defaultDisplay
}

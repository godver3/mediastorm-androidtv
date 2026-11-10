package com.strmr.mobile.player.ui

import androidx.annotation.OptIn
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.common.util.UnstableApi
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.PlayerView
import com.strmr.tv.player.PlayerController
import com.strmr.tv.player.SubtitleStyleStore

/** How the picture fills the screen (the Aspect sheet). */
enum class AspectMode(val title: String, val detail: String, val resizeMode: Int) {
    @OptIn(UnstableApi::class)
    FIT("Fit", "The whole picture, with bars", AspectRatioFrameLayout.RESIZE_MODE_FIT),
    @OptIn(UnstableApi::class)
    ZOOM("Zoom", "Fill the screen, cropping the edges", AspectRatioFrameLayout.RESIZE_MODE_ZOOM),
    @OptIn(UnstableApi::class)
    FILL("Stretch", "Fill the screen, stretching the picture", AspectRatioFrameLayout.RESIZE_MODE_FILL),
}

/**
 * The player's screen: the video (Media3's PlayerView, its own controller and subtitles off),
 * the subtitles drawn on the picture in the device's style, and the touch controls, which go
 * away in picture in picture.
 */
@OptIn(UnstableApi::class)
@Composable
fun PlayerScreen(
    controller: PlayerController,
    inPip: Boolean,
    tablet: Boolean,
    onExit: () -> Unit,
    onPictureInPicture: (() -> Unit)?,
) {
    var aspectMode by rememberSaveable { mutableStateOf(AspectMode.FIT) }
    val state = remember(controller) { PlayerUiState() }
    // A sheet doesn't belong in the PiP window; it's gone when the player comes back.
    androidx.compose.runtime.LaunchedEffect(inPip) { if (inPip) state.sheet = null }
    Box(Modifier.fillMaxSize().background(Color.Black)) {
        AndroidView(
            factory = { context ->
                PlayerView(context).apply {
                    useController = false
                    player = controller.player
                    setShutterBackgroundColor(android.graphics.Color.BLACK)
                    // Subtitles are drawn by PhoneSubtitleOverlay, as on the TV.
                    subtitleView?.visibility = android.view.View.GONE
                }
            },
            update = { it.resizeMode = aspectMode.resizeMode },
            modifier = Modifier.fillMaxSize(),
        )
        val cues by controller.cues.collectAsState()
        val aspect by controller.videoAspect.collectAsState()
        // Fit: on the picture. Zoom / Stretch: the picture is the screen (or past it), so the screen.
        PhoneSubtitleOverlay(cues, SubtitleStyleStore.style, if (aspectMode == AspectMode.FIT) aspect else 0f, Modifier.fillMaxSize())
        if (!inPip) {
            PlayerControls(
                controller = controller,
                ui = state,
                tablet = tablet,
                aspectMode = aspectMode,
                onAspectMode = { aspectMode = it },
                onExit = onExit,
                onPictureInPicture = onPictureInPicture,
            )
        }
    }
}

/** The controls' state hoisted above them: the open sheet. */
class PlayerUiState {
    var sheet by mutableStateOf<PlayerSheet?>(null)
}

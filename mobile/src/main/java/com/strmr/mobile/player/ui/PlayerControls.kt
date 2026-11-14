package com.strmr.mobile.player.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.dp
import com.strmr.mobile.cast.castable
import com.strmr.mobile.ui.cast.PlayerCastPicker
import com.strmr.tv.player.PlayerController
import com.strmr.tv.player.ProgrammeTimeline
import com.strmr.tv.player.SkipAction
import com.strmr.tv.player.Timeshift
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow

/**
 * The phone player's touch controls over the video (the iPhone player's transport, in Material):
 * a scrim with the top row (close, title, picture in picture, Audio, Subtitles, Settings), the
 * centre −10 s / play-pause / +10 s, and the seek bar (or the LIVE capsule) at the bottom with a
 * live channel's Start Over and Go Live. A tap shows or hides them; they hide by themselves a
 * few seconds after the last touch while playing ([ChromeTimer]). Skip Intro and Up Next stay
 * on screen with the controls hidden, as on the TV.
 */
@Composable
fun PlayerControls(
    controller: PlayerController,
    ui: PlayerUiState,
    tablet: Boolean,
    aspectMode: AspectMode,
    onAspectMode: (AspectMode) -> Unit,
    onExit: () -> Unit,
    onPictureInPicture: (() -> Unit)?,
) {
    val playing by controller.isPlaying.collectAsState()
    val buffering by controller.buffering.collectAsState()
    val error by controller.error.collectAsState()
    val switching by controller.switching.collectAsState()
    val notice by controller.notice.collectAsState()
    val skip by (controller.upNext?.action ?: remember { MutableStateFlow<SkipAction?>(null) }).collectAsState()
    val reconnecting by (controller.liveRetune?.reconnecting ?: remember { MutableStateFlow(false) }).collectAsState()
    val reconnectNote by (controller.liveRetune?.note ?: remember { MutableStateFlow<String?>(null) }).collectAsState()
    val programme by (controller.catchup?.timeline ?: remember { MutableStateFlow<ProgrammeTimeline?>(null) }).collectAsState()
    val timeshift by controller.timeshift.collectAsState()
    val behindLive by controller.behindLive.collectAsState()
    val timer = remember { ChromeTimer() }
    var visible by remember { mutableStateOf(true) }
    var scrubbing by remember { mutableStateOf(false) }
    var castPicker by remember { mutableStateOf(false) }
    val busy by rememberUpdatedState(ui.sheet != null || scrubbing)
    val isPlaying by rememberUpdatedState(playing)
    val live = controller.session.isLive

    LaunchedEffect(Unit) {
        while (true) {
            if (timer.shouldHide(System.currentTimeMillis(), isPlaying, busy)) {
                timer.hide()
                visible = false
            }
            delay(250)
        }
    }
    fun toggle() {
        timer.toggle(System.currentTimeMillis())
        visible = timer.visible
    }
    fun open(sheet: PlayerSheet) {
        ui.sheet = sheet
    }
    // playWhenReady, not isPlaying: a tap while buffering (a live join, a seek) must pause.
    fun togglePlay() = if (controller.player.playWhenReady) controller.player.pause() else controller.player.play()

    Box(
        Modifier.fillMaxSize().pointerInput(Unit) {
            // Every touch, wherever it lands, restarts the hide countdown (observed, not consumed).
            awaitPointerEventScope {
                while (true) {
                    awaitPointerEvent(PointerEventPass.Initial)
                    timer.touched(System.currentTimeMillis())
                }
            }
        },
    ) {
        PlayerGestures(controller.seekable, onTap = ::toggle, onSeek = { controller.skip(it) })

        if (reconnecting && error == null) {
            ReconnectingCover(controller.request, reconnectNote)
        } else if ((buffering || switching) && error == null && !scrubbing && !visible) {
            CircularProgressIndicator(Modifier.align(Alignment.Center).size(48.dp), color = Color.White)
        }

        AnimatedVisibility(visible && error == null, enter = fadeIn(), exit = fadeOut()) {
            Box(
                Modifier.fillMaxSize().background(
                    Brush.verticalGradient(
                        0f to Color.Black.copy(alpha = 0.7f), 0.3f to Color.Black.copy(alpha = 0.25f),
                        0.65f to Color.Black.copy(alpha = 0.25f), 1f to Color.Black.copy(alpha = 0.8f),
                    ),
                ),
            ) {
                Box(Modifier.fillMaxSize().safeDrawingPadding().padding(horizontal = 8.dp, vertical = 4.dp)) {
                    TopBar(
                        controller, onExit, onPictureInPicture,
                        onAudio = { open(PlayerSheet.AUDIO) }, onSubtitles = { open(PlayerSheet.SUBTITLES) },
                        onSettings = { open(PlayerSheet.SETTINGS) }, modifier = Modifier.align(Alignment.TopCenter),
                        onCast = { castPicker = true }.takeIf { controller.session.castable() },
                    )
                    CenterButtons(
                        seekable = controller.seekable, playing = playing || (buffering && controller.player.playWhenReady),
                        loading = (buffering || switching) && !scrubbing,
                        onBack = { controller.skip(-10_000) }, onPlayPause = ::togglePlay, onForward = { controller.skip(10_000) },
                        modifier = Modifier.align(Alignment.Center),
                    )
                    BottomBar(
                        controller, live = live, timeshift = timeshift, programme = programme,
                        showGoLive = live && (timeshift || programme != null) && !Timeshift.isAtLive(behindLive),
                        showStartOver = live && programme.let { it != null && it.positionMs > START_OVER_MIN_MS },
                        onScrubbing = { scrubbing = it },
                        modifier = Modifier.align(Alignment.BottomCenter).padding(horizontal = 8.dp),
                    )
                }
            }
        }

        skip?.takeIf { error == null }?.let { action ->
            val placement = Modifier.align(Alignment.BottomEnd).safeDrawingPadding()
                .padding(end = 16.dp, bottom = if (visible) 104.dp else 24.dp)
            when (action) {
                is SkipAction.Skip -> SkipButton(action, placement) { controller.seekTo((action.segment.end * 1000).toLong()) }
                is SkipAction.NextEpisode -> UpNextCard(
                    action, onPlay = { controller.upNext?.advance(action.next) }, onDismiss = { controller.upNext?.dismiss() },
                    modifier = placement,
                )
            }
        }

        notice?.let { text ->
            LaunchedEffect(text) { delay(4_000); controller.notice.value = null }
            StatusPill(text, Modifier.align(Alignment.TopCenter).safeDrawingPadding().padding(top = 64.dp))
        }
        SubtitleStatus(controller, Modifier.align(Alignment.TopEnd).safeDrawingPadding().padding(top = 64.dp, end = 16.dp))
        error?.takeIf { ui.sheet != PlayerSheet.REPORT }?.let { PlaybackErrorDialog(it, onExit,
            onReport = if (controller.canReportRelease) ({ ui.sheet = PlayerSheet.REPORT }) else null) }
        PlayerSheetHost(controller, ui, tablet, aspectMode, onAspectMode)
        if (castPicker) PlayerCastPicker(controller, onExit, onDismiss = { castPicker = false })
    }
}

/** Start Over shows once the playhead is this far into the programme. */
private const val START_OVER_MIN_MS = 60_000L

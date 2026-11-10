package com.strmr.mobile.player.ui

import android.app.Activity
import android.content.Context
import android.media.AudioManager
import android.provider.Settings
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material.icons.filled.BrightnessMedium
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay

/**
 * The touch layer under the controls: a tap shows or hides them, a double tap on the left or
 * right half seeks back or forward 10 s (with a label that adds up quick repeats), and a vertical
 * swipe on the left half sets the screen's brightness (this window only) and on the right half
 * the media volume, as the Expo phone player does.
 */
@Composable
fun BoxScope.PlayerGestures(
    seekable: Boolean,
    onTap: () -> Unit,
    onSeek: (Long) -> Unit,
) {
    val context = LocalContext.current
    val ripple = remember { SeekRipple() }
    var rippleVersion by remember { mutableIntStateOf(0) }
    var level by remember { mutableStateOf<Pair<Int, Float>?>(null) }
    val audio = remember { context.getSystemService(Context.AUDIO_SERVICE) as AudioManager }

    Box(
        Modifier.fillMaxSize()
            .pointerInput(seekable) {
                detectTapGestures(
                    onTap = { onTap() },
                    onDoubleTap = { at ->
                        if (seekable) {
                            onSeek(ripple.tap(sideOf(at.x, size.width.toFloat()), System.currentTimeMillis()))
                            rippleVersion++
                        } else onTap()
                    },
                )
            }
            .pointerInput(Unit) {
                var side = 0
                var start = 0f
                var dragged = 0f
                detectVerticalDragGestures(
                    onDragStart = { at ->
                        side = sideOf(at.x, size.width.toFloat())
                        dragged = 0f
                        start = if (side < 0) brightness(context) else volume(audio)
                        level = side to start
                    },
                    onDragEnd = { level = null },
                    onDragCancel = { level = null },
                ) { change, dy ->
                    change.consume()
                    dragged += dy
                    val next = swipeLevel(start, dragged, size.height.toFloat())
                    if (side < 0) setBrightness(context, next) else setVolume(audio, next)
                    level = side to next
                }
            },
    )

    // The seek label over the tapped side, gone shortly after the last tap.
    if (rippleVersion > 0 && ripple.side != 0) {
        LaunchedEffect(rippleVersion) {
            delay(SeekRipple.WINDOW_MS)
            ripple.reset()
            rippleVersion = 0
        }
        SeekRippleLabel(
            ripple.label,
            Modifier.align(if (ripple.side < 0) Alignment.CenterStart else Alignment.CenterEnd).padding(horizontal = 48.dp),
        )
    }
    level?.let { (side, value) ->
        LevelIndicator(
            if (side < 0) Icons.Filled.BrightnessMedium else Icons.AutoMirrored.Filled.VolumeUp, value,
            Modifier.align(Alignment.TopCenter).padding(top = 32.dp),
        )
    }
}

/** This window's brightness, or the system's while the window follows it. */
private fun brightness(context: Context): Float {
    val window = (context as? Activity)?.window ?: return 0.5f
    val own = window.attributes.screenBrightness
    if (own >= 0) return own
    val system = runCatching { Settings.System.getInt(context.contentResolver, Settings.System.SCREEN_BRIGHTNESS) }.getOrDefault(128)
    return (system / 255f).coerceIn(0f, 1f)
}

/** Only this window: leaving the player gives the screen back its own brightness. */
private fun setBrightness(context: Context, value: Float) {
    val window = (context as? Activity)?.window ?: return
    window.attributes = window.attributes.apply { screenBrightness = value.coerceIn(0.01f, 1f) }
}

private fun volume(audio: AudioManager): Float {
    val max = audio.getStreamMaxVolume(AudioManager.STREAM_MUSIC)
    return volumeLevel(audio.getStreamVolume(AudioManager.STREAM_MUSIC), max)
}

private fun setVolume(audio: AudioManager, value: Float) {
    val max = audio.getStreamMaxVolume(AudioManager.STREAM_MUSIC)
    val step = volumeStep(value, max)
    if (step != audio.getStreamVolume(AudioManager.STREAM_MUSIC)) {
        runCatching { audio.setStreamVolume(AudioManager.STREAM_MUSIC, step, 0) }
    }
}

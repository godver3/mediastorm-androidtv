package com.strmr.tv.ui.live.multiscreen

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEvent
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.tv.material3.Text
import com.strmr.tv.live.MultiscreenSession
import com.strmr.tv.ui.BrandBackground
import com.strmr.tv.ui.ChoiceDialog
import com.strmr.tv.ui.DialogChoice
import com.strmr.tv.ui.MessageDialog
import com.strmr.tv.ui.live.LivePick
import com.strmr.tv.ui.live.LiveScreen
import kotlinx.coroutines.delay

/** Space between tiles, in dp: room for the focus hairline. */
private const val GAP = 8f
/** Around the tiles, so the focus hairline at the screen's edge stays on screen. */
private const val MARGIN = 12f
/** The footer hint's band under the tiles. */
private const val FOOTER = 28f
/** The overlay hides this long after the last key, as the live player's HUD does. */
private const val HUD_TIMEOUT_MS = 5_000L

/**
 * The multiscreen: the tiles at their frames, the D-pad over them handled here (arrows by
 * [MultiscreenLayout.neighbor], not focus search over absolutely placed tiles; OK listens,
 * then enlarges; hold OK for the control bar), and the bar, dialogs and channel picker.
 */
@Composable
fun MultiscreenScreen(model: MultiscreenModel) {
    val state by model.session.state.collectAsState()
    val catcher = remember { FocusRequester() }
    var hudVisible by remember { mutableStateOf(true) }
    var held by remember { mutableStateOf(false) }
    val dialogOpen = model.picker || model.warning || model.replacement != null || model.notice != null

    LaunchedEffect(Unit) {
        while (true) {
            hudVisible = System.currentTimeMillis() - model.lastInput < HUD_TIMEOUT_MS
            delay(250)
        }
    }
    // The tiles' channels' now / next, every minute.
    val keys = state.slots.mapNotNull { it.channel?.guideKey }.distinct()
    LaunchedEffect(keys) {
        while (true) {
            model.refreshEpg(keys)
            delay(60_000)
        }
    }
    LaunchedEffect(model.actions, dialogOpen) {
        if (!model.actions && !dialogOpen) runCatching { catcher.requestFocus() }
    }
    BackHandler { model.back() }

    BoxWithConstraints(Modifier.fillMaxSize().background(BrandBackground)) {
        val width = maxWidth.value
        val height = maxHeight.value
        val occupied = state.slots.indices.filter { state.slots[it].isOccupied }
        val ids = occupied.ifEmpty { listOf(0, 1) }
        val area = (if (model.actions) height - DOCK_HEIGHT else height - FOOTER) - MARGIN
        val frames = remember(ids, model.expanded, width, height, model.actions) {
            MultiscreenLayout.frames(ids, model.expanded, width - MARGIN * 2, maxOf(1f, area), GAP)
                .mapValues { (_, f) -> f.copy(x = f.x + MARGIN, y = f.y + MARGIN) }
        }

        fun onKey(e: KeyEvent): Boolean {
            if (e.type == KeyEventType.KeyDown) model.reveal()
            val direction = when (e.key) {
                Key.DirectionLeft -> TileDirection.LEFT
                Key.DirectionRight -> TileDirection.RIGHT
                Key.DirectionUp -> TileDirection.UP
                Key.DirectionDown -> TileDirection.DOWN
                else -> null
            }
            if (direction != null) {
                if (e.type == KeyEventType.KeyDown) model.arrow(direction, frames)
                return true
            }
            if (e.key != Key.DirectionCenter && e.key != Key.Enter && e.key != Key.NumPadEnter) return false
            // Select acts on release; held, it opens the control bar instead.
            when {
                e.type == KeyEventType.KeyDown && e.nativeKeyEvent.repeatCount == 0 -> held = false
                e.type == KeyEventType.KeyDown -> if (!held) {
                    held = true
                    model.reveal()
                    model.actions = true
                }
                e.type == KeyEventType.KeyUp -> if (!held) model.select(state.focusedIndex) else held = false
            }
            return true
        }

        state.slots.forEachIndexed { index, slot ->
            val frame = frames[index] ?: return@forEachIndexed
            key(index) {
                MultiscreenTile(
                    model, index, slot, frame,
                    focused = index == state.focusedIndex && hudVisible && !model.actions,
                    audible = state.audioIndex == index,
                    overlay = !model.picker && (hudVisible || model.actions),
                    alone = frames.size == 1,
                )
            }
        }
        Box(
            Modifier.fillMaxSize().focusRequester(catcher).focusProperties { canFocus = !model.actions }
                .focusable().onKeyEvent(::onKey),
        )
        if (model.actions) MultiscreenControlBar(model, state, Modifier.align(Alignment.BottomStart))
        if (hudVisible || model.actions || model.exiting) {
            val audible = state.focusedIndex == state.audioIndex
            Text(
                when {
                    model.exiting -> "Releasing streams…"
                    model.actions -> "Arrow keys: Choose screen · OK ${if (audible) "Enlarge" else "Listen"} · ↑ / Back: Return to video"
                    else -> "Arrow keys: Choose screen · OK ${if (audible) "Enlarge" else "Listen"} · Hold OK: Controls · " +
                        "Back ${if (model.expanded != null) "Equal tiles" else "Guide"}"
                },
                fontSize = 11.sp, color = Color.White.copy(alpha = 0.55f),
                modifier = Modifier.align(if (model.actions) Alignment.TopStart else Alignment.BottomStart)
                    .padding(horizontal = 16.dp, vertical = 6.dp),
            )
        }
    }
    MultiscreenDialogs(model, state)
}

@Composable
private fun MultiscreenDialogs(model: MultiscreenModel, state: MultiscreenSession.State) {
    if (model.warning) {
        ChoiceDialog(
            title = "More screens, higher load",
            message = "Additional screens may cause buffering, overheating, or crashes on some devices.",
            choices = listOf(DialogChoice("Cancel") {}, DialogChoice("Continue") { model.acceptWarning() }),
            onDismiss = { model.dismissWarning() },
        )
    }
    model.replacement?.let { channel ->
        val occupied = state.slots.indices.filter { state.slots[it].isOccupied }
        ChoiceDialog(
            title = "Stream limit reached",
            message = "Replace a screen, or cancel and choose a source with available capacity.\n\n${channel.name}",
            choices = occupied.map { i ->
                DialogChoice("Screen ${i + 1} · ${state.slots[i].channel?.name ?: "Empty"}") { model.replace(i, channel) }
            } + DialogChoice("Cancel") {},
            onDismiss = { model.replacement = null },
        )
    }
    model.notice?.let { MessageDialog("Screen limit reached", it) { model.notice = null } }
    if (model.picker) {
        val marked = state.slots.getOrNull(state.focusedIndex)?.channel
        val pick = LivePick(model.pickerTitle(state.focusedIndex), marked, model.pickerError, model::choose)
        Dialog(
            onDismissRequest = { model.closePicker() },
            properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false),
        ) {
            Box(Modifier.fillMaxSize().background(BrandBackground)) { LiveScreen(pick, model.store) }
        }
    }
}

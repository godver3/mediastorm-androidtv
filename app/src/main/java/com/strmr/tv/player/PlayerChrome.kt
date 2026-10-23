package com.strmr.tv.player

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Info
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEvent
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import com.strmr.tv.ui.AppIcons
import kotlinx.coroutines.delay

private enum class Menu { INFO, AUDIO, SUBTITLES, SUBTITLE_STYLE, SUBTITLE_SEARCH, SUBTITLE_TRANSLATE, SPEED, SOURCES }

/**
 * Media3 has no TV transport UI, so this is the player's chrome in Compose for TV, laid out
 * like the tvOS transport bar: title and the Info / Audio / Subtitles / Speed buttons over a
 * progress bar with a seek preview, menus as popovers above the buttons, and Skip Intro /
 * Next Episode as a pill at the bottom right (alone while the chrome is hidden).
 *
 * Keys: Left / Right scrub (see [ScrubState]) from the bar or with the chrome hidden; select
 * plays / pauses (or commits a scrub); Down on the bar opens Info. Any key shows the chrome,
 * which hides after 5 s while playing. Back closes a menu, cancels a scrub, hides the chrome,
 * dismisses the pill, then closes the player.
 */
@Composable
fun PlayerChrome(controller: PlayerController, onExit: () -> Unit) {
    val player = controller.player
    val playing by controller.isPlaying.collectAsState()
    val buffering by controller.buffering.collectAsState()
    val error by controller.error.collectAsState()
    val skip by (controller.upNext?.action ?: remember { kotlinx.coroutines.flow.MutableStateFlow<SkipAction?>(null) }).collectAsState()
    val scope = rememberCoroutineScope()
    val scrub = remember { ScrubState(controller, scope) }
    var visible by remember { mutableStateOf(true) }
    var menu by remember { mutableStateOf<Menu?>(null) }
    var lastInput by remember { mutableLongStateOf(System.currentTimeMillis()) }
    var pillFocused by remember { mutableStateOf(false) }
    val scrubber = remember { FocusRequester() }
    val catcher = remember { FocusRequester() }
    val pill = remember { FocusRequester() }
    /** One per menu button; a closed menu hands focus back to the button that opened it. */
    val buttons = remember { Menu.entries.associateWith { FocusRequester() } }
    var openedFrom by remember { mutableStateOf<FocusRequester?>(null) }
    val live = controller.session.isLive
    val switchable = !live && controller.session.reportsProgress && controller.session.streamPath != null
    val skipShown = skip != null && error == null

    // Auto-hide.
    LaunchedEffect(Unit) {
        while (true) {
            if (visible && menu == null && playing && !scrub.active && System.currentTimeMillis() - lastInput > 5_000) visible = false
            delay(250)
        }
    }
    LaunchedEffect(visible, menu) {
        if (menu != null) return@LaunchedEffect
        val back = openedFrom.takeIf { visible }
        openedFrom = null
        runCatching { (back ?: if (visible) scrubber else if (skipShown) pill else catcher).requestFocus() }
    }
    // The pill appearing or going away: it takes the keys while the chrome is hidden.
    LaunchedEffect(skipShown) {
        if (menu != null) return@LaunchedEffect
        runCatching {
            when {
                !visible -> (if (skipShown) pill else catcher).requestFocus()
                !skipShown && pillFocused -> scrubber.requestFocus()
            }
        }
    }

    fun open(m: Menu, from: FocusRequester) {
        openedFrom = from
        menu = m
    }

    fun togglePlay() = if (player.isPlaying) player.pause() else player.play()
    fun show() { visible = true }

    fun activate(action: SkipAction) {
        when (action) {
            is SkipAction.Skip -> controller.seekTo((action.segment.end * 1000).toLong())
            is SkipAction.NextEpisode -> controller.upNext?.advance(action.next)
        }
    }

    /** Left / Right scrub; true when handled. */
    fun scrubKey(e: KeyEvent): Boolean {
        val dir = when (e.key) { Key.DirectionLeft -> -1; Key.DirectionRight -> 1; else -> return false }
        if (live) return false
        scrub.step(dir, e.nativeKeyEvent.repeatCount)
        show()
        return true
    }

    BackHandler {
        when {
            // Back from Style returns to the Subtitles menu, as a tvOS submenu does.
            menu == Menu.SUBTITLE_STYLE -> menu = Menu.SUBTITLES
            menu != null -> menu = null
            scrub.active -> scrub.cancel()
            visible && error == null -> visible = false
            skipShown -> controller.upNext?.dismiss()
            else -> onExit()
        }
    }

    val transportFocusable = Modifier.focusProperties { canFocus = menu == null }

    Box(
        Modifier.fillMaxSize().onPreviewKeyEvent { e ->
            if (e.type == KeyEventType.KeyDown) lastInput = System.currentTimeMillis()
            // Remote transport keys work whatever has focus.
            when {
                e.type != KeyEventType.KeyDown -> false
                e.key == Key.MediaPlayPause -> { togglePlay(); show(); true }
                e.key == Key.MediaPlay -> { player.play(); true }
                e.key == Key.MediaPause -> { player.pause(); show(); true }
                e.key == Key.MediaFastForward -> { controller.seekTo(player.currentPosition + 30_000); show(); true }
                e.key == Key.MediaRewind -> { controller.seekTo(player.currentPosition - 10_000); show(); true }
                else -> false
            }
        },
    ) {
        // Hidden chrome: this takes the keys.
        Box(
            Modifier.fillMaxSize().focusProperties { canFocus = !visible && menu == null }
                .focusRequester(catcher).focusable().onKeyEvent { e ->
                    if (e.type != KeyEventType.KeyDown) return@onKeyEvent false
                    if (scrubKey(e)) return@onKeyEvent true
                    when (e.key) {
                        Key.DirectionCenter, Key.Enter -> togglePlay()
                        Key.DirectionUp, Key.DirectionDown -> Unit
                        else -> return@onKeyEvent false
                    }
                    show()
                    true
                },
        )
        if (buffering && error == null && !scrub.active) {
            CircularProgressIndicator(Modifier.align(Alignment.Center).size(56.dp), color = Color.White, strokeWidth = 3.dp)
        }
        error?.let { ErrorPanel(it, onExit, Modifier.align(Alignment.Center)) }

        AnimatedVisibility(visible && error == null, enter = fadeIn(), exit = fadeOut()) {
            Box(Modifier.fillMaxSize().background(Brush.verticalGradient(0.5f to Color.Transparent, 1f to Color.Black.copy(alpha = 0.9f)))) {
                Column(Modifier.align(Alignment.BottomStart).fillMaxWidth().padding(horizontal = 64.dp, vertical = 40.dp)) {
                    Row(verticalAlignment = Alignment.Bottom) {
                        Column(Modifier.weight(1f).padding(end = 24.dp)) {
                            controller.request.displaySubtitle?.let {
                                Text(controller.request.displayTitle, color = Color.White.copy(alpha = 0.7f),
                                     style = MaterialTheme.typography.titleMedium, maxLines = 1)
                            }
                            Text(controller.request.displaySubtitle ?: controller.request.displayTitle,
                                 style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold,
                                 maxLines = 1, overflow = TextOverflow.Ellipsis)
                        }
                        Row(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                            @Composable
                            fun MenuButton(m: Menu, icon: androidx.compose.ui.graphics.vector.ImageVector, label: String) {
                                val requester = buttons.getValue(m)
                                TransportButton(icon, label, transportFocusable.focusRequester(requester)) { open(m, requester) }
                            }
                            if (!live) MenuButton(Menu.INFO, Icons.Filled.Info, "Info")
                            MenuButton(Menu.AUDIO, AppIcons.Audio, "Audio")
                            MenuButton(Menu.SUBTITLES, AppIcons.Subtitles, "Subtitles")
                            if (!live) MenuButton(Menu.SPEED, AppIcons.Speed, "Playback Speed")
                            // Not for live TV or trailers: only a resolved title has other sources.
                            if (switchable) MenuButton(Menu.SOURCES, AppIcons.SwapSource, "Switch Source")
                        }
                    }
                    Box(Modifier.padding(top = 20.dp)) {
                        if (live) {
                            LiveBadge(transportFocusable.focusRequester(scrubber))
                        } else {
                            ScrubBar(
                                controller, scrub.target,
                                barModifier = transportFocusable.focusRequester(scrubber).onKeyEvent { e ->
                                    if (e.type != KeyEventType.KeyDown) return@onKeyEvent false
                                    if (scrubKey(e)) return@onKeyEvent true
                                    when (e.key) {
                                        Key.DirectionCenter, Key.Enter -> { if (scrub.active) scrub.commit() else togglePlay(); true }
                                        Key.DirectionDown -> { open(Menu.INFO, scrubber); true }
                                        else -> false
                                    }
                                },
                            )
                        }
                    }
                }
            }
        }

        skip?.takeIf { error == null }?.let { action ->
            SkipPill(
                action,
                Modifier.align(Alignment.BottomEnd).padding(end = 64.dp, bottom = if (visible) 200.dp else 64.dp)
                    .then(transportFocusable).focusRequester(pill)
                    .onFocusChanged { pillFocused = it.isFocused }
                    .onKeyEvent { e -> e.type == KeyEventType.KeyDown && scrubKey(e) },
            ) { activate(action) }
        }

        // Menus: popovers above the buttons, Info across the top.
        AnimatedVisibility(menu == Menu.INFO, Modifier.align(Alignment.TopCenter),
                           enter = slideInVertically { -it } + fadeIn(), exit = slideOutVertically { -it } + fadeOut()) {
            InfoPanel(controller.request, Modifier.padding(48.dp).onKeyEvent { e ->
                if (e.type == KeyEventType.KeyDown && e.key in setOf(Key.DirectionUp, Key.DirectionDown)) menu = null
                e.key in setOf(Key.DirectionUp, Key.DirectionDown, Key.DirectionLeft, Key.DirectionRight)
            })
        }
        val popover = Modifier.align(Alignment.BottomEnd).padding(end = 64.dp, bottom = 190.dp)
        when (menu) {
            Menu.AUDIO -> TrackMenu(controller, audio = true, onDone = { menu = null }, modifier = popover)
            Menu.SUBTITLES -> TrackMenu(controller, audio = false, onDone = { menu = null }, modifier = popover,
                                        onStyle = { menu = Menu.SUBTITLE_STYLE },
                                        onSearch = { menu = Menu.SUBTITLE_SEARCH }.takeIf { !live },
                                        onTranslate = { menu = Menu.SUBTITLE_TRANSLATE }.takeIf { !live })
            Menu.SUBTITLE_TRANSLATE -> TranslateMenu(controller, onDone = { menu = null }, modifier = popover)
            Menu.SUBTITLE_SEARCH -> SubtitleSearchPanel(controller, onDone = { menu = null },
                                                        modifier = Modifier.align(Alignment.CenterEnd).padding(end = 64.dp))
            Menu.SUBTITLE_STYLE -> SubtitleStyleMenu(popover)
            Menu.SPEED -> SpeedMenu(controller, onDone = { menu = null }, modifier = popover)
            Menu.SOURCES -> SourceSwitchMenu(controller, onDone = { menu = null }, modifier = popover)
            else -> Unit
        }
    }
}

/** In place of the scrubber on a live channel; focusable so the chrome has somewhere to start. */
@Composable
private fun LiveBadge(modifier: Modifier) {
    Text(
        "LIVE", fontWeight = FontWeight.Black, style = MaterialTheme.typography.labelLarge,
        modifier = modifier.focusable().background(Color(0xFFE5332A), RoundedCornerShape(50)).padding(horizontal = 12.dp, vertical = 4.dp),
    )
}

@Composable
private fun ErrorPanel(message: String, onExit: () -> Unit, modifier: Modifier) {
    val focus = remember { FocusRequester() }
    LaunchedEffect(Unit) { runCatching { focus.requestFocus() } }
    Column(
        modifier.background(Color.Black.copy(alpha = 0.8f), RoundedCornerShape(16.dp)).padding(28.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Text("Playback Error", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
        Text(message, color = Color.White.copy(alpha = 0.7f))
        androidx.tv.material3.Button(onClick = onExit, modifier = Modifier.focusRequester(focus)) { Text("Close") }
    }
}

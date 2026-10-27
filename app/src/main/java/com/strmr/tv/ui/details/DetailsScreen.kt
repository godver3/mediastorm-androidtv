package com.strmr.tv.ui.details

import android.os.Build
import androidx.annotation.OptIn
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.BlurEffect
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.TileMode
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.compose.PlayerSurface
import androidx.media3.ui.compose.SURFACE_TYPE_TEXTURE_VIEW
import androidx.media3.ui.compose.modifiers.resizeWithContentScale
import androidx.media3.ui.compose.state.rememberPresentationState
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.strmr.tv.api.Artwork
import com.strmr.tv.api.DetailsTarget
import com.strmr.tv.api.Episode
import com.strmr.tv.api.SourceResult
import com.strmr.tv.api.StartPoint
import com.strmr.tv.library.CustomListsStore
import com.strmr.tv.library.LibraryVersion
import com.strmr.tv.player.ManualSourceTracks
import com.strmr.tv.player.PlaybackCoordinator
import com.strmr.tv.ui.ChoiceDialog
import com.strmr.tv.ui.DialogChoice
import com.strmr.tv.ui.WithDefaultScroll
import com.strmr.tv.ui.WithFoldScroll
import com.strmr.tv.ui.library.LibraryVersionsDialog
import com.strmr.tv.ui.settings.SpoilerSettings
import kotlinx.coroutines.launch

/** The picker's subject: the movie, or one episode. */
private data class PickerTarget(val episode: Episode?)

/** A picked source waiting on the Resume / Start Over choice. */
private data class PendingSource(val source: SourceResult, val episode: Episode?, val resumeAt: StartPoint, val manual: ManualSourceTracks?)

/** A library copy picked in Versions, waiting on the Resume / Start Over choice. */
private data class PendingVersion(val version: LibraryVersion, val episode: Episode?, val resumeAt: StartPoint)

/**
 * A title's page, as on tvOS: a full-bleed backdrop (the trailer plays over it), the header
 * anchored in the lower left of the first screen, seasons and episodes below the fold.
 */
@Composable
fun DetailsScreen(target: DetailsTarget, openWatchState: Boolean = false, autoplay: Boolean = false) {
    val model = remember(target) { DetailsModel(target) }
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    val trailer = remember { TrailerController(context, scope) }
    val sessionsEnded by PlaybackCoordinator.sessionsEnded.collectAsState()
    val resolving by PlaybackCoordinator.resolving.collectAsState()
    val warm by PlaybackCoordinator.warm.collectAsState()
    val prequeueTracks = remember(model) { PrequeueTracks(model) }
    var picker by remember { mutableStateOf<PickerTarget?>(null) }
    var pendingSource by remember { mutableStateOf<PendingSource?>(null) }
    /** The Versions list (movie, or a specific episode) and a copy picked there. */
    var versionsFor by remember { mutableStateOf<PickerTarget?>(null) }
    var pendingVersion by remember { mutableStateOf<PendingVersion?>(null) }
    var headerFocused by remember { mutableStateOf(true) }
    var watchState by remember { mutableStateOf(false) }
    var more by remember { mutableStateOf(false) }
    var customizingButtons by remember { mutableStateOf(false) }
    val navigator = com.strmr.tv.ui.LocalNavigator.current
    val actions = com.strmr.tv.library.TitleActions.changes
    // A long-press menu's bulk watch-state action opens the sheet once the seasons are in.
    var watchStateOpened by remember { mutableStateOf(false) }
    LaunchedEffect(model.seasons.isNotEmpty()) {
        if (openWatchState && !watchStateOpened && model.seasons.isNotEmpty()) {
            watchStateOpened = true
            watchState = true
        }
    }
    LaunchedEffect(model) { model.loadLibrary() }
    LaunchedEffect(model) { if (!target.isMovie) SpoilerSettings.refresh() }
    // A title action taken elsewhere (a menu over this page, another page): show its new state.
    LaunchedEffect(actions) { if (actions > 0) model.load() }

    DisposableEffect(Unit) { onDispose { trailer.release() } }
    // A home screen row's play link: the primary action once, when the first load has it.
    var autoplayed by remember { mutableStateOf(!autoplay) }
    LaunchedEffect(sessionsEnded) {
        // The picks were for the prequeue that just played.
        if (sessionsEnded > 0) prequeueTracks.clear()
        model.load()
        val primary = model.primary
        if (!autoplayed && primary != null) {
            autoplayed = true
            model.play(primary.episode, primary.start)
            return@LaunchedEffect
        }
        autoplayed = true
        trailer.prepare(model.trailers?.primary)
    }
    // The backdrop trailer stops for good once playback starts or focus leaves the header.
    LaunchedEffect(resolving, headerFocused) {
        if (resolving != null) trailer.dismiss("playback starting") else if (!headerFocused) trailer.dismiss("header lost focus")
    }
    // Any of the page's menus is up: the source picker (its track panel included) or its Resume
    // prompt, Audio & Subtitles, Add to List…, the watch-state screen, or More. They are dialog windows, so the
    // header keeps its focus state under them; this one flag stops the trailer for the visit
    // instead (as the Expo page's dismissTrailerAutoPlay; it doesn't restart when they close).
    val menuOpen = picker != null || pendingSource != null || versionsFor != null || pendingVersion != null ||
        prequeueTracks.open || watchState || more || customizingButtons || CustomListsStore.picking != null
    LaunchedEffect(menuOpen) { if (menuOpen) trailer.dismiss("menu open") }

    fun chooseVersion(version: LibraryVersion, episode: Episode?) {
        val resumeAt = model.resumePoint(episode)
        if (resumeAt != null) pendingVersion = PendingVersion(version, episode, resumeAt) else model.playVersion(version, episode, null)
    }

    fun choose(source: SourceResult, episode: Episode?, manual: ManualSourceTracks?) {
        val resumeAt = model.resumePoint(episode)
        if (resumeAt != null) pendingSource = PendingSource(source, episode, resumeAt, manual) else model.play(source, episode, null, manual)
    }

    // Shared by the row's buttons and, for those moved there, More.
    val startOver = { p: DetailsModel.Primary -> model.play(p.episode, null, prequeueTracks.pick(warm)) }
    val openVersions = { versionsFor = PickerTarget(if (target.isMovie) null else model.primaryEpisode) }
    val chooseSource = { picker = PickerTarget(if (target.isMovie) null else model.primaryEpisode) }

    BoxWithConstraints(
        Modifier.fillMaxSize().background(Color.Black).onPreviewKeyEvent { e ->
            // Play/Pause stops the backdrop trailer, as opening a menu does, rather than leaving
            // a paused frame behind the header.
            if (trailer.showing && e.key in PlayPauseKeys) {
                if (e.type == KeyEventType.KeyDown) trailer.dismiss("play/pause")
                true
            } else if (trailer.immersive && e.type == KeyEventType.KeyDown) {
                // Any press over the immersive trailer brings the page back first.
                trailer.wake()
                true
            } else false
        },
    ) {
        val screenHeight = maxHeight
        val backdropAlpha by animateFloatAsState(if (trailer.showing) 0f else 1f, tween(400), label = "backdrop")
        val pageScroll = rememberScrollState()
        // How far the page is scrolled toward the rows (0 at the header, 1 by half a screen down),
        // read in the draw phase so scrolling doesn't recompose the page.
        val foldPx = with(LocalDensity.current) { screenHeight.toPx() } * 0.5f
        val belowFold = { (pageScroll.value / foldPx).coerceIn(0f, 1f) }
        AsyncImage(
            model = ImageRequest.Builder(context).data(Artwork.url(model.title?.backdrop?.url ?: target.backdropUrl)).crossfade(300).build(),
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = Modifier.fillMaxSize().alpha(backdropAlpha).graphicsLayer {
                // Below the fold the backdrop blurs behind the rows (Android 12+; older ones only dim).
                val blur = belowFold() * BackdropBlur.toPx()
                renderEffect = if (Build.VERSION.SDK_INT >= 31 && blur > 0f) BlurEffect(blur, blur, TileMode.Clamp) else null
            },
        )
        Box(Modifier.fillMaxSize().drawBehind { drawRect(Color.Black.copy(alpha = belowFold() * BackdropDim)) })
        trailer.player?.let { TrailerVideo(it) }
        BackdropShade(hidden = trailer.immersive)

        val pageAlpha by animateFloatAsState(if (trailer.immersive) 0f else 1f, tween(400), label = "page")
        WithFoldScroll(pageScroll) { rowScroll ->
        Column(Modifier.fillMaxSize().alpha(pageAlpha).verticalScroll(pageScroll)) {
            // The header fades as it scrolls up, so the actions don't linger above the rows.
            Box(Modifier.fillMaxWidth().height(screenHeight).graphicsLayer { alpha = 1f - belowFold() }) {
                DetailsHeader(
                    model, trailer,
                    onPlay = { p -> model.play(p.episode, p.start, prequeueTracks.pick(warm)) },
                    onStartOver = startOver,
                    onVersions = openVersions,
                    onChooseSource = chooseSource,
                    prequeueTracks = prequeueTracks,
                    warm = warm,
                    onWatchState = { watchState = true },
                    onMore = { more = true },
                    onFocusChanged = { headerFocused = it },
                )
            }
            WithDefaultScroll(rowScroll) {
                if (!target.isMovie && model.seasons.isNotEmpty()) {
                    EpisodesSection(
                        model,
                        onPlay = { ep -> model.play(ep, model.resumePoint(ep), prequeueTracks.pick(warm)) },
                        onChooseSource = { ep -> picker = PickerTarget(ep) },
                        onChooseVersion = { ep -> versionsFor = PickerTarget(ep) },
                        onToggleWatched = { ep -> scope.launch { model.toggleWatched(ep) } },
                        modifier = Modifier.padding(top = 24.dp),
                    )
                }
                RelatedRows(model, Modifier.padding(top = 24.dp, bottom = 48.dp))
            }
        }
        }
    }

    PrequeueTracksPanel(prequeueTracks, warm) {
        model.primary?.let { p -> model.play(p.episode, p.start, prequeueTracks.pick(warm)) }
    }
    if (watchState) WatchStateDialog(model, onChanged = { com.strmr.tv.library.TitleActions.changed() }, onDismiss = { watchState = false })
    if (more) {
        val moved = movedActions(
            model, trailer, prequeueTracks, warm, scope,
            onStartOver = startOver, onVersions = openVersions, onChooseSource = chooseSource, onWatchState = { watchState = true },
        )
        DetailsMoreMenu(target, scope, moved, onCustomize = { customizingButtons = true }, onHidden = { navigator.close() }, onDismiss = { more = false })
    }
    if (customizingButtons) DetailsButtonsDialog(onDismiss = { customizingButtons = false })
    picker?.let { p ->
        SourcePicker(target, p.episode, onPick = { source, manual -> choose(source, p.episode, manual) }, onDismiss = { picker = null })
    }
    versionsFor?.let { v ->
        LibraryVersionsDialog(
            v.episode?.let { "${target.name} · ${it.code}" } ?: target.name, model.libraryVersions(v.episode),
            onPick = { chooseVersion(it, v.episode) }, onDismiss = { versionsFor = null },
        )
    }
    pendingVersion?.let { pending ->
        ChoiceDialog(
            title = pending.episode?.let { "${target.name} · ${it.code}" } ?: target.name,
            choices = listOf(
                DialogChoice("Resume from ${pending.resumeAt.label}") { model.playVersion(pending.version, pending.episode, pending.resumeAt) },
                DialogChoice("Start Over") { model.playVersion(pending.version, pending.episode, null) },
                DialogChoice("Cancel") {},
            ),
            onDismiss = { pendingVersion = null },
        )
    }
    pendingSource?.let { pending ->
        ChoiceDialog(
            title = pending.episode?.let { "${target.name} · ${it.code}" } ?: target.name,
            choices = listOf(
                DialogChoice("Resume from ${pending.resumeAt.label}") { model.play(pending.source, pending.episode, pending.resumeAt, pending.manual) },
                DialogChoice("Start Over") { model.play(pending.source, pending.episode, null, pending.manual) },
                DialogChoice("Cancel") {},
            ),
            onDismiss = { pendingSource = null },
        )
    }
}

private val BackdropBlur = 24.dp
private val PlayPauseKeys = setOf(Key.MediaPlayPause, Key.MediaPause, Key.MediaPlay)
private val BackdropDim = if (Build.VERSION.SDK_INT >= 31) 0.2f else 0.45f

/** The backdrop trailer, cropped to fill like the image under it. */
@OptIn(UnstableApi::class)
@Composable
private fun TrailerVideo(player: ExoPlayer) {
    val presentation = rememberPresentationState(player)
    PlayerSurface(
        player = player,
        surfaceType = SURFACE_TYPE_TEXTURE_VIEW,
        modifier = Modifier.fillMaxSize().resizeWithContentScale(ContentScale.Crop, presentation.videoSizeDp),
    )
}

/**
 * Bottom-weighted for the rows and darker on the left for the header text; fades out with the
 * page while the trailer is immersive, so nothing is left over the video. Drawn once into a
 * cached layer (as on Home): redrawn as gradient shaders under every trailer frame, they
 * overwhelm a Fire TV's GPU.
 */
@Composable
private fun BackdropShade(hidden: Boolean) {
    val alpha by animateFloatAsState(if (hidden) 0f else 1f, tween(400), label = "shade")
    Box(Modifier.fillMaxSize().graphicsLayer { this.alpha = alpha; compositingStrategy = CompositingStrategy.Offscreen }) {
        Box(
            Modifier.fillMaxSize().background(
                Brush.verticalGradient(0f to Color.Transparent, 0.45f to Color.Black.copy(alpha = 0.35f), 0.8f to Color.Black.copy(alpha = 0.9f), 1f to Color.Black),
            ),
        )
        Box(Modifier.fillMaxSize().background(Brush.horizontalGradient(listOf(Color.Black.copy(alpha = 0.8f), Color.Black.copy(alpha = 0.2f), Color.Transparent))))
    }
}

package com.strmr.mobile.ui.details

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import com.strmr.mobile.ui.library.LibraryVersionsSheet
import com.strmr.mobile.ui.library.ResumeDialog
import com.strmr.tv.player.WarmPrequeue
import com.strmr.tv.player.releaseTitle
import com.strmr.tv.ui.details.ProbeState
import com.strmr.tv.player.trackLists
import kotlinx.coroutines.CoroutineScope

/**
 * The details page's sheets and prompts: More, an episode's menu, Episode Order, Watch State,
 * the prequeued stream's Audio & Subtitles, Choose Source, Versions (and Play's copy choice
 * with Stream Instead), and Resume / Start Over after a manual pick.
 */
@Composable
fun DetailsPresentations(state: DetailsPageState, warm: WarmPrequeue?, scope: CoroutineScope, extras: DetailsActionContext, onHidden: () -> Unit) {
    val model = state.model
    if (state.more) DetailsMoreSheet(state, warm, scope, extras, onHidden)
    state.episodeMenu?.let { EpisodeMenuSheet(state, it, scope, extras) }
    if (state.episodeOrder) EpisodeOrderSheet(state, scope)
    if (state.watchState) WatchStateSheet(model, state.activeEpisode) { state.watchState = false }

    val tracks = state.prequeueTracks
    if (tracks.open) {
        val status = tracks.visible(warm)?.tracks
        if (status == null) {
            // The prequeue moved on (another episode selected): nothing to show.
            LaunchedEffect(Unit) { tracks.open = false }
        } else {
            TracksSheet(
                release = status.releaseTitle ?: model.target.name,
                state = ProbeState(status.trackLists),
                defaults = tracks.defaults(status),
                choice = tracks.choiceFor(status),
                preselected = tracks.preselected(status),
                playLabel = state.primary?.label ?: "Play",
                onChoice = { tracks.choose(status, it) },
                onRetry = null,
                onPlay = {
                    tracks.open = false
                    state.playPrimary()
                },
                onDismiss = { tracks.open = false },
            )
        }
    }

    state.sourcesFor?.let { picker ->
        SourcePicker(
            model.target, picker.episode, model.libraryVersions(picker.episode),
            onPick = { source, manual -> state.choose(source, picker.episode, manual) },
            onPickLibrary = { state.chooseVersion(it, picker.episode) },
            onDismiss = { state.sourcesFor = null },
        )
    }
    state.versionsFor?.let { v ->
        LibraryVersionsSheet(
            state.heading(v.episode), model.libraryVersions(v.episode),
            onPick = { state.chooseVersion(it, v.episode) }, onDismiss = { state.versionsFor = null },
        )
    }
    model.playChoice?.let { choice ->
        LibraryVersionsSheet(
            state.heading(choice.episode), model.libraryVersions(choice.episode),
            onPick = { model.playVersion(it, choice.episode, choice.start) }, onDismiss = { model.playChoice = null },
            onStream = { model.streamInstead(choice) },
        )
    }
    state.pendingVersion?.let { p ->
        ResumeDialog(state.heading(p.episode), p.resumeAt, onPlay = { model.playVersion(p.version, p.episode, it) }) { state.pendingVersion = null }
    }
    state.pendingSource?.let { p ->
        ResumeDialog(state.heading(p.episode), p.resumeAt, onPlay = { model.play(p.source, p.episode, it, p.manual) }) { state.pendingSource = null }
    }
}

package com.strmr.tv.ui.library

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import com.strmr.tv.api.ApiClient
import com.strmr.tv.api.ApiError
import com.strmr.tv.api.DetailsTarget
import com.strmr.tv.api.LibraryEntry
import com.strmr.tv.api.LibraryGroup
import com.strmr.tv.api.LibraryPlaybackInfo
import com.strmr.tv.api.StartPoint
import com.strmr.tv.api.libraryGroup
import com.strmr.tv.api.libraryPlayback
import com.strmr.tv.api.resumeState
import com.strmr.tv.library.LibraryVersion
import com.strmr.tv.library.LibraryVersions
import com.strmr.tv.player.PlaybackCoordinator
import com.strmr.tv.player.PlaybackRequest
import com.strmr.tv.player.playLibrary
import com.strmr.tv.ui.ChoiceDialog
import com.strmr.tv.ui.DialogChoice
import com.strmr.tv.ui.LocalNavigator
import com.strmr.tv.ui.Page
import kotlinx.coroutines.launch

/** Resume / Start Over for a library file left partway. */
@Composable
fun LibraryResumeDialog(start: LibraryFileStart, onDismiss: () -> Unit) {
    val at = start.resumeAt ?: return
    ChoiceDialog(
        title = start.episode?.let { "${start.group.title} · ${it.code}" } ?: start.group.title,
        choices = listOf(
            DialogChoice("Resume from ${at.label}") { start.play(at) },
            DialogChoice("Start Over") { start.play(null) },
            DialogChoice("Cancel") {},
        ),
        onDismiss = onDismiss,
    )
}

/**
 * What selecting an unmatched library title does (it has no details page): a show opens its library
 * episodes; a file (a home video) plays at once, after a choice of copies when it has several and
 * Resume / Start Over when it was left partway. Returns the handler; its dialogs are drawn here.
 */
@Composable
fun rememberLibraryOpener(): (LibraryEntry) -> Unit {
    val navigator = LocalNavigator.current
    val scope = rememberCoroutineScope()
    var choice by remember { mutableStateOf<Pair<LibraryGroup, LibraryVersions>?>(null) }
    var pending by remember { mutableStateOf<LibraryFileStart?>(null) }
    var busy by remember { mutableStateOf(false) }

    suspend fun start(version: LibraryVersion, versions: LibraryVersions, group: LibraryGroup) {
        try {
            val prepared = LibraryFileStart.prepare(version, versions, group)
            if (prepared.resumeAt != null) pending = prepared else prepared.play(null)
        } catch (e: Exception) {
            PlaybackCoordinator.errorMessage.value = e.message ?: "Playback failed"
        }
    }

    choice?.let { (group, versions) ->
        LibraryVersionsDialog(group.title, versions.movie, onPick = { scope.launch { start(it, versions, group) } }, onDismiss = { choice = null })
    }
    pending?.let { LibraryResumeDialog(it) { pending = null } }

    return { entry ->
        if (entry.group.isShow) {
            navigator.open(Page.LibraryShow(entry))
        } else if (!busy) {
            busy = true
            scope.launch {
                try {
                    // Rows and grids carry no files: fetch them, then play the only copy or offer the choice.
                    val group = ApiClient.libraryGroup(entry)
                    val versions = LibraryVersions.of(group, entry.library.name)
                    val only = versions.movie.singleOrNull()
                    when {
                        only != null -> start(only, versions, group)
                        versions.movie.isNotEmpty() -> choice = group to versions
                        else -> throw ApiError.NotReady("This title has no playable file.")
                    }
                } catch (e: Exception) {
                    PlaybackCoordinator.errorMessage.value = e.message ?: "Playback failed"
                } finally {
                    busy = false
                }
            }
        }
    }
}

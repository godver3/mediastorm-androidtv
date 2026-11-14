package com.strmr.tv.ui

import androidx.compose.runtime.*
import com.strmr.tv.api.DetailsTarget
import com.strmr.tv.app.AppSession
import com.strmr.tv.ratings.ServerTitleRatingGateway
import com.strmr.tv.ratings.TitleRatingEditor

/** A shell-owned prompt outlives the long-press menu that opened it. */
object TitleRatingPrompt {
    var editor by mutableStateOf<TitleRatingEditor?>(null)
        private set
    fun open(value: TitleRatingEditor) {
        if (!value.isCurrent()) return
        editor?.close()
        editor = value
    }
    fun dismiss() { editor?.close(); editor = null }
}

/** Capture account/profile/server when the menu is composed, before its click. */
@Composable
fun rememberRatingAction(target: DetailsTarget): () -> Unit {
    val profile by AppSession.profile.collectAsState()
    val server by AppSession.serverUrl.collectAsState()
    val phase by AppSession.phase.collectAsState()
    val gateway = remember(target.titleId, target.isMovie, profile?.id, server, phase) { ServerTitleRatingGateway() }
    return { TitleRatingPrompt.open(TitleRatingEditor(target, gateway)) }
}

/** Profile switches and sign-out hide the prompt before old responses can land. */
@Composable
fun currentRatingEditor(): TitleRatingEditor? {
    val profile by AppSession.profile.collectAsState()
    val server by AppSession.serverUrl.collectAsState()
    val phase by AppSession.phase.collectAsState()
    val editor = TitleRatingPrompt.editor ?: return null
    val current = profile != null && server != null && phase == AppSession.Phase.Ready && editor.isCurrent()
    LaunchedEffect(editor, current) { if (!current) TitleRatingPrompt.dismiss() }
    if (!current) return null
    DisposableEffect(editor) { onDispose { editor.close() } }
    LaunchedEffect(editor) { editor.load() }
    return editor
}

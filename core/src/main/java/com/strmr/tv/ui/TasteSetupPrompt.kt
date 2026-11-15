package com.strmr.tv.ui

import androidx.compose.runtime.*
import com.strmr.tv.api.ApiClient
import com.strmr.tv.app.AppSession
import com.strmr.tv.player.PlaybackCoordinator
import com.strmr.tv.library.CustomListsStore
import com.strmr.tv.taste.*

/** Main-thread state only; keys and tokens are never logged or persisted. */
object TasteSetupPrompt {
    var editor by mutableStateOf<TasteOnboardingEditor?>(null)
        private set
    private var offerIdentity: List<Any?>? = null
    private var offer = TasteSetupOffer()
    fun open(value: TasteOnboardingEditor) {
        if (!value.isCurrent()) return
        editor?.close(); editor = value
    }
    fun dismiss() { editor?.close(); editor = null }
    suspend fun offerIfNew(visible: () -> Boolean) {
        if (editor != null || !visible()) return
        val identity = listOf(ApiClient.profileId, ApiClient.baseUrl, ApiClient.session?.accountId, ApiClient.session?.token, ApiClient.remoteCredential)
        if (identity != offerIdentity) { offerIdentity = identity; offer = TasteSetupOffer() }
        val gateway = ServerTasteSetupGateway()
        offer.check(gateway, { visible() && editor == null && TitleRatingPrompt.editor == null }) { reply ->
            if (editor == null && gateway.isCurrent()) open(TasteOnboardingEditor(gateway, initial = reply, intro = true))
        }
    }
}

@Composable
fun rememberTasteSetupAction(): () -> Unit {
    val profile by AppSession.profile.collectAsState()
    val server by AppSession.serverUrl.collectAsState()
    val phase by AppSession.phase.collectAsState()
    val gateway = remember(profile?.id, server, phase) { ServerTasteSetupGateway() }
    return { TasteSetupPrompt.open(TasteOnboardingEditor(gateway)) }
}

/** Only offer on a visible Home, outside playback and other title dialogs. */
@Composable
fun TasteSetupHomeOffer(visible: Boolean) {
    val profile by AppSession.profile.collectAsState()
    val server by AppSession.serverUrl.collectAsState()
    val phase by AppSession.phase.collectAsState()
    val player by PlaybackCoordinator.playerVisibility.collectAsState()
    val resolving by PlaybackCoordinator.resolving.collectAsState()
    val active = visible && !player && resolving == null && phase == AppSession.Phase.Ready && profile != null && TitleRatingPrompt.editor == null && CustomListsStore.picking == null
    val currentVisible by rememberUpdatedState(active)
    LaunchedEffect(active, profile?.id, server, phase) {
        if (currentVisible) TasteSetupPrompt.offerIfNew { currentVisible }
    }
}

@Composable
fun currentTasteSetupEditor(): TasteOnboardingEditor? {
    val profile by AppSession.profile.collectAsState()
    val server by AppSession.serverUrl.collectAsState()
    val phase by AppSession.phase.collectAsState()
    val editor = TasteSetupPrompt.editor ?: return null
    val state by editor.state.collectAsState()
    val current = profile != null && server != null && phase == AppSession.Phase.Ready && editor.isCurrent() && !state.finished
    LaunchedEffect(editor, current) { if (!current) TasteSetupPrompt.dismiss() }
    if (!current) return null
    DisposableEffect(editor) { onDispose { editor.close() } }
    LaunchedEffect(editor) { editor.load() }
    return editor
}

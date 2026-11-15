package com.strmr.tv.postplay

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import com.strmr.tv.launcher.DeepLink
import com.strmr.tv.launcher.DeepLinkRouter
import com.strmr.tv.library.ProfileDataContext
import com.strmr.tv.player.PlayerController

/** Wait for the canonical progress acknowledgement before opening the scoped end context. */
@Composable
fun rememberPostPlayEditor(controller: PlayerController, onClose: () -> Unit): PostPlayEditor? {
    val request by controller.postPlay.collectAsState()
    val epoch by ProfileDataContext.changes.collectAsState()
    var ready by remember(controller) { mutableStateOf(false) }
    var itemId by remember(controller) { mutableStateOf<String?>(null) }
    LaunchedEffect(controller,request,epoch) {
        if (request == null) return@LaunchedEffect
        if (epoch != controller.dataContext) { onClose(); return@LaunchedEffect }
        val canonical = controller.completedItemId()
        if (ProfileDataContext.current != controller.dataContext) { onClose(); return@LaunchedEffect }
        itemId = canonical; ready = true
    }
    val editor = remember(controller,request,ready,itemId,epoch) {
        request?.takeIf { ready && epoch == controller.dataContext }?.let { completed ->
            PostPlayEditor(completed.title, ServerPostPlayGateway(completed.title) { controller.storedCompletedItemId ?: itemId }, navigate = { target,play ->
                val link = DeepLink(if (play) DeepLink.Action.PLAY else DeepLink.Action.DETAILS,target,controller.profileId)
                if (DeepLinkRouter.openInternal(link,controller.dataContext)) onClose()
            })
        }
    }
    DisposableEffect(editor) { onDispose { editor?.close() } }
    LaunchedEffect(editor) { editor?.load() }
    return editor
}

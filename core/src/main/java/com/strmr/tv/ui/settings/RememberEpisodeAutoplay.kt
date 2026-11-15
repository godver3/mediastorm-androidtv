package com.strmr.tv.ui.settings

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import com.strmr.tv.library.ProfileDataContext

@Composable
fun rememberEpisodeAutoplayEditor(): EpisodeAutoplayEditor {
    val editor = remember(ProfileDataContext.current) { EpisodeAutoplayEditor(ServerEpisodeAutoplayGateway()) }
    LaunchedEffect(editor) { editor.load() }
    DisposableEffect(editor) { onDispose { editor.close() } }
    return editor
}

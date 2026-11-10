package com.strmr.mobile.ui.downloads

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.RowScope
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LargeTopAppBar
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.text.style.TextOverflow

/**
 * The Downloads pages' frame: the shell's page look (a large collapsing bar at a root, a small
 * one on a sub page) with an explicit back action, because these pages also show outside the
 * signed-in shell (offline, before sign-in) where there is no navigator.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DownloadPage(
    title: String,
    onBack: (() -> Unit)?,
    large: Boolean = onBack == null,
    backIcon: @Composable () -> Unit = { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back") },
    topInset: @Composable () -> Unit = {},
    actions: @Composable RowScope.() -> Unit = {},
    content: @Composable (PaddingValues) -> Unit,
) {
    val behavior = if (large) TopAppBarDefaults.exitUntilCollapsedScrollBehavior() else TopAppBarDefaults.pinnedScrollBehavior()
    val navigation: @Composable () -> Unit = { onBack?.let { IconButton(onClick = it, content = backIcon) } }
    Scaffold(
        modifier = Modifier.nestedScroll(behavior.nestedScrollConnection),
        topBar = {
            androidx.compose.foundation.layout.Column {
                if (large) {
                    LargeTopAppBar(title = { Text(title, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                                   navigationIcon = navigation, actions = actions, scrollBehavior = behavior)
                } else {
                    TopAppBar(title = { Text(title, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                              navigationIcon = navigation, actions = actions, scrollBehavior = behavior)
                }
                topInset()
            }
        },
        content = content,
    )
}

package com.strmr.mobile.ui.shell

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.strmr.tv.ui.AppToast

/** The shared confirmations ("Hidden from this profile") as Material snackbars, above the navigation bar. */
@Composable
fun ToastHost() {
    val state = remember { SnackbarHostState() }
    val message = AppToast.message
    LaunchedEffect(AppToast.shown) {
        val text = message ?: return@LaunchedEffect
        state.currentSnackbarData?.dismiss()
        state.showSnackbar(text)
        AppToast.clear()
    }
    Box(Modifier.fillMaxSize().navigationBarsPadding().padding(bottom = 80.dp), contentAlignment = Alignment.BottomCenter) {
        SnackbarHost(state)
    }
}

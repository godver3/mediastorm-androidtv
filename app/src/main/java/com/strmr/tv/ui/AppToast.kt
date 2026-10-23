package com.strmr.tv.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import kotlinx.coroutines.delay

/** A short confirmation over the app ("Hidden from this profile"), as the Expo app's toasts. Main thread only. */
object AppToast {
    var message by mutableStateOf<String?>(null)
        private set
    private var shown by mutableIntStateOf(0)

    fun show(text: String) {
        message = text
        shown++
    }

    /** Drawn by the top window; clears itself after a few seconds. */
    @Composable
    fun Host() {
        LaunchedEffect(shown) {
            if (message == null) return@LaunchedEffect
            delay(2_500)
            message = null
        }
        var last by androidx.compose.runtime.remember { mutableStateOf("") }
        message?.let { last = it }
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.BottomCenter) {
            AnimatedVisibility(message != null, enter = fadeIn(), exit = fadeOut()) {
                Text(
                    last, style = MaterialTheme.typography.titleSmall, color = Color.White,
                    modifier = Modifier.padding(bottom = 48.dp).background(Color(0xE6242426), RoundedCornerShape(50))
                        .padding(horizontal = 22.dp, vertical = 12.dp),
                )
            }
        }
    }
}

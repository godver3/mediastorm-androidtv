package com.strmr.tv.ui

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

/**
 * A short confirmation over the app ("Hidden from this profile"), as the Expo app's toasts.
 * Main thread only. Each app draws it (TV: `AppToastHost`); the host clears it.
 */
object AppToast {
    var message by mutableStateOf<String?>(null)
        private set

    /** Bumped on every [show], so a host restarts its timer for a repeated message. */
    var shown by mutableIntStateOf(0)
        private set

    fun show(text: String) {
        message = text
        shown++
    }

    fun clear() {
        message = null
    }
}

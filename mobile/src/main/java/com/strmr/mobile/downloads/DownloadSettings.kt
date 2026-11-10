package com.strmr.mobile.downloads

import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.strmr.tv.app.App
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/**
 * Settings › Downloads, kept on this device (as the iPhone and Expo apps keep them): Wi-Fi
 * only, simultaneous downloads (1–3), delete once watched, and the Downloads list's sort.
 * Compose state for the settings page; [changes] for the queue.
 */
object DownloadSettings {
    enum class Sort(val label: String) {
        NEWEST("Newest First"),
        OLDEST("Oldest First"),
        NAME_ASCENDING("Name (A–Z)"),
        NAME_DESCENDING("Name (Z–A)"),
    }

    private val prefs by lazy { App.context.getSharedPreferences("strmr.downloads", Context.MODE_PRIVATE) }
    private val _changes = MutableStateFlow(0)
    /** Bumped when a setting the queue follows changes. */
    val changes: StateFlow<Int> = _changes

    private var wifiOnlyState by mutableStateOf(prefs.getBoolean("wifiOnly", false))
    private var maxConcurrentState by mutableStateOf(prefs.getInt("maxConcurrent", 1).coerceIn(1, 3))
    private var deleteWatchedState by mutableStateOf(prefs.getBoolean("deleteWatched", false))
    private var sortState by mutableStateOf(
        prefs.getString("sort", null)?.let { runCatching { Sort.valueOf(it) }.getOrNull() } ?: Sort.NEWEST,
    )

    var wifiOnly: Boolean
        get() = wifiOnlyState
        set(value) {
            wifiOnlyState = value
            prefs.edit().putBoolean("wifiOnly", value).apply()
            _changes.value += 1
        }

    var maxConcurrent: Int
        get() = maxConcurrentState
        set(value) {
            maxConcurrentState = value.coerceIn(1, 3)
            prefs.edit().putInt("maxConcurrent", maxConcurrentState).apply()
            _changes.value += 1
        }

    var deleteWatched: Boolean
        get() = deleteWatchedState
        set(value) {
            deleteWatchedState = value
            prefs.edit().putBoolean("deleteWatched", value).apply()
        }

    var sort: Sort
        get() = sortState
        set(value) {
            sortState = value
            prefs.edit().putString("sort", value.name).apply()
        }
}

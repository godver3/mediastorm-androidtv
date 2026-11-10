package com.strmr.tv.ui.settings

import android.content.Context
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.strmr.tv.api.ApiClient
import com.strmr.tv.api.badgeVisibility
import com.strmr.tv.app.App
import com.strmr.tv.home.BadgeVisibility

/**
 * Settings › Card Badges: this device's overrides of the server's `display.badgeVisibility`
 * (null follows the server: the profile's list, else the server's, else Expo's default of
 * progress bars only). Cards read [visibility] when they draw, so a change shows at once.
 * Progress bars aren't an option: they always show.
 */
object BadgeSettings {
    enum class Option(val key: String, val title: String, val detail: String) {
        WatchState("watchState", "Watch State", "Watched or in progress"),
        ReleaseStatus("releaseStatus", "Release Status", "Coming soon, unreleased or in theaters"),
        UnwatchedCount("unwatchedCount", "Unwatched Count", "Series: episodes left to watch"),
    }

    private val prefs by lazy { App.context.getSharedPreferences("strmr", Context.MODE_PRIVATE) }
    private fun prefKey(option: Option) = "badge.${option.key}"

    /** On / off on this device; absent for Default. */
    private val overrides = mutableStateMapOf<Option, Boolean>().apply {
        Option.entries.forEach { if (prefs.contains(prefKey(it))) put(it, prefs.getBoolean(prefKey(it), false)) }
    }

    /** The server's list (profile, else server); null until read, which counts as the default (all off). */
    var server by mutableStateOf<List<String>?>(null)
        private set

    fun override(option: Option): Boolean? = overrides[option]

    fun set(option: Option, value: Boolean?) {
        if (value == null) overrides.remove(option) else overrides[option] = value
        prefs.edit().apply { if (value == null) remove(prefKey(option)) else putBoolean(prefKey(option), value) }.apply()
    }

    fun serverValue(option: Option): Boolean = BadgeVisibility.shows(option.key, null, server)

    /** What cards show: this device's choices over the server's. */
    val visibility: BadgeVisibility
        get() = BadgeVisibility(
            watchState = shows(Option.WatchState),
            releaseStatus = shows(Option.ReleaseStatus),
            unwatchedCount = shows(Option.UnwatchedCount),
        )

    /** Home passes on the list it read with its settings; null keeps the last one. */
    fun updateServer(list: List<String>?) {
        if (list != null) server = list
    }

    suspend fun refresh() = updateServer(ApiClient.badgeVisibility())

    private fun shows(option: Option) = BadgeVisibility.shows(option.key, overrides[option], server)
}

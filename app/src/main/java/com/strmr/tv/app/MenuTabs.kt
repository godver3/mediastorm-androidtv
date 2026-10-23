package com.strmr.tv.app

import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.strmr.tv.api.ApiClient
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.serialization.Serializable

/**
 * A tab of the top menu that can be hidden. Home and Settings always show: Home is where the
 * app opens and Settings is where the menu is set.
 */
enum class MenuTab(val key: String, val label: String) {
    LIVE("live", "Live"),
    /** Live TV recordings, next to Live as in Expo's menu; off unless the profile or this device turns it on. */
    RECORDINGS("recordings", "Recordings"),
    WATCHLIST("watchlist", "Watchlist"),
    LISTS("lists", "Lists"),
    /** Off unless the profile or this device turns it on, as in the Expo app. */
    CALENDAR("calendar", "Calendar"),
    SEARCH("search", "Search"),
}

@Serializable private data class DisplayTabs(val navigationTabVisibility: List<String>? = null, val hideTvDrawerRail: Boolean? = null)

@Serializable private data class UserTabs(val display: DisplayTabs? = null)

/**
 * Which menu tabs show: the profile's `navigationTabVisibility` (set in the Expo app or the
 * admin UI, this device's admin override first), with this device's own choice per tab on top.
 * A null choice follows the profile.
 */
object MenuTabs {
    /** The Expo app's default `navigationTabVisibility`, for servers that send none. */
    private val serverDefault = setOf("home", "watchlist", "search", "lists", "profiles", "settings")
    private val prefs by lazy { App.context.getSharedPreferences("strmr.menuTabs", Context.MODE_PRIVATE) }

    /** The profile's visible tabs, as last loaded. */
    var profileTabs by mutableStateOf(serverDefault)
        private set
    /** This device's choices, by tab key. */
    var overrides by mutableStateOf(readOverrides())
        private set

    fun profileShows(tab: MenuTab) = profileShows(tab.key)

    fun isVisible(tab: MenuTab) = isVisible(tab.key)

    fun setOverride(tab: MenuTab, value: Boolean?) = setOverride(tab.key, value)

    /**
     * By key, for the Home views a profile can show as their own tabs (`movies`, `shows`,
     * `page-…`): off unless the profile's `navigationTabVisibility` or this device turns one on.
     */
    fun profileShows(key: String) = key in profileTabs

    fun isVisible(key: String) = overrides[key] ?: profileShows(key)

    fun setOverride(key: String, value: Boolean?) {
        overrides = if (value == null) overrides - key else overrides + (key to value)
        prefs.edit().apply {
            if (value == null) remove("override.$key") else putBoolean("override.$key", value)
        }.apply()
    }

    /** Shows the profile's last loaded list at once, then asks the server for its current one. */
    suspend fun refresh(profileId: String) {
        prefs.getStringSet("profile.$profileId", null)?.let { profileTabs = it }
        val loaded = load() ?: return
        profileTabs = loaded
        prefs.edit().putStringSet("profile.$profileId", loaded).apply()
    }

    /** Device override from `/api/clients/{id}/settings`, then the profile's; an empty list means unset. */
    private suspend fun load(): Set<String>? = coroutineScope {
        val api = ApiClient
        val user = async { runCatching { api.send<UserTabs>("GET", "${api.profilePath}/settings", promptOnLock = false, timeoutMs = 10_000) }.getOrNull() }
        val device = async {
            runCatching {
                api.send<DisplayTabs>("GET", "/api/clients/${api.segment(Config.clientId)}/settings", mapOf("userId" to api.profileId),
                                      promptOnLock = false, timeoutMs = 10_000)
            }.getOrNull()
        }
        val devicePrefs = device.await()
        val userPrefs = user.await()?.display
        // The side menu's rail follows the same device-then-profile order (Expo's CustomMenu).
        if (devicePrefs != null || userPrefs != null) {
            MenuStyle.profileHidesRail = devicePrefs?.hideTvDrawerRail ?: userPrefs?.hideTvDrawerRail ?: false
        }
        listOf(devicePrefs?.navigationTabVisibility, userPrefs?.navigationTabVisibility)
            .firstOrNull { !it.isNullOrEmpty() }?.toSet()
    }

    private fun readOverrides(): Map<String, Boolean> = prefs.all.mapNotNull { (key, value) ->
        if (key.startsWith("override.") && value is Boolean) key.removePrefix("override.") to value else null
    }.toMap()
}

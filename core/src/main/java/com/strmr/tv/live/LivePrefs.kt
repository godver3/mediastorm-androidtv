package com.strmr.tv.live

import android.content.Context
import com.strmr.tv.app.App

/** The Live tab's category selection, kept on this device per profile. */
object LivePrefs {
    private val prefs by lazy { App.context.getSharedPreferences("strmr.live", Context.MODE_PRIVATE) }

    fun categories(profileId: String): Set<String> = prefs.getStringSet("categories.$profileId", null)?.toSet() ?: emptySet()

    fun groupStations(profileId: String): Boolean = prefs.getBoolean("groupStations.$profileId", true)

    fun saveGroupStations(profileId: String, enabled: Boolean) =
        prefs.edit().putBoolean("groupStations.$profileId", enabled).apply()

    fun saveCategories(profileId: String, names: Set<String>) =
        prefs.edit().putStringSet("categories.$profileId", names).apply()
}

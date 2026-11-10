package com.strmr.tv.live

import android.content.Context
import com.strmr.tv.app.App

/** The Live tab's category selection, kept on this device per profile. */
object LivePrefs {
    private val prefs by lazy { App.context.getSharedPreferences("strmr.live", Context.MODE_PRIVATE) }

    fun categories(profileId: String): Set<String> = prefs.getStringSet("categories.$profileId", null)?.toSet() ?: emptySet()

    fun saveCategories(profileId: String, names: Set<String>) =
        prefs.edit().putStringSet("categories.$profileId", names).apply()
}

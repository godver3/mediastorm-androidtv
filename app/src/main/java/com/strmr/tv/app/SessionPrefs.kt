package com.strmr.tv.app

import android.content.Context
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull

/** What [AppSession] keeps across launches: the server, the last profile and the automatic one. */
internal object SessionPrefs {
    private val prefs by lazy { App.context.getSharedPreferences("strmr", Context.MODE_PRIVATE) }
    private const val KEY_SERVER = "serverUrl"
    private const val KEY_LAST_PROFILE = "lastProfileId"
    private const val KEY_AUTO_PROFILE = "autoProfileId"

    val server: HttpUrl? get() = prefs.getString(KEY_SERVER, null)?.toHttpUrlOrNull()
    val lastProfileId: String? get() = prefs.getString(KEY_LAST_PROFILE, null)
    val autoProfileId: String? get() = prefs.getString(KEY_AUTO_PROFILE, null)

    fun saveServer(url: HttpUrl) = prefs.edit().putString(KEY_SERVER, url.toString()).apply()

    fun saveLastProfile(id: String) = prefs.edit().putString(KEY_LAST_PROFILE, id).apply()

    fun saveAutoProfile(id: String?) = prefs.edit().putString(KEY_AUTO_PROFILE, id).apply()

    /** Signed out or on another server: no profile to come back to. */
    fun forgetProfiles() = prefs.edit().remove(KEY_LAST_PROFILE).remove(KEY_AUTO_PROFILE).apply()

    fun forgetAll() = prefs.edit().remove(KEY_SERVER).remove(KEY_LAST_PROFILE).remove(KEY_AUTO_PROFILE).apply()
}

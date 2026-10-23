package com.strmr.tv.app

import android.content.Context
import android.os.Build
import android.provider.Settings
import com.strmr.tv.BuildConfig
import java.util.UUID
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

/**
 * Per-install identity and build info. Server, account and profile are chosen at runtime
 * (see [AppSession]); nothing here is environment-specific.
 */
object Config {
    /**
     * Stable per-install client ID, sent as X-Client-ID and registered with
     * `POST /api/clients/register` so the backend's per-device settings and Active Streams
     * can tell this client apart. Never rotated, including on sign-out.
     */
    val clientId: String by lazy {
        val prefs = App.context.getSharedPreferences("strmr", Context.MODE_PRIVATE)
        prefs.getString("clientId", null) ?: ("strmr-androidtv-" + UUID.randomUUID().toString()).also {
            prefs.edit().putString("clientId", it).apply()
        }
    }

    const val DEVICE_TYPE = "Android TV"

    val osVersion: String get() = "Android ${Build.VERSION.RELEASE} (API ${Build.VERSION.SDK_INT})"

    /** `version+build`, as registration and log uploads report it. */
    val fullVersion: String get() = "${BuildConfig.VERSION_NAME}+${BuildConfig.VERSION_CODE}"

    /** The name the owner gave the device in system settings, else its model. */
    val deviceName: String
        get() = Settings.Global.getString(App.context.contentResolver, Settings.Global.DEVICE_NAME)
            ?.ifBlank { null } ?: "${Build.MANUFACTURER} ${Build.MODEL}"

    /**
     * Payload for `POST /api/clients/register`. Nickname is left out so routine
     * re-registration never overwrites one set in the admin UI.
     */
    fun registration(profileId: String): JsonObject = buildJsonObject {
        put("id", clientId)
        put("userId", profileId)
        put("deviceName", deviceName)
        put("deviceType", DEVICE_TYPE)
        put("os", osVersion)
        put("appVersion", fullVersion)
    }
}

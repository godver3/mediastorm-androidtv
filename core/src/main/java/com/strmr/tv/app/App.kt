package com.strmr.tv.app

import android.content.Context

/**
 * What differs between the apps built on this module (Android TV, phone and tablet): set once
 * by each app's Application before anything else runs.
 */
data class Platform(
    val versionName: String,
    val versionCode: Int,
    val debug: Boolean,
    /** `deviceType` in client registration and log uploads. */
    val deviceType: String,
    /** Prefix of a new install's client ID. */
    val clientIdPrefix: String,
    /** The self-update manifest (AppUpdater). */
    val updateManifestUrl: String,
    /** A TV (D-pad, home screen channels) rather than a touch device. */
    val isTv: Boolean,
    /** The full-screen player the coordinator opens (TV: PlayerActivity). */
    val playerActivity: Class<out android.app.Activity>,
    /** Live multiscreen, where the app has one. */
    val multiscreenActivity: Class<out android.app.Activity>? = null,
)

/** The application context and platform, for the singletons (storage, NSD, logs). */
object App {
    lateinit var context: Context
        private set
    lateinit var platform: Platform
        private set

    fun init(context: Context, platform: Platform) {
        this.context = context.applicationContext
        this.platform = platform
    }
}

/** The build values the shared code reads, from the running app's [Platform]. */
object AppBuild {
    val DEBUG: Boolean get() = App.platform.debug
    val VERSION_NAME: String get() = App.platform.versionName
    val VERSION_CODE: Int get() = App.platform.versionCode
    val UPDATE_MANIFEST_URL: String get() = App.platform.updateManifestUrl
    // Debug-only sign-in prefill (secrets.properties), compiled into this module's BuildConfig.
    val DEBUG_SERVER_URL: String get() = com.strmr.core.BuildConfig.DEBUG_SERVER_URL
    val DEBUG_USERNAME: String get() = com.strmr.core.BuildConfig.DEBUG_USERNAME
    val DEBUG_PASSWORD: String get() = com.strmr.core.BuildConfig.DEBUG_PASSWORD
    val DEBUG_PROFILE_NAME: String get() = com.strmr.core.BuildConfig.DEBUG_PROFILE_NAME
    val DEBUG_PROFILE_PIN: String get() = com.strmr.core.BuildConfig.DEBUG_PROFILE_PIN
}

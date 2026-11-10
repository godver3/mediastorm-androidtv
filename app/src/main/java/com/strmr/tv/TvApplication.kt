package com.strmr.tv

import android.app.Application
import coil.ImageLoader
import coil.ImageLoaderFactory
import com.strmr.tv.api.ServerArtwork
import com.strmr.tv.app.App
import com.strmr.tv.app.Platform
import com.strmr.tv.update.AppForeground
import com.strmr.tv.update.AppUpdater
import okhttp3.OkHttpClient

class TvApplication : Application(), ImageLoaderFactory {
    override fun onCreate() {
        super.onCreate()
        App.init(
            this,
            Platform(
                versionName = BuildConfig.VERSION_NAME,
                versionCode = BuildConfig.VERSION_CODE,
                debug = BuildConfig.DEBUG,
                deviceType = "Android TV",
                clientIdPrefix = "strmr-androidtv-",
                updateManifestUrl = BuildConfig.UPDATE_MANIFEST_URL,
                isTv = true,
                playerActivity = com.strmr.tv.player.PlayerActivity::class.java,
                multiscreenActivity = com.strmr.tv.ui.live.multiscreen.MultiscreenActivity::class.java,
            ),
        )
        com.strmr.tv.diagnostics.CrashReporter.install()
        AppForeground.register(this)
        com.strmr.tv.diagnostics.MainThreadWatchdog.start()
        // Expo's "foreground" trigger (a no-op until a profile is in use).
        AppForeground.onEntered = { com.strmr.tv.adaptive.AdaptiveThroughput.scheduleIdle("foreground-idle") }
        AppUpdater.start()
        com.strmr.tv.launcher.LauncherSync.start()
    }

    /** Coil's loader, with the app's credentials on the server's own artwork (Plex and Jellyfin posters). */
    override fun newImageLoader(): ImageLoader = ImageLoader.Builder(this)
        .okHttpClient { OkHttpClient.Builder().addInterceptor(ServerArtwork.interceptor).build() }
        .build()
}

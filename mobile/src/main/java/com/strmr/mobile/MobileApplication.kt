package com.strmr.mobile

import android.app.Application
import coil.ImageLoader
import coil.ImageLoaderFactory
import coil.disk.DiskCache
import coil.memory.MemoryCache
import com.strmr.mobile.player.MobilePlayerActivity
import com.strmr.tv.api.ServerArtwork
import com.strmr.tv.app.App
import com.strmr.tv.app.Platform
import com.strmr.tv.update.AppForeground
import com.strmr.tv.update.AppUpdater
import okhttp3.OkHttpClient

class MobileApplication : Application(), ImageLoaderFactory {
    override fun onCreate() {
        super.onCreate()
        App.init(
            this,
            Platform(
                versionName = BuildConfig.VERSION_NAME,
                versionCode = BuildConfig.VERSION_CODE,
                debug = BuildConfig.DEBUG,
                deviceType = if (resources.configuration.smallestScreenWidthDp >= 600) "Android Tablet" else "Android Phone",
                clientIdPrefix = "strmr-android-",
                updateManifestUrl = BuildConfig.UPDATE_MANIFEST_URL,
                isTv = false,
                playerActivity = MobilePlayerActivity::class.java,
            ),
        )
        com.strmr.tv.diagnostics.CrashReporter.install()
        AppForeground.register(this)
        com.strmr.tv.diagnostics.MainThreadWatchdog.start()
        AppForeground.onEntered = { com.strmr.tv.adaptive.AdaptiveThroughput.scheduleIdle("foreground-idle") }
        AppUpdater.start()
    }

    /**
     * Coil's loader: the app's credentials on the server's own artwork (Plex and Jellyfin
     * posters), and a disk cache sized for Home's rows (as the iPhone app's 512 MB).
     */
    override fun newImageLoader(): ImageLoader = ImageLoader.Builder(this)
        .okHttpClient { OkHttpClient.Builder().addInterceptor(ServerArtwork.interceptor).build() }
        .memoryCache { MemoryCache.Builder(this).maxSizePercent(0.25).build() }
        .diskCache { DiskCache.Builder().directory(cacheDir.resolve("artwork")).maxSizeBytes(512L * 1024 * 1024).build() }
        .crossfade(true)
        .build()
}

package com.strmr.tv.app

import android.app.Application
import android.content.Context
import coil.ImageLoader
import coil.ImageLoaderFactory
import com.strmr.tv.api.ServerArtwork
import com.strmr.tv.update.AppForeground
import com.strmr.tv.update.AppUpdater
import okhttp3.OkHttpClient

class App : Application(), ImageLoaderFactory {
    override fun onCreate() {
        super.onCreate()
        context = applicationContext
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

    companion object {
        /** The application context, for the singletons (storage, NSD, logs). */
        lateinit var context: Context
            private set
    }
}

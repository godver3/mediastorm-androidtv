package com.strmr.tv.app

import android.app.Application
import android.content.Context
import com.strmr.tv.update.AppForeground
import com.strmr.tv.update.AppUpdater

class App : Application() {
    override fun onCreate() {
        super.onCreate()
        context = applicationContext
        AppForeground.register(this)
        // Expo's "foreground" trigger (a no-op until a profile is in use).
        AppForeground.onEntered = { com.strmr.tv.adaptive.AdaptiveThroughput.scheduleIdle("foreground-idle") }
        AppUpdater.start()
        com.strmr.tv.launcher.LauncherSync.start()
    }

    companion object {
        /** The application context, for the singletons (storage, NSD, logs). */
        lateinit var context: Context
            private set
    }
}

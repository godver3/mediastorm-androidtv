package com.strmr.tv.app

import android.content.Context
import android.hardware.display.DisplayManager
import android.os.Handler
import android.os.Looper
import com.strmr.tv.adaptive.AdaptiveThroughput
import com.strmr.tv.api.ApiClient
import com.strmr.tv.api.Profile
import com.strmr.tv.api.registerClient
import com.strmr.tv.api.reportDisplayCapabilities
import com.strmr.tv.diagnostics.LogUploader
import com.strmr.tv.launcher.LauncherSync
import com.strmr.tv.library.CustomListsStore
import com.strmr.tv.library.HiddenItemsStore
import com.strmr.tv.library.ListsTabStore
import com.strmr.tv.library.SetAsideStore
import com.strmr.tv.library.WatchlistStore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

/** What changes around [AppSession] when a profile comes into use: stores, launcher rows, device registration. */
internal object ProfileSetup {
    /** Before the profile is published: the API and the app-wide stores move to it. */
    fun beforeActivating(chosen: Profile, previousId: String?) {
        // The library stores are app-wide; drop the previous profile's lists first.
        if (previousId != chosen.id) {
            WatchlistStore.reset()
            CustomListsStore.reset()
            HiddenItemsStore.reset()
            SetAsideStore.reset()
            ListsTabStore.reset()
        }
        ApiClient.activate(chosen.id, chosen.name)
        LauncherSync.profileActivated(chosen.id)
    }

    /** Once it is the current profile and the app is Ready. */
    fun afterActivating(chosen: Profile, scope: CoroutineScope) {
        // Home's load writes them too (debounced into one); this covers picking the same profile again.
        LauncherSync.request("profile activated")
        // Registration ties this device to the profile for per-device settings and Active
        // Streams; it is not needed to browse, so a failure isn't fatal. In the session's
        // scope: the screen that picked the profile is already gone.
        scope.launch {
            try {
                ApiClient.registerClient(chosen.id)
            } catch (e: Exception) {
                AppLog.w("AppSession", "client registration failed: ${e.message}")
            }
            ApiClient.reportDisplayCapabilities()
        }
        // Expo's "settings-loaded" trigger: an idle speed test once things settle.
        AdaptiveThroughput.scheduleIdle("settings-loaded-idle")
        LogUploader.start()
    }

    /** Calls `onChange` whenever the display changes (another TV, a new output mode). */
    fun watchDisplay(onChange: () -> Unit) {
        (App.context.getSystemService(Context.DISPLAY_SERVICE) as DisplayManager).registerDisplayListener(
            object : DisplayManager.DisplayListener {
                override fun onDisplayAdded(displayId: Int) = Unit
                override fun onDisplayRemoved(displayId: Int) = Unit
                override fun onDisplayChanged(displayId: Int) = onChange()
            },
            Handler(Looper.getMainLooper()),
        )
    }
}

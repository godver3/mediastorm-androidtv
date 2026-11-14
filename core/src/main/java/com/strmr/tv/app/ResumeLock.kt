package com.strmr.tv.app

import android.app.Activity
import android.app.Application
import android.os.Bundle
import com.strmr.tv.api.ApiClient
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import kotlinx.serialization.Serializable

/**
 * Coming back from the background, as the Expo app does: the profile picker comes up again
 * (server setting `display.alwaysShowProfileSelector`, on by default), and with it off a PIN
 * profile asks for its PIN again after five minutes away. Neither happens for a PIN-free "Use this
 * profile automatically" profile, when the app comes back to the player, or after a moment
 * away (under 2 s). "Background" is the whole app: no activity started (the player is an
 * activity of its own, so opening it doesn't count). Main thread only.
 */
internal object ResumeLock {
    private const val PICKER_MIN_AWAY_MS = 2_000L
    private const val PIN_EXPIRY_MS = 5 * 60_000L

    /** The picker can't be backed out of: it came up to re-check a PIN profile. */
    var pickRequired = false
    private var alwaysShowProfileSelector = true
    private var started = 0
    private var backgroundedAt: Long? = null
    private lateinit var scope: CoroutineScope

    @Serializable
    private data class Settings(val display: Display? = null) {
        @Serializable
        data class Display(val alwaysShowProfileSelector: Boolean? = null)
    }

    fun install(scope: CoroutineScope) {
        this.scope = scope
        (App.context as Application).registerActivityLifecycleCallbacks(object : Application.ActivityLifecycleCallbacks {
            override fun onActivityStarted(activity: Activity) {
                started += 1
                if (started == 1) foregrounded(activity)
            }

            override fun onActivityStopped(activity: Activity) {
                started = (started - 1).coerceAtLeast(0)
                // A rotation stops and restarts the activity; that isn't leaving the app.
                if (started == 0 && !activity.isChangingConfigurations) backgroundedAt = System.currentTimeMillis()
            }

            override fun onActivityCreated(activity: Activity, savedInstanceState: Bundle?) = Unit
            override fun onActivityResumed(activity: Activity) = Unit
            override fun onActivityPaused(activity: Activity) = Unit
            override fun onActivitySaveInstanceState(activity: Activity, outState: Bundle) = Unit
            override fun onActivityDestroyed(activity: Activity) = Unit
        })
    }

    /** Re-reads the server setting so the next resume decides without waiting on the network. */
    fun refreshSetting() {
        scope.launch {
            val settings = runCatching {
                ApiClient.send<Settings>("GET", "/api/settings", promptOnLock = false, timeoutMs = 10_000)
            }.getOrNull() ?: return@launch
            alwaysShowProfileSelector = settings.display?.alwaysShowProfileSelector ?: true
        }
    }

    private fun foregrounded(activity: Activity) {
        val since = backgroundedAt ?: return
        backgroundedAt = null
        val away = System.currentTimeMillis() - since
        val current = AppSession.profile.value
        val onPlayer = activity.javaClass == App.platform.playerActivity ||
            activity.javaClass == App.platform.multiscreenActivity
        if (AppSession.phase.value != AppSession.Phase.Ready || current == null || onPlayer ||
            (current.id == AppSession.autoProfileId && ProfileSelectionPolicy.canOpenAutomatically(current)) || ProfileUnlock.pinChallenge.value != null
        ) return

        if (alwaysShowProfileSelector && AppSession.profiles.value.size > 1 && away > PICKER_MIN_AWAY_MS) {
            AppLog.i("AppSession", "resume after ${away / 1000} s: profile picker")
            pickRequired = current.requiresPin
            scope.launch { AppSession.switchProfile() }
        } else if (current.requiresPin && away >= PIN_EXPIRY_MS) {
            AppLog.i("AppSession", "resume after ${away / 1000} s: PIN for ${current.name}")
            // Cancel goes to the picker (AppSession.unlockLockedProfile), which can't then be backed out of.
            pickRequired = true
            scope.launch { if (AppSession.unlockLockedProfile(current.id)) pickRequired = false }
        }
        refreshSetting()
    }
}

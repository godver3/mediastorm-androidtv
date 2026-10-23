package com.strmr.tv.update

import android.app.Activity
import android.app.Application
import android.os.Bundle

/**
 * Whether any of the app's activities is on screen. The player is its own activity, so the
 * main one stopping doesn't mean the user left the app; the count reaching zero does.
 */
object AppForeground : Application.ActivityLifecycleCallbacks {
    private var started = 0
    val visible: Boolean get() = started > 0
    /** Called on the main thread when the last activity stops: the user went home or slept. */
    var onLeft: (() -> Unit)? = null
    /** Called on the main thread when the first activity starts: launch, or back from home / sleep. */
    var onEntered: (() -> Unit)? = null

    fun register(app: Application) = app.registerActivityLifecycleCallbacks(this)

    override fun onActivityStarted(activity: Activity) {
        started += 1
        if (started == 1) onEntered?.invoke()
    }
    override fun onActivityStopped(activity: Activity) {
        started = (started - 1).coerceAtLeast(0)
        if (started == 0 && !activity.isChangingConfigurations) onLeft?.invoke()
    }
    override fun onActivityCreated(activity: Activity, savedInstanceState: Bundle?) {}
    override fun onActivityResumed(activity: Activity) {}
    override fun onActivityPaused(activity: Activity) {}
    override fun onActivitySaveInstanceState(activity: Activity, outState: Bundle) {}
    override fun onActivityDestroyed(activity: Activity) {}
}

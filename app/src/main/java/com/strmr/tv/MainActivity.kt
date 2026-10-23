package com.strmr.tv

import android.content.Intent
import android.os.Bundle
import android.view.KeyEvent
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import com.strmr.tv.app.AppSession
import com.strmr.tv.launcher.DeepLinkRouter
import com.strmr.tv.ui.RootScreen
import com.strmr.tv.ui.StrmrTheme
import com.strmr.tv.ui.home.RemotePresses
import com.strmr.tv.update.AppUpdater

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // A home screen row's link, before startup so it can pick the row's profile. Not again
        // when the activity is recreated: that intent was already handled.
        if (savedInstanceState == null) DeepLinkRouter.open(intent?.dataString)
        // Debug builds: `adb shell am start -n com.strmr.tv/.MainActivity --ez autoSignIn true`.
        AppSession.start(autoSignIn = BuildConfig.DEBUG && intent.getBooleanExtra("autoSignIn", false))
        setContent { StrmrTheme { RootScreen() } }
    }

    /** Every remote press restarts the Top Carousel's 8 s. */
    override fun dispatchKeyEvent(event: KeyEvent): Boolean {
        if (event.action == KeyEvent.ACTION_DOWN) RemotePresses.count++
        return super.dispatchKeyEvent(event)
    }

    /** Already running (singleTask): a home screen row opened over whatever was showing. */
    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        DeepLinkRouter.open(intent.dataString)
    }

    override fun onStart() {
        super.onStart()
        // The display can change while the app sleeps.
        AppSession.reportDisplayCapabilities()
        AppSession.wakes.value += 1
        AppSession.reconnectRemote()
    }

    override fun onResume() {
        super.onResume()
        AppUpdater.onForeground()
    }
}

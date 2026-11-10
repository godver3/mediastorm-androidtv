package com.strmr.mobile

import android.content.Intent
import android.content.pm.ActivityInfo
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.strmr.mobile.ui.RootScreen
import com.strmr.mobile.ui.theme.MediaStormTheme
import com.strmr.tv.app.AppSession
import com.strmr.tv.launcher.DeepLinkRouter
import com.strmr.tv.update.AppUpdater

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.dark(android.graphics.Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.dark(android.graphics.Color.TRANSPARENT),
        )
        super.onCreate(savedInstanceState)
        // Phones stay upright outside the player, as the Expo and iPhone apps; tablets turn freely.
        requestedOrientation = if (resources.configuration.smallestScreenWidthDp >= 600) {
            ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
        } else {
            ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
        }
        if (savedInstanceState == null) DeepLinkRouter.open(intent?.dataString)
        // Debug builds: `adb shell am start -n com.strmr.mobile/.MainActivity --ez autoSignIn true`.
        AppSession.start(autoSignIn = BuildConfig.DEBUG && intent.getBooleanExtra("autoSignIn", false))
        setContent { MediaStormTheme { RootScreen() } }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        DeepLinkRouter.open(intent.dataString)
    }

    override fun onStart() {
        super.onStart()
        AppSession.reportDisplayCapabilities()
        AppSession.wakes.value += 1
        AppSession.reconnectRemote()
    }

    override fun onResume() {
        super.onResume()
        AppUpdater.onForeground()
    }
}

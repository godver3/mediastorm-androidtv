package com.strmr.mobile.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.ui.Modifier
import com.strmr.mobile.ui.onboarding.LoginScreen
import com.strmr.mobile.ui.onboarding.PinSheet
import com.strmr.mobile.ui.onboarding.ProfilePickerScreen
import com.strmr.mobile.ui.onboarding.ServerScreen
import com.strmr.mobile.ui.connection.ConnectionHosts
import com.strmr.mobile.ui.connection.OfflineScreen
import com.strmr.mobile.ui.connection.StartingScreen
import com.strmr.mobile.ui.playback.PlaybackOverlays
import com.strmr.mobile.ui.shell.MainScaffold
import com.strmr.mobile.ui.shell.ToastHost
import com.strmr.tv.app.AppSession

/** Startup phase → screen; a locked-profile PIN prompt over whatever is showing. */
@Composable
fun RootScreen() {
    val phase by AppSession.phase.collectAsState()
    val profile by AppSession.profile.collectAsState()
    val challenge by AppSession.pinChallenge.collectAsState()

    Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        Box(Modifier.fillMaxSize()) {
            when (val p = phase) {
                AppSession.Phase.Starting -> StartingScreen()
                AppSession.Phase.Server -> ServerScreen()
                AppSession.Phase.Login -> LoginScreen()
                AppSession.Phase.Profiles -> ProfilePickerScreen()
                // Rebuilt per profile so every screen reloads its data.
                AppSession.Phase.Ready -> key(profile?.id) { MainScaffold() }
                // Retries by itself and offers the downloads meanwhile.
                is AppSession.Phase.Unavailable -> OfflineScreen(p.message)
            }
            ConnectionHosts()
            PlaybackOverlays()
            ToastHost()
            challenge?.let { c ->
                PinSheet(
                    profile = c.profile,
                    verify = { AppSession.verifyChallenge(it) },
                    onCancel = { AppSession.cancelChallenge() },
                )
            }
        }
    }
}

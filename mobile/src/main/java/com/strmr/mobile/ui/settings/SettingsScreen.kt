package com.strmr.mobile.ui.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Logout
import androidx.compose.material.icons.filled.Dashboard
import androidx.compose.material.icons.filled.Dns
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.PlayCircle
import androidx.compose.material.icons.filled.Style
import androidx.compose.material.icons.filled.Subtitles
import androidx.compose.material.icons.filled.SystemUpdate
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.strmr.mobile.ui.onboarding.ProfileAvatar
import com.strmr.tv.app.AppSession
import com.strmr.tv.auth.ServerAddress
import com.strmr.tv.ui.settings.BadgeSettings
import com.strmr.tv.ui.settings.SpoilerSettings
import com.strmr.tv.ui.settings.loadTrackDefaults
import com.strmr.tv.update.AppUpdater
import kotlinx.coroutines.launch

/**
 * Settings on the phone, as the TV's Settings and the iPhone's: the profile, this device's
 * playback and display choices as sub pages, then Server, App Updates, About and Sign Out.
 * Each page writes the same stores as the TV's Settings.
 */
@Composable
fun SettingsScreen(asPage: Boolean) {
    val session = AppSession
    val profile by session.profile.collectAsState()
    val server by session.serverUrl.collectAsState()
    val remote by session.isRemote.collectAsState()
    val scope = rememberCoroutineScope()
    var confirmingSignOut by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { runCatching { loadTrackDefaults() } }
    LaunchedEffect(Unit) { runCatching { SpoilerSettings.refresh() } }
    LaunchedEffect(Unit) { runCatching { BadgeSettings.refresh() } }

    SettingsPage("Settings", showBack = asPage, large = !asPage) {
        profile?.let { p ->
            SettingsSection {
                Row(
                    Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(14.dp),
                ) {
                    ProfileAvatar(p, 52.dp)
                    Column {
                        Text(p.name, style = MaterialTheme.typography.titleMedium)
                        val host = if (remote) "Remote access" else server?.let { ServerAddress.display(it) }
                        host?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
                    }
                }
                RowDivider()
                SettingsRow("Switch Profile") { scope.launch { session.switchProfile() } }
            }
        }
        SettingsSection("Preferences", "Stored on this device. Choices left at Default follow the server's settings for this profile.") {
            PageRow("Playback", SettingsKeys.PLAYBACK, icon = Icons.Filled.PlayCircle)
            PageRow("Subtitle Style", SettingsKeys.SUBTITLES, icon = Icons.Filled.Subtitles)
            PageRow("Home Layout", SettingsKeys.HOME, icon = Icons.Filled.Dashboard)
            PageRow("Cards & Spoilers", SettingsKeys.CARDS, icon = Icons.Filled.Style)
            PageRow("Menu", SettingsKeys.MENU, "Which sections show", icon = Icons.Filled.Menu)
        }
        SettingsSection("App") {
            PageRow("Server", SettingsKeys.SERVER, if (remote) "Remote access (iroh)" else server?.let { ServerAddress.display(it) }, Icons.Filled.Dns)
            val update = AppUpdater.state
            PageRow(
                "App Updates", SettingsKeys.UPDATES,
                if (update is AppUpdater.State.Available || update is AppUpdater.State.Ready) "An update is available" else null,
                Icons.Filled.SystemUpdate,
            )
            PageRow("About", SettingsKeys.ABOUT, icon = Icons.Filled.Info)
        }
        SettingsSection("Account") {
            SettingsRow("Sign Out", session.username, Icons.AutoMirrored.Filled.Logout) { confirmingSignOut = true }
        }
    }

    if (confirmingSignOut) {
        AlertDialog(
            onDismissRequest = { confirmingSignOut = false },
            title = { Text("Sign out of ${session.username ?: "this account"}?") },
            confirmButton = {
                TextButton(onClick = { confirmingSignOut = false; scope.launch { session.signOut() } }) {
                    Text("Sign Out", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = { TextButton(onClick = { confirmingSignOut = false }) { Text("Cancel") } },
        )
    }
}

package com.strmr.tv.ui.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.tv.material3.ListItem
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import com.strmr.tv.api.ApiClient
import com.strmr.tv.api.checkHealth
import com.strmr.tv.api.playbackSettings
import com.strmr.tv.app.AppSession
import com.strmr.tv.app.Config
import com.strmr.tv.auth.ServerAddress
import com.strmr.tv.diagnostics.LogUploader
import com.strmr.tv.ui.LocalNavigator
import com.strmr.tv.ui.Page
import com.strmr.tv.ui.PageMargin
import com.strmr.tv.ui.PickerDialog
import com.strmr.tv.ui.clipVertically
import com.strmr.tv.ui.onboarding.BrandMark
import com.strmr.tv.ui.onboarding.ProfileAvatar
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import kotlinx.coroutines.launch
import okhttp3.HttpUrl

/** Server, profile and account, and About. The rest of Settings is Tier 2. */
@Composable
fun SettingsScreen() {
    val session = AppSession
    val server by session.serverUrl.collectAsState()
    val remote by session.isRemote.collectAsState()
    val profile by session.profile.collectAsState()
    val scope = rememberCoroutineScope()
    LaunchedEffect(Unit) { TrailerSettings.serverValue = ApiClient.playbackSettings().autoPlayTrailers }
    LaunchedEffect(Unit) { loadTrackDefaults() }
    LaunchedEffect(Unit) { SpoilerSettings.refresh() }

    var health by remember { mutableStateOf<String?>(null) }
    fun checkConnection() {
        health = null
        scope.launch {
            health = try { ApiClient.checkHealth(ApiClient.baseUrl); "Connected" } catch (e: Exception) { e.message ?: "Unreachable" }
        }
    }
    LaunchedEffect(Unit) { checkConnection() }

    // The tvOS layout: the brand and profile on the left, the settings list on the right.
    Row(Modifier.fillMaxSize().padding(top = 76.dp, start = PageMargin, end = PageMargin)) {
        Column(
            Modifier.weight(0.4f).fillMaxHeight(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            BrandMark(90.dp)
            Text(profile?.name ?: "", fontSize = 20.sp, color = Color.White.copy(alpha = 0.6f), modifier = Modifier.padding(top = 16.dp))
        }
        SettingsList(server, remote, health, ::checkConnection, Modifier.weight(0.6f))
    }
}

@Composable
private fun SettingsList(server: HttpUrl?, remote: Boolean, health: String?, checkConnection: () -> Unit, modifier: Modifier) {
    val session = AppSession
    val profile by session.profile.collectAsState()
    val version by session.serverVersion.collectAsState()
    val scope = rememberCoroutineScope()
    val navigator = LocalNavigator.current
    var pickingBackground by remember { mutableStateOf(false) }
    if (pickingBackground) {
        val options = AppearanceSettings.Background.entries
        PickerDialog("Background", options.map { it.label }, AppearanceSettings.background.ordinal,
                     onPick = { AppearanceSettings.choose(options[it]) }, onDismiss = { pickingBackground = false })
    }
    LazyColumn(
        // Clipped below the tab bar, so rows never scroll under it.
        modifier.fillMaxHeight().clipVertically(),
        contentPadding = PaddingValues(top = 8.dp, bottom = 48.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        item { Header("Server") }
        item {
            ListItem(
                selected = false, onClick = {},
                headlineContent = { Text("Address") },
                trailingContent = {
                    Text(if (remote) "Remote access (iroh)" else server?.let { ServerAddress.display(it) } ?: "Not connected")
                },
            )
        }
        item {
            ListItem(
                selected = false, onClick = checkConnection,
                headlineContent = { Text("Status") },
                supportingContent = { Text("Select to check the connection") },
                trailingContent = { Text(health ?: "Checking…") },
            )
        }
        speedTestRows()
        item {
            ListItem(
                selected = false, onClick = { session.changeServer() },
                headlineContent = { Text("Change Server…") },
            )
        }
        item { Header("Profile") }
        item {
            ListItem(
                selected = false, onClick = { scope.launch { session.switchProfile() } },
                headlineContent = { Text(profile?.name ?: "") },
                supportingContent = { Text("Switch Profile") },
                leadingContent = { profile?.let { ProfileAvatar(it, 32.dp) } },
            )
        }
        item { Header("Home") }
        item {
            ListItem(
                selected = false, onClick = { navigator.open(Page.HomeLayout) },
                headlineContent = { Text("Home Layout") },
                supportingContent = { Text("Top carousel, focused title info, view switcher, card size and position, each row's style") },
            )
        }
        item {
            ListItem(
                selected = false, onClick = { navigator.open(Page.TopMenu) },
                headlineContent = { Text("Menu") },
                supportingContent = { Text("Top bar or side menu, and which entries show") },
            )
        }
        item { Header("Appearance") }
        item {
            ListItem(
                selected = false, onClick = { pickingBackground = true },
                headlineContent = { Text("Background") },
                supportingContent = { Text("Behind every page but Home") },
                trailingContent = { Text(AppearanceSettings.background.label) },
            )
        }
        item { Header("Spoilers") }
        spoilerSettingRows()
        item { Header("Playback") }
        item {
            ListItem(
                selected = false, onClick = { navigator.open(Page.SubtitleStyle) },
                headlineContent = { Text("Subtitle Style") },
                supportingContent = { Text("Size, colour, edge, background and position") },
            )
        }
        item {
            val override = TrailerSettings.override
            val server = TrailerSettings.serverValue
            ListItem(
                selected = false,
                // Default (follows the server) → On → Off → Default.
                onClick = { TrailerSettings.set(when (override) { null -> true; true -> false; false -> null }) },
                headlineContent = { Text("Auto-play Trailers") },
                supportingContent = {
                    Text(when (override) {
                        null -> "Default" + (server?.let { if (it) " (On)" else " (Off)" } ?: "")
                        true -> "On"
                        false -> "Off"
                    })
                },
            )
        }
        trackSettingRows()
        item {
            val enabled = DolbyVisionSettings.profile7Enabled
            ListItem(
                selected = false,
                onClick = { DolbyVisionSettings.setProfile7(!enabled) },
                headlineContent = { Text("Dolby Vision Profile 7") },
                supportingContent = { Text("Convert UHD Blu-ray Dolby Vision so this device can play it") },
                trailingContent = { Text(if (enabled) "On" else "Off") },
            )
        }
        liveHlsResetRow()
        item { Header("Updates") }
        updateRows()
        item { Header("Account") }
        item {
            ListItem(
                selected = false, onClick = { scope.launch { session.signOut() } },
                headlineContent = { Text("Sign Out") },
                supportingContent = { Text(session.username ?: "") },
            )
        }
        item { Header("About") }
        item {
            ListItem(
                selected = false, onClick = {},
                headlineContent = { Text("MediaStorm for Android TV") },
                supportingContent = {
                    Text("App ${Config.fullVersion}" + (version?.let { " · Server ${it.version}" } ?: ""))
                },
            )
        }
        item {
            val status = LogUploader.status
            ListItem(
                selected = false, onClick = { LogUploader.uploadNow() },
                headlineContent = { Text("Upload Logs") },
                supportingContent = {
                    Text(when (status) {
                        LogUploader.Status.Idle -> "Send this device's log to the server for the admin UI's Logs page"
                        LogUploader.Status.Uploading -> "Uploading…"
                        is LogUploader.Status.Uploaded -> "Uploaded at " + status.at.atZone(ZoneId.systemDefault())
                            .format(DateTimeFormatter.ofLocalizedTime(FormatStyle.SHORT))
                        is LogUploader.Status.Failed -> "Upload failed: ${status.message}"
                    })
                },
            )
        }
    }
}

@Composable
private fun Header(title: String) {
    Text(
        title,
        style = MaterialTheme.typography.titleSmall,
        color = Color.White.copy(alpha = 0.6f),
        modifier = Modifier.padding(top = 14.dp, bottom = 2.dp, start = 4.dp),
    )
}

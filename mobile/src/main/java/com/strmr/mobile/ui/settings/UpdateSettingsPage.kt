package com.strmr.mobile.ui.settings

import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import com.strmr.tv.app.AppBuild
import com.strmr.tv.app.AppSession
import com.strmr.tv.app.Config
import com.strmr.tv.diagnostics.LogUploader
import com.strmr.tv.update.AppUpdater
import com.strmr.tv.update.AppUpdater.State
import com.strmr.tv.update.UpdateInstaller
import com.strmr.tv.update.UpdateSettings

/** Settings › App Updates: the check (its row acts on what it found) and Automatic Updates. */
@Composable
fun UpdateSettingsPage() {
    SettingsPage("App Updates") {
        SettingsSection {
            val state = AppUpdater.state
            ValueRow(
                if (state is State.Available || state is State.Ready) "Install Update" else "Check for Updates",
                "${AppBuild.VERSION_NAME} (${AppBuild.VERSION_CODE})",
                describe(state),
            ) { AppUpdater.select() }
            val modes = UpdateSettings.Mode.entries
            PickerRow("Automatic Updates", modes.map { it.label }, UpdateSettings.mode.ordinal, UpdateSettings.mode.detail) {
                UpdateSettings.set(modes[it])
            }
        }
    }
}

private fun describe(state: State): String = when (state) {
    State.Idle -> "Tap to check for a newer build"
    State.Checking -> "Checking…"
    is State.UpToDate -> "Up to date · checked ${time(state.at)}"
    is State.Available -> "${state.manifest.label} is available"
    is State.Downloading -> "Downloading ${state.manifest.label}… ${state.percent}%"
    is State.Ready -> "${state.manifest.label} is ready to install"
    is State.Installing -> "Installing ${state.manifest.label}…"
    is State.Failed -> state.message
}

/** The downloaded build's prompt; the shell hosts it so an automatic check can ask from anywhere. */
@Composable
fun UpdatePrompt() {
    if (!AppUpdater.prompting) return
    val manifest = (AppUpdater.state as? State.Ready)?.manifest
    val notes = manifest?.notes.orEmpty().take(6).joinToString("\n") { "• $it" }
    val consent = if (UpdateInstaller.allowed) "" else
        "\n\nFirst, allow MediaStorm to install apps in the screen that opens, then come back."
    AlertDialog(
        onDismissRequest = { if (AppUpdater.prompting) AppUpdater.later() },
        title = { Text(manifest?.let { "MediaStorm ${it.label} Is Ready" } ?: "Install the Update") },
        text = { Text(notes.ifEmpty { "A new build of MediaStorm is ready to install." } + consent) },
        confirmButton = { TextButton(onClick = { AppUpdater.install() }) { Text("Install Now") } },
        dismissButton = { TextButton(onClick = { AppUpdater.later() }) { Text("Later") } },
    )
}

/** Settings › About: the app and server versions, the device ID (tap to copy) and the log upload. */
@Composable
fun AboutSettingsPage() {
    val version by AppSession.serverVersion.collectAsState()
    @Suppress("DEPRECATION") val clipboard = LocalClipboardManager.current
    SettingsPage("About") {
        SettingsSection(footer = "Airing schedules: TVmaze (CC BY-SA).") {
            ValueRow("Version", "${AppBuild.VERSION_NAME} (${AppBuild.VERSION_CODE})")
            ValueRow("Server Version", version?.let { v -> v.buildId?.takeIf { it.isNotEmpty() }?.let { "${v.version} ($it)" } ?: v.version } ?: "—")
            SettingsRow("Device ID", Config.clientId) { clipboard.setText(AnnotatedString(Config.clientId)) }
            val status = LogUploader.status
            SettingsRow(
                "Upload Logs",
                when (status) {
                    LogUploader.Status.Idle -> "Send this device's log to the server for the admin UI's Logs page"
                    LogUploader.Status.Uploading -> "Uploading…"
                    is LogUploader.Status.Uploaded -> "Uploaded ${time(status.at)}"
                    is LogUploader.Status.Failed -> "Upload failed: ${status.message}"
                },
                enabled = status != LogUploader.Status.Uploading,
            ) { LogUploader.uploadNow() }
        }
    }
}

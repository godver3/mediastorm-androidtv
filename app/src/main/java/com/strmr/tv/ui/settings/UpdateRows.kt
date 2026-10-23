package com.strmr.tv.ui.settings

import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.runtime.Composable
import androidx.tv.material3.ListItem
import androidx.tv.material3.Text
import com.strmr.tv.BuildConfig
import com.strmr.tv.update.AppUpdater
import com.strmr.tv.update.AppUpdater.State
import com.strmr.tv.update.UpdateInstaller
import com.strmr.tv.update.UpdateSettings
import com.strmr.tv.ui.ChoiceDialog
import com.strmr.tv.ui.DialogChoice
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle

/** Settings › Updates: the check (its row acts on what it found) and Automatic Updates. */
fun LazyListScope.updateRows() {
    item {
        val state = AppUpdater.state
        ListItem(
            selected = false, onClick = { AppUpdater.select() },
            headlineContent = { Text(if (state is State.Available || state is State.Ready) "Install Update" else "Check for Updates") },
            supportingContent = { Text(describe(state)) },
            trailingContent = { Text("${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE})") },
        )
    }
    item {
        val mode = UpdateSettings.mode
        ListItem(
            selected = false, onClick = { UpdateSettings.cycle() },
            headlineContent = { Text("Automatic Updates") },
            supportingContent = { Text(mode.detail) },
            trailingContent = { Text(mode.label) },
        )
    }
}

private fun describe(state: State): String = when (state) {
    State.Idle -> "Select to check for a newer build"
    State.Checking -> "Checking…"
    is State.UpToDate -> "Up to date · checked " +
        state.at.atZone(ZoneId.systemDefault()).format(DateTimeFormatter.ofLocalizedTime(FormatStyle.SHORT))
    is State.Available -> "${state.manifest.label} is available"
    is State.Downloading -> "Downloading ${state.manifest.label}… ${state.percent}%"
    is State.Ready -> "${state.manifest.label} is ready to install"
    is State.Installing -> "Installing ${state.manifest.label}…"
    is State.Failed -> state.message
}

/** The downloaded build's prompt, hosted on the top page so it gets focus. */
@Composable
fun UpdatePrompt() {
    if (!AppUpdater.prompting) return
    val manifest = (AppUpdater.state as? State.Ready)?.manifest
    val notes = manifest?.notes.orEmpty().take(6).joinToString("\n") { "• $it" }
    val consent = if (UpdateInstaller.allowed) "" else
        "\n\nFirst, turn on MediaStorm in the \"Install unknown apps\" list that opens, then press Back."
    ChoiceDialog(
        title = manifest?.let { "MediaStorm ${it.label} Is Ready" } ?: "Install the Update",
        message = (notes.ifEmpty { "A new build of MediaStorm is ready to install." }) + consent +
            "\n\nThe app closes while it updates; open it again from the home screen.",
        choices = listOf(DialogChoice("Install Now") { AppUpdater.install() }, DialogChoice("Later") { AppUpdater.later() }),
        onDismiss = { if (AppUpdater.prompting) AppUpdater.later() },
    )
}

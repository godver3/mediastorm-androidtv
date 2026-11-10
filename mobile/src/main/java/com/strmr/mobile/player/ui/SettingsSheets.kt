package com.strmr.mobile.player.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.AspectRatio
import androidx.compose.material.icons.filled.Headphones
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.ScreenRotation
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material.icons.filled.SwapHoriz
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.strmr.mobile.player.PlayerPrefs
import com.strmr.tv.api.Artwork
import com.strmr.tv.player.MenuChoice
import com.strmr.tv.player.PlayerController
import com.strmr.tv.player.speedLabel
import com.strmr.tv.player.streamInfoRows

/**
 * The player's Settings sheet (the iPhone player's gear menu): speed, aspect, Switch Source,
 * Stream Info, the phone's orientation and background audio.
 */
@Composable
fun SettingsSheet(controller: PlayerController, ui: PlayerUiState, tablet: Boolean, aspectMode: AspectMode) {
    val speed by controller.speed.collectAsState()
    val live = controller.session.isLive
    Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(bottom = 24.dp)) {
        SheetTitle("Settings")
        if (!live) SettingRow(Icons.Filled.Speed, "Playback Speed", speedLabel(speed)) { ui.sheet = PlayerSheet.SPEED }
        SettingRow(Icons.Filled.AspectRatio, "Aspect Ratio", aspectMode.title) { ui.sheet = PlayerSheet.ASPECT }
        if (switchable(controller)) SettingRow(Icons.Filled.SwapHoriz, "Switch Source", controller.session.releaseName) { ui.sheet = PlayerSheet.SOURCES }
        SettingRow(Icons.Filled.Info, if (live) "Channel Info" else "Stream Info", null) { ui.sheet = PlayerSheet.INFO }
        HorizontalDivider(Modifier.padding(vertical = 8.dp))
        // A tablet always turns with the device.
        if (!tablet) SettingRow(Icons.Filled.ScreenRotation, "Player Orientation", PlayerPrefs.orientation.title) { ui.sheet = PlayerSheet.ORIENTATION }
        ListItem(
            headlineContent = { Text("Background Audio") },
            supportingContent = { Text("Keep playing when you leave the app") },
            leadingContent = { Icon(Icons.Filled.Headphones, contentDescription = null) },
            trailingContent = { Switch(checked = PlayerPrefs.backgroundAudio, onCheckedChange = { PlayerPrefs.backgroundAudio = it }) },
            colors = ListItemDefaults.colors(containerColor = Color.Transparent),
            modifier = Modifier.clickable { PlayerPrefs.backgroundAudio = !PlayerPrefs.backgroundAudio },
        )
    }
}

/** Switch Source: a resolved title only (not live TV, trailers, recordings or a downloaded file). */
fun switchable(controller: PlayerController): Boolean =
    !controller.session.isLive && controller.session.reportsProgress && controller.session.streamPath != null &&
        !controller.session.localFile

@Composable
private fun SettingRow(icon: ImageVector, title: String, value: String?, onClick: () -> Unit) {
    ListItem(
        headlineContent = { Text(title) },
        supportingContent = value?.let { { Text(it, maxLines = 1, overflow = TextOverflow.Ellipsis) } },
        leadingContent = { Icon(icon, contentDescription = null) },
        trailingContent = { Icon(Icons.AutoMirrored.Filled.ArrowForward, contentDescription = null) },
        colors = ListItemDefaults.colors(containerColor = Color.Transparent),
        modifier = Modifier.clickable(onClick = onClick),
    )
}

/** Player Orientation: Landscape (the default), Portrait, or Follow Device Rotation. */
@Composable
fun OrientationSheet(onDone: () -> Unit) {
    val choices = PlayerPrefs.Orientation.entries.map { o ->
        MenuChoice(o.title, if (o == PlayerPrefs.Orientation.DEVICE) "Turns with the phone" else null,
                   selected = o == PlayerPrefs.orientation) { PlayerPrefs.orientation = o; onDone() }
    }
    ChoiceSheet("Player Orientation", choices, "")
}

/**
 * Stream Info (the TV's Info panel): the episode still or backdrop (a live channel's logo), the
 * title, the episode, the overview and the stream's details.
 */
@Composable
fun InfoSheet(controller: PlayerController) {
    val request = controller.request
    val video by controller.videoFormat.collectAsState()
    val audio by controller.audioFormat.collectAsState()
    val wide = request.episode?.imageUrl ?: request.backdrops.firstOrNull() ?: request.title.backdropUrl
    val logo = (request.logoUrl ?: request.title.logoUrl).takeIf { controller.session.isLive }?.let { Artwork.logo(it) }
    val image = wide?.let { Artwork.url(it) } ?: logo
    val overview = request.episode?.overview ?: request.title.overview
    Column(
        Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(start = 24.dp, end = 24.dp, bottom = 32.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        image?.let {
            AsyncImage(
                model = it, contentDescription = null, contentScale = if (wide != null) ContentScale.Crop else ContentScale.Fit,
                modifier = Modifier.fillMaxWidth().aspectRatio(16f / 9f).clip(RoundedCornerShape(12.dp)).background(Color.Black),
            )
        }
        Text(request.title.name, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold,
             modifier = Modifier.padding(top = 8.dp))
        request.episode?.let { ep ->
            Text(listOfNotNull(ep.code, ep.name?.takeIf { it.isNotEmpty() }).joinToString(" · "),
                 style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        request.title.year?.takeIf { request.episode == null }?.let {
            Text(it.toString(), style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        overview?.takeIf { it.isNotBlank() }?.let {
            Text(it, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(top = 4.dp))
        }
        val rows = streamInfoRows(controller.session, video, audio)
        if (rows.isNotEmpty()) HorizontalDivider(Modifier.padding(vertical = 8.dp))
        rows.forEach { (label, value) ->
            Row(Modifier.fillMaxWidth()) {
                Text(label, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant,
                     modifier = Modifier.width(120.dp))
                Text(value, style = MaterialTheme.typography.bodyMedium, maxLines = if (label == "File") 3 else 2,
                     overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
            }
        }
    }
}

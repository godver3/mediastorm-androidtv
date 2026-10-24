package com.strmr.tv.ui.sports.cycling

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.tv.material3.ExperimentalTvMaterial3Api
import androidx.tv.material3.FilterChip
import androidx.tv.material3.OutlinedButton
import androidx.tv.material3.Text
import coil.compose.SubcomposeAsyncImage
import com.strmr.tv.sports.SportsCyclingRace
import com.strmr.tv.sports.SportsCyclingStage
import com.strmr.tv.ui.sports.SportsMuted
import com.strmr.tv.ui.sports.SportsNotice
import com.strmr.tv.ui.sports.SportsPanel
import com.strmr.tv.ui.sports.game.SportsChipRow
import com.strmr.tv.ui.sports.race.Eyebrow
import com.strmr.tv.ui.sports.race.MapDialog

/**
 * The Route tab (Expo `SportsOrganizerGuide` for a stage): the organizer's route map and stage
 * profile (chips switch between them), Expand to see one full screen, the guide's state and
 * Retry Guide when it is late or missing. A calendar listing has its source only.
 */
@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
fun SportsCyclingRoute(state: SportsCyclingPageState, race: SportsCyclingRace, stage: SportsCyclingStage) {
    val guide = stage.routeGuide
    val assets = listOfNotNull(
        guide?.mapUrl?.takeIf { it.startsWith("https://") }?.let { "Route map" to it },
        guide?.profileUrl?.takeIf { it.startsWith("https://") }?.let { "Stage profile" to it },
    )
    var chosen by remember(stage.id) { mutableStateOf(0) }
    var expanded by remember { mutableStateOf(false) }
    val asset = assets.getOrNull(chosen) ?: assets.firstOrNull()
    val title = "${race.name} · ${stage.name}"
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        if (guide?.state == "pending" && state.loading) {
            SportsNotice("Loading stage guide", "Checking the selected stage's official map and profile.", loading = true)
        }
        SportsPanel(title = null) {
            Column(Modifier.padding(horizontal = 10.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Eyebrow(if (guide != null) "Official stage guide" else if (race.calendarOnly) "Calendar source" else "Organizer race center")
                Text(title, fontSize = 18.sp, fontWeight = FontWeight.SemiBold)
                stage.route.ifEmpty { null }?.let { Text(it, fontSize = 13.sp, color = SportsMuted) }
                if (guide?.state == "stale") Text("Updates delayed · Showing the last verified guide.", fontSize = 12.sp, color = SportsMuted)
                if (assets.size > 1) {
                    val focus = remember { FocusRequester() }
                    SportsChipRow(focus) {
                        assets.forEachIndexed { index, (label, _) ->
                            val on = asset?.first == label
                            FilterChip(selected = on, onClick = { chosen = index }, modifier = if (on) Modifier.focusRequester(focus) else Modifier) { Text(label) }
                        }
                    }
                }
                if (asset != null) {
                    SubcomposeAsyncImage(
                        model = asset.second, contentDescription = asset.first, contentScale = ContentScale.Fit,
                        loading = { Text("Loading ${asset.first.lowercase()}…", fontSize = 12.sp, color = SportsMuted) },
                        error = { Text("Guide image unavailable", fontSize = 12.sp, color = SportsMuted) },
                        modifier = Modifier.fillMaxWidth().height(300.dp).clip(RoundedCornerShape(8.dp)).background(Color(0xFFF5F2E9)),
                    )
                } else {
                    Text(
                        guide?.reason ?: if (race.calendarOnly) "This calendar supplies race dates only. Check the organizer for the published schedule."
                        else "Stage map and profile are unavailable here. The organizer guide may have further route details.",
                        fontSize = 13.sp, color = SportsMuted,
                    )
                }
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    if (asset != null) OutlinedButton(onClick = { expanded = true }) { Text("Expand ${asset.first}") }
                    if (guide != null && guide.state in setOf("unavailable", "stale")) OutlinedButton(onClick = state::retry) { Text("Retry Guide") }
                }
            }
        }
    }
    if (expanded && asset != null) {
        MapDialog(title = title, subtitle = listOf(asset.first, stage.route).filter { it.isNotEmpty() }.joinToString(" · "),
                  note = "Published organizer diagram", onClose = { expanded = false }) {
            SubcomposeAsyncImage(
                model = asset.second, contentDescription = asset.first, contentScale = ContentScale.Fit,
                modifier = Modifier.fillMaxWidth().weight(1f).clip(RoundedCornerShape(8.dp)).background(Color(0xFFF5F2E9)),
            )
        }
    }
}

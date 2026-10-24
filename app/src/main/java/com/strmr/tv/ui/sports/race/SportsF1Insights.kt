package com.strmr.tv.ui.sports.race

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.focusRestorer
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.tv.material3.ExperimentalTvMaterial3Api
import androidx.tv.material3.FilterChip
import androidx.tv.material3.OutlinedButton
import androidx.tv.material3.Text
import com.strmr.tv.sports.SportsF1Archive
import com.strmr.tv.sports.SportsF1Driver
import com.strmr.tv.sports.SportsF1Message
import com.strmr.tv.sports.SportsF1Qualifying
import com.strmr.tv.sports.SportsF1Timing
import com.strmr.tv.ui.sports.SportsAmber
import com.strmr.tv.ui.sports.SportsMuted
import com.strmr.tv.ui.sports.SportsPanel
import com.strmr.tv.ui.sports.SportsReadout
import com.strmr.tv.ui.sports.SportsReading

private enum class InsightTab { TIMELINE, TYRES, SECTORS }

/**
 * Session insights from F1's timing archive (Expo `SportsF1Analysis`): weather and the selected
 * driver's last lap and interval, then Race Timeline (race-control messages; flags and safety
 * car alone on request), Tyre Stints (bars sized by each stint's laps, coloured by compound)
 * and Sectors (Q1–Q3 in qualifying). An archived or recorded snapshot, never a live feed.
 */
@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
fun SportsF1Insights(archive: SportsF1Archive, driverNumber: String?) {
    var tab by remember(archive.eventId) { mutableStateOf(InsightTab.TIMELINE) }
    val drivers = remember(archive) { SportsF1Timing.drivers(archive) }
    val qualifying = remember(archive) { SportsF1Timing.qualifying(archive) }
    val weather = remember(archive) { SportsF1Timing.weather(archive) }
    val selected = drivers.firstOrNull { it.id == driverNumber }
    // The selected driver first, then the rest in position order.
    val ordered = selected?.let { listOf(it) + drivers.filter { d -> d.id != it.id } } ?: drivers
    SportsPanel(title = "Session insights", note = if (archive.recorded) "Recorded · ${archive.capturedElapsed.orEmpty()}" else "Archived session") {
        Column(Modifier.padding(horizontal = 10.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            if (weather.isNotEmpty()) Text(weather, fontSize = 13.sp)
            selected?.let {
                Column {
                    Text(it.name, fontSize = 17.sp, fontWeight = FontWeight.SemiBold)
                    Text("Selected driver · Last ${it.lastLap.ifEmpty { "—" }} · Interval ${it.interval.ifEmpty { "—" }}",
                         fontSize = 12.sp, color = SportsMuted)
                }
            }
            Row(Modifier.focusRestorer(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                InsightTab.entries.forEach { option ->
                    val label = when (option) {
                        InsightTab.TIMELINE -> "Race Timeline"
                        InsightTab.TYRES -> "Tyre Stints"
                        InsightTab.SECTORS -> if (qualifying != null) "Qualifying" else "Sectors"
                    }
                    FilterChip(selected = tab == option, onClick = { tab = option }) { Text(label) }
                }
            }
        }
        when (tab) {
            InsightTab.TIMELINE -> Timeline(archive)
            InsightTab.TYRES -> Tyres(ordered.take(5), selected)
            InsightTab.SECTORS -> Sectors(ordered, selected, qualifying)
        }
        Text(
            "F1 timing archive · ${if (archive.recorded) "Frozen at the recorded timestamp" else "Final session snapshot"} · No live updates",
            fontSize = 11.sp, color = SportsMuted, modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
        )
    }
}

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
private fun Timeline(archive: SportsF1Archive) {
    var flagsOnly by remember(archive.eventId) { mutableStateOf(false) }
    var all by remember(archive.eventId) { mutableStateOf(false) }
    val messages = remember(archive, flagsOnly) {
        SportsF1Timing.messages(archive).filter { !flagsOnly || it.flag.isNotEmpty() || it.category == "SafetyCar" }
    }
    Column(Modifier.padding(horizontal = 10.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        FilterChip(selected = flagsOnly, onClick = { flagsOnly = !flagsOnly }) { Text("Flags & Safety Car Only") }
        if (messages.isEmpty()) Text("No ${if (flagsOnly) "flag" else "race-control"} messages in this capture.", fontSize = 12.sp, color = SportsMuted)
    }
    (if (all) messages else messages.take(6)).forEach { MessageRow(it) }
    if (messages.size > 6) {
        OutlinedButton(onClick = { all = !all }, modifier = Modifier.padding(horizontal = 10.dp)) {
            Text(if (all) "Show Recent Messages" else "View All ${messages.size} Messages")
        }
    }
}

@Composable
private fun MessageRow(message: SportsF1Message) {
    SportsReadout {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Box(Modifier.size(7.dp).clip(CircleShape).background(flagColor(message.flag)))
            Text(message.flag.ifEmpty { message.category }.ifEmpty { "Race control" }.uppercase(), fontSize = 11.sp, fontWeight = FontWeight.Bold)
            message.lap?.let { Text("L$it", fontSize = 11.sp, color = SportsMuted) }
            if (message.utc.length >= 16) Text("${message.utc.substring(11, 16)} UTC", fontSize = 11.sp, color = SportsMuted)
        }
        Text(message.message, fontSize = 13.sp)
    }
}

@Composable
private fun Tyres(rows: List<SportsF1Driver>, selected: SportsF1Driver?) {
    Text("Selected driver first · Bar segments show reported laps on each stint.", fontSize = 12.sp, color = SportsMuted,
         modifier = Modifier.padding(horizontal = 10.dp))
    rows.forEach { driver ->
        SportsReadout {
            Text(driver.abbreviation.ifEmpty { driver.name } + if (driver.id == selected?.id) " · Selected" else "",
                 fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
            Row(Modifier.fillMaxWidth().height(9.dp), horizontalArrangement = Arrangement.spacedBy(3.dp)) {
                driver.stints.forEach { stint ->
                    val laps = stint.laps ?: 0
                    if (laps > 0) Box(Modifier.weight(laps.toFloat()).fillMaxHeight().background(compoundColor(stint.compound)))
                }
            }
            Text(
                if (driver.stints.isEmpty()) "Stint data not supplied"
                else driver.stints.joinToString("  /  ") { "${it.compound} · ${it.laps ?: "—"} laps${if (it.used) " (used set)" else ""}" },
                fontSize = 12.sp,
            )
        }
    }
}

@Composable
private fun Sectors(rows: List<SportsF1Driver>, selected: SportsF1Driver?, qualifying: SportsF1Qualifying?) {
    Text(
        if (qualifying != null) "Q${qualifying.part}${qualifying.remaining?.takeIf { it > 0 }?.let { " · $it entries" } ?: ""} · Reported times by qualifying segment"
        else "Latest reported sector times · Not a reconstructed best lap.",
        fontSize = 12.sp, color = SportsMuted, modifier = Modifier.padding(horizontal = 10.dp),
    )
    rows.forEach { driver ->
        SportsReadout {
            Row {
                Text(
                    listOfNotNull(driver.position?.let { "P$it" }, driver.abbreviation.ifEmpty { driver.name }, "Selected".takeIf { driver.id == selected?.id })
                        .joinToString(" · "),
                    fontSize = 14.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f),
                )
                Text(
                    if (driver.knockedOut) "Knocked out" else driver.bestLap.ifEmpty { null }?.let { "Best $it" }.orEmpty(),
                    fontSize = 12.sp, color = if (driver.knockedOut) SportsAmber else SportsMuted,
                )
            }
            Row(horizontalArrangement = Arrangement.spacedBy(18.dp)) {
                (0..2).forEach { i ->
                    val time = if (qualifying != null) driver.qualifyingTimes.getOrNull(i) else driver.sectors.getOrNull(i)
                    SportsReading("${if (qualifying != null) "Q" else "S"}${i + 1}", time?.ifEmpty { null } ?: "—")
                }
            }
        }
    }
}

private fun flagColor(flag: String): Color = when (flag) {
    "RED" -> Color(0xFFFF6370)
    "YELLOW" -> Color(0xFFF5D454)
    "GREEN" -> Color(0xFF65CF8E)
    else -> Color(0xFF00C8F0)
}

private fun compoundColor(compound: String): Color = when (compound) {
    "SOFT" -> Color(0xFFFF6370)
    "MEDIUM" -> Color(0xFFF5D454)
    "HARD" -> Color(0xFFDCE3E9)
    "INTERMEDIATE" -> Color(0xFF65CF8E)
    "WET" -> Color(0xFF72B9FF)
    else -> SportsMuted
}

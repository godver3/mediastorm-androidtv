package com.strmr.tv.ui.sports.race

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.tv.material3.OutlinedButton
import androidx.tv.material3.Text
import com.strmr.tv.sports.SportsRaceDriver
import com.strmr.tv.sports.SportsRaceEvent
import com.strmr.tv.sports.SportsRaceFormat
import com.strmr.tv.ui.PickerDialog
import com.strmr.tv.ui.sports.SportsFigure
import com.strmr.tv.ui.sports.SportsMuted
import com.strmr.tv.ui.sports.SportsPanel

/**
 * The selected entry (Expo `SportsRaceDriverCard`): name and position, team, number and result,
 * then the figures that matter most (gap, laps behind, laps, fastest lap, pit stops, time);
 * More Details adds every other statistic the provider sent.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun SportsRaceDriverCard(driver: SportsRaceDriver) {
    var expanded by remember(driver.id) { mutableStateOf(false) }
    val supplied = driver.statistics.filter { it.value.isNotBlank() }
    val primary = PRIORITY.flatMap { name -> supplied.filter { it.name == name } }
    val secondary = supplied.filter { it.name !in PRIORITY }
    SportsPanel(title = null) {
        Column(Modifier.padding(horizontal = 10.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Column {
                Text(driver.name + (driver.position?.let { " · P$it" } ?: ""), fontSize = 18.sp, fontWeight = FontWeight.Bold,
                     maxLines = 1, overflow = TextOverflow.Ellipsis)
                val line = listOfNotNull(driver.team?.ifEmpty { null }, driver.number?.let { "#$it" }, driver.result?.ifEmpty { null })
                if (line.isNotEmpty()) Text(line.joinToString(" · "), fontSize = 13.sp, color = SportsMuted)
            }
            val stats = if (expanded) primary + secondary else primary
            if (stats.isNotEmpty()) {
                FlowRow(horizontalArrangement = Arrangement.spacedBy(18.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    stats.forEach { SportsFigure(it.label, it.value.trim(), Modifier.widthIn(min = 84.dp)) }
                }
            }
            if (secondary.isNotEmpty()) {
                OutlinedButton(onClick = { expanded = !expanded }) { Text(if (expanded) "Fewer Details" else "More Details") }
            }
        }
    }
}

/**
 * Two entries of the session side by side (Expo `SportsRaceComparison`): position and the gap,
 * laps, fastest lap and pit stops (Q1–Q3 in qualifying). Gaps are each to the leader. Picking
 * or closing swaps the panel's buttons, so focus moves to the one now leading it.
 */
@Composable
fun SportsRaceComparison(drivers: List<SportsRaceDriver>, selected: SportsRaceDriver, session: SportsRaceEvent) {
    var otherId by remember(session.id) { mutableStateOf<String?>(null) }
    var picking by remember { mutableStateOf(false) }
    val lead = remember { FocusRequester() }
    var refocus by remember { mutableStateOf(false) }
    LaunchedEffect(otherId, refocus) {
        if (refocus) runCatching { lead.requestFocus() }
        refocus = false
    }
    val options = drivers.filter { it.id != selected.id }
    if (options.isEmpty()) return
    val other = options.firstOrNull { it.id == otherId }
    val noun = if (session.riders) "rider" else "driver"
    SportsPanel(title = "Compare ${noun.replaceFirstChar(Char::uppercase)}s") {
        Column(Modifier.padding(horizontal = 10.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            if (other == null) {
                Text("Choose a second $noun to compare with ${selected.name}.", fontSize = 13.sp, color = SportsMuted)
                OutlinedButton(onClick = { picking = true }, modifier = Modifier.focusRequester(lead)) {
                    Text("Choose a ${noun.replaceFirstChar(Char::uppercase)}")
                }
            } else {
                ComparisonRow("Statistic", selected.name, other.name, heading = true)
                val fields = if (SportsRaceFormat.isQualifying(session)) QUALIFYING else RACE
                ComparisonRow("Position", selected.position?.let { "P$it" } ?: "—", other.position?.let { "P$it" } ?: "—")
                fields.forEach { (name, label) -> ComparisonRow(label, selected.stat(name) ?: "—", other.stat(name) ?: "—") }
                Text("Same session · — means not supplied. Gaps are to the leader, not between these entries.",
                     fontSize = 11.sp, color = SportsMuted)
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    OutlinedButton(onClick = { picking = true }, modifier = Modifier.focusRequester(lead)) {
                        Text("Change ${noun.replaceFirstChar(Char::uppercase)}")
                    }
                    OutlinedButton(onClick = { otherId = null; refocus = true }) { Text("Close") }
                }
            }
        }
    }
    if (picking) {
        PickerDialog(
            title = "Compare with", options = options.map { it.name },
            selected = options.indexOfFirst { it.id == otherId },
            onPick = { otherId = options[it].id; refocus = true }, onDismiss = { picking = false },
        )
    }
}

@Composable
private fun ComparisonRow(label: String, first: String, second: String, heading: Boolean = false) {
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(label, fontSize = 13.sp, color = SportsMuted, modifier = Modifier.weight(1f))
        listOf(first, second).forEach {
            Text(
                it, fontSize = 13.sp, fontWeight = if (heading) FontWeight.SemiBold else FontWeight.Normal, textAlign = TextAlign.End,
                maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f),
            )
        }
    }
}

/** The driver card's leading figures, in order (Expo `SportsRaceDriverCard` priority). */
private val PRIORITY = listOf("behindTime", "behindLaps", "lapsCompleted", "fastestLap", "pitsTaken", "totalTime")

private val RACE = listOf(
    "behindTime" to "Gap to leader", "behindLaps" to "Laps behind", "lapsCompleted" to "Laps",
    "fastestLap" to "Fastest lap", "pitsTaken" to "Pit stops",
)
private val QUALIFYING = listOf("qual1TimeMS" to "Q1", "qual2TimeMS" to "Q2", "qual3TimeMS" to "Q3")

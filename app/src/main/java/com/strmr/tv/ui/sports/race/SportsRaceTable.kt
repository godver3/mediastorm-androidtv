package com.strmr.tv.ui.sports.race

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.tv.material3.Text
import com.strmr.tv.sports.SportsF1Driver
import com.strmr.tv.sports.SportsRaceDriver
import com.strmr.tv.sports.SportsRaceEvent
import com.strmr.tv.sports.SportsRaceFormat
import com.strmr.tv.ui.Accent
import com.strmr.tv.ui.sports.SportsAmber
import com.strmr.tv.ui.sports.SportsColumnHeading
import com.strmr.tv.ui.sports.SportsMuted
import com.strmr.tv.ui.sports.SportsReading
import com.strmr.tv.ui.sports.SportsReadout

/**
 * A session's entries with position, laps and timing (Expo `SportsRaceTable`): "TIME / GAP"
 * (the gap to the leader, else session time or best lap), or "BEST LAP" with each entry's
 * Q1–Q3 times in qualifying. With an F1 timing archive a row adds interval, last lap, tyre,
 * stops and sectors. Every row is a stop for the remote: selecting it picks the entry for the
 * driver card, and [expandedId]'s row lists all of its labelled statistics.
 */
@Composable
fun SportsRaceTable(
    drivers: List<SportsRaceDriver>,
    session: SportsRaceEvent,
    selectedId: String?,
    telemetry: List<SportsF1Driver>,
    expandedId: String?,
    onSelect: (String) -> Unit,
) {
    val qualifying = SportsRaceFormat.isQualifying(session)
    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Row(Modifier.padding(horizontal = 10.dp), verticalAlignment = Alignment.CenterVertically) {
            SportsColumnHeading("Pos", Modifier.width(PositionWidth))
            SportsColumnHeading(if (session.riders) "Rider / Team" else "Driver / Team", Modifier.weight(1f))
            SportsColumnHeading("Laps", Modifier.width(LapsWidth))
            Text(
                if (qualifying) "BEST LAP" else "TIME / GAP", fontSize = 10.sp, fontWeight = FontWeight.SemiBold, color = SportsMuted,
                textAlign = TextAlign.End, modifier = Modifier.width(TimingWidth),
            )
        }
        drivers.forEach { driver ->
            val f1 = telemetry.firstOrNull { it.id == (driver.number ?: driver.id) }
            DriverRow(driver, qualifying, f1, driver.id == selectedId, driver.id == expandedId) { onSelect(driver.id) }
        }
    }
}

@Composable
private fun DriverRow(
    driver: SportsRaceDriver,
    qualifying: Boolean,
    f1: SportsF1Driver?,
    selected: Boolean,
    expanded: Boolean,
    onClick: () -> Unit,
) {
    val timing = if (qualifying) driver.stat("fastestLap") else SportsRaceFormat.timing(driver)
    SportsReadout(selected = selected, onClick = onClick) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                driver.position?.toString() ?: "–", fontSize = 17.sp, fontWeight = FontWeight.Bold,
                color = if (driver.position == 1) Accent else Color.White, modifier = Modifier.width(PositionWidth),
            )
            Column(Modifier.weight(1f)) {
                Text(
                    buildAnnotatedString {
                        append(driver.name)
                        driver.number?.let { withStyle(SpanStyle(color = SportsMuted, fontWeight = FontWeight.Normal)) { append("  #$it") } }
                    },
                    fontSize = 14.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis,
                )
                val team = listOfNotNull(driver.team?.ifEmpty { null }, driver.outcome).joinToString(" · ")
                if (team.isNotEmpty()) Text(team, fontSize = 12.sp, color = SportsMuted, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            Text(driver.stat("lapsCompleted") ?: "–", fontSize = 13.sp, color = SportsMuted, modifier = Modifier.width(LapsWidth))
            Column(Modifier.width(TimingWidth), horizontalAlignment = Alignment.End) {
                Text(timing ?: "–", fontSize = 13.sp, maxLines = 1)
                if (timing != null) {
                    Text((if (qualifying) "Best lap" else SportsRaceFormat.timingKind(driver)).orEmpty(), fontSize = 11.sp, color = SportsMuted)
                }
            }
        }
        if (qualifying) QualifyingTimes(driver, f1)
        if (f1 != null && !qualifying) Telemetry(f1)
        if (expanded) Statistics(driver)
    }
}

/** Q1–Q3 as reported, from the session or the F1 archive; "—" where no time was set. */
@Composable
private fun QualifyingTimes(driver: SportsRaceDriver, f1: SportsF1Driver?) {
    val times = (1..3).map { driver.stat("qual${it}TimeMS") ?: f1?.qualifyingTimes?.getOrNull(it - 1)?.ifEmpty { null } }
    if (times.all { it == null }) return
    Row(Modifier.padding(start = PositionWidth), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
        times.forEachIndexed { i, time -> SportsReading("Q${i + 1}", time ?: "—") }
        if (f1?.knockedOut == true) Text("Knocked out", fontSize = 12.sp, color = SportsAmber)
    }
}

/** An F1 archive's line for the entry: interval, last lap, tyre, stops, state, then sectors. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun Telemetry(f1: SportsF1Driver) {
    Column(Modifier.padding(start = PositionWidth), verticalArrangement = Arrangement.spacedBy(2.dp)) {
        FlowRow(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
            SportsReading("INT", f1.interval.ifEmpty { "—" })
            SportsReading("LAST", f1.lastLap.ifEmpty { "—" })
            f1.stints.lastOrNull()?.let { tyre -> SportsReading("TYRE", tyre.compound + (tyre.age?.let { " · ${it}L" } ?: "")) }
            f1.pitStops?.let { SportsReading("STOPS", "$it") }
            f1.state?.let { Text(it, fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = SportsAmber) }
        }
        if (f1.sectors.any { it.isNotEmpty() }) {
            Row(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                f1.sectors.forEachIndexed { i, sector -> SportsReading("S${i + 1}", sector.ifEmpty { "—" }) }
            }
        }
    }
}

/** Every statistic the provider sent for the entry, labelled. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun Statistics(driver: SportsRaceDriver) {
    FlowRow(Modifier.padding(start = PositionWidth, top = 4.dp), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
        Text(driver.result ?: "Published session details", fontSize = 12.sp, color = SportsMuted)
        driver.statistics.forEach { SportsReading(it.label, it.value) }
    }
}

private val PositionWidth = 34.dp
private val LapsWidth = 40.dp
private val TimingWidth = 100.dp

package com.strmr.tv.ui.sports.race

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.focusRestorer
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.tv.material3.ExperimentalTvMaterial3Api
import androidx.tv.material3.FilterChip
import androidx.tv.material3.OutlinedButton
import androidx.tv.material3.Text
import com.strmr.tv.sports.SportsF1Driver
import com.strmr.tv.sports.SportsRaceEvent
import com.strmr.tv.sports.SportsRaceFormat
import com.strmr.tv.ui.Accent
import com.strmr.tv.ui.sports.SportsMuted
import com.strmr.tv.ui.sports.SportsNotice
import com.strmr.tv.ui.sports.SportsPanel
import com.strmr.tv.ui.sports.SportsReadout

/**
 * The shown session's results (Expo `SportsRaceDetailTV`'s session view): broadcasts and the
 * snapshot line, notices for loading, late or missing data, Timing's result filter, the
 * classification ([SportsRaceTable]: eight entries on Overview with View All, every entry on
 * Timing), Timing's footnote and reported leaders, and where the figures come from.
 * [onViewAll] opens Timing from Overview. A weekend without sessions says so instead.
 */
@Composable
fun SportsRaceSessionResults(state: SportsRacePageState, telemetry: List<SportsF1Driver>, overview: Boolean, onViewAll: () -> Unit = {}) {
    val detail = state.detail
    if (detail == null) {
        state.weekend?.let { NoSessions(it) }
        return
    }
    val all = detail.participants
    val entries = state.entries
    val noun = if (detail.riders) "rider" else "driver"
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        val broadcasts = detail.broadcasts?.ifEmpty { null } ?: state.weekend?.broadcasts.orEmpty()
        if (broadcasts.isNotEmpty()) Text(broadcasts.joinToString(" · "), fontSize = 13.sp, color = SportsMuted)
        Text(SportsRaceFormat.updateLabel(detail, state.delayed), fontSize = 12.sp, color = SportsMuted)
        if (state.loading && all.isEmpty()) {
            SportsNotice("Loading ${detail.sessionType ?: "session"}", "Checking for supplied entries and classification.", loading = true)
        }
        if (state.delayed) {
            SportsNotice(
                "Updates delayed",
                if (all.isNotEmpty()) "Displayed results may be incomplete or from an earlier update."
                else "The latest session data could not be confirmed. No entries or classification are available in this view.",
                onRetry = state::retry,
            )
        }
        if (all.isEmpty() && !state.loading && !state.delayed) {
            val (title, message) = SportsRaceFormat.emptyNotice(detail)
            SportsNotice(title, message, onRetry = state::retry)
        }
        if (!overview && state.resultStatuses.isNotEmpty()) ResultFilter(state)
        if (all.isNotEmpty()) {
            val shown = if (overview) entries.take(OVERVIEW_ROWS) else entries
            val ranked = all.any { it.position != null }
            SportsPanel(
                title = if (ranked) "Classification" else "${noun.replaceFirstChar(Char::uppercase)} entries",
                note = "${shown.size} of ${all.size} ${noun}s",
            ) {
                if (!ranked && !state.loading) {
                    Text("Positions have not been supplied for this session.", fontSize = 12.sp, color = SportsMuted,
                         modifier = Modifier.padding(horizontal = 10.dp))
                }
                SportsRaceTable(
                    shown, detail, state.competitor?.id, telemetry,
                    expandedId = if (overview) null else state.expandedId,
                ) { id ->
                    state.competitorId = id
                    if (!overview) state.expandedId = id.takeIf { it != state.expandedId }
                }
            }
            if (overview && entries.size > OVERVIEW_ROWS) {
                OutlinedButton(onClick = onViewAll) {
                    Text("View All ${all.size} ${noun.replaceFirstChar(Char::uppercase)}s")
                }
            }
            if (!overview) {
                Text(
                    if (SportsRaceFormat.isQualifying(detail)) "Reported qualifying times · — means no time supplied. Elimination is shown only when reported."
                    else "Gap to leader when supplied; otherwise session time or best lap. Select a $noun for labelled statistics.",
                    fontSize = 12.sp, color = SportsMuted,
                )
            }
        }
        if (!overview && detail.isFinal && state.activeFilter == null) SessionLeaders(detail, saved = detail.stale || state.weekend?.stale == true)
        Text(SportsRaceFormat.sourceLine(detail), fontSize = 12.sp, color = SportsMuted)
    }
}

/** A weekend the provider lists without sessions (Expo's "Session schedule unavailable"). */
@Composable
private fun NoSessions(weekend: SportsRaceEvent) {
    val about = listOfNotNull(
        SportsRaceFormat.date(weekend.start), weekend.venue?.ifEmpty { null }, weekend.statusDetail?.ifEmpty { null },
        weekend.broadcasts?.joinToString(" · ")?.ifEmpty { null },
    )
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text(about.joinToString(" · "), fontSize = 13.sp, color = SportsMuted)
        SportsNotice(
            when (SportsRaceFormat.sessionState(weekend)) {
                "Cancelled" -> "Race weekend cancelled"
                "Postponed" -> "Race weekend postponed"
                else -> "Session schedule unavailable"
            },
            "No sessions are available for this weekend. Session selection and classifications will appear when the provider supplies them.",
        )
    }
}

/** Timing's result filter (Expo's All entries / Classified / Retired… pills). */
@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
private fun ResultFilter(state: SportsRacePageState) {
    Row(Modifier.focusRestorer(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        FilterChip(selected = state.activeFilter == null, onClick = { state.resultFilter = null }) { Text("All Entries") }
        state.resultStatuses.forEach { status ->
            FilterChip(selected = state.activeFilter == status, onClick = { state.resultFilter = status }) { Text(status) }
        }
    }
}

/**
 * The top three as reported, with the gap or time (Expo `SportsClassificationLeaders`);
 * "Saved" when the snapshot is stale.
 */
@Composable
private fun SessionLeaders(session: SportsRaceEvent, saved: Boolean) {
    val leaders = session.participants.filter { (it.position ?: 0) in 1..3 }.sortedBy { it.position }
    if (leaders.isEmpty()) return
    SportsPanel(title = if (saved) "Saved Session Leaders" else "Reported Session Leaders") {
        leaders.forEach { driver ->
            SportsReadout {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text("${driver.position}", fontSize = 24.sp, fontWeight = FontWeight.Bold, color = Accent, modifier = Modifier.width(24.dp))
                    Column(Modifier.weight(1f)) {
                        Text(driver.name, fontSize = 15.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        driver.team?.ifEmpty { null }?.let { Text(it, fontSize = 12.sp, color = SportsMuted, maxLines = 1) }
                    }
                    (driver.stat("behindTime") ?: driver.stat("totalTime"))?.let { Text(it, fontSize = 13.sp) }
                }
            }
        }
    }
}

private const val OVERVIEW_ROWS = 8

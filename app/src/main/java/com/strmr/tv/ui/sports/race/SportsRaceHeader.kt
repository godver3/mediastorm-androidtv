package com.strmr.tv.ui.sports.race

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.focusRestorer
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.tv.material3.Button
import androidx.tv.material3.ExperimentalTvMaterial3Api
import androidx.tv.material3.FilterChip
import androidx.tv.material3.Icon
import androidx.tv.material3.OutlinedButton
import androidx.tv.material3.Text
import com.strmr.tv.sports.SportsLifecycle
import com.strmr.tv.sports.SportsRaceEvent
import com.strmr.tv.sports.SportsRaceFormat
import com.strmr.tv.ui.sports.SportsMuted
import com.strmr.tv.ui.sports.SportsSpoilers
import com.strmr.tv.ui.sports.SportsStatusPill
import com.strmr.tv.ui.sports.SportsWatchTarget

/**
 * The race page's top (Expo's editorial header and event context): series and the shown
 * session's status, the weekend's title and the session's line (type, state, place, time),
 * then Watch Live / Find Streams, the session picker and Hide Scores, and the page's tabs.
 * One block, so the title stays in view while the remote is on the controls; [firstFocus]
 * lands on its first control and [tabFocus] on the shown tab.
 */
@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
fun SportsRaceHeader(
    state: SportsRacePageState,
    weekend: SportsRaceEvent,
    leagueName: String,
    spoilers: Boolean,
    firstFocus: FocusRequester,
    tabFocus: FocusRequester,
    onWatch: (SportsWatchTarget) -> Unit,
    onSessions: () -> Unit,
) {
    val session = state.detail
    val watch = session?.let { watchTarget(weekend, it) }
    val choosesSession = weekend.sessions.size > 1
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Text(leagueName.uppercase(), fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = SportsMuted)
            session?.let { SportsStatusPill(it.lifecycle) }
        }
        Text(weekend.title.trim(), fontSize = 28.sp, fontWeight = FontWeight.SemiBold, maxLines = 2, overflow = TextOverflow.Ellipsis)
        Text(
            when {
                state.tab == SportsRaceTab.CHAMPIONSHIP && !spoilers -> "Championship standings"
                session != null -> SportsRaceFormat.contextLine(session, weekend)
                else -> "Race weekend"
            },
            fontSize = 15.sp, fontWeight = FontWeight.SemiBold,
        )
        Row(
            Modifier.padding(top = 10.dp).focusRestorer(),
            horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically,
        ) {
            if (watch != null) {
                Button(onClick = { onWatch(watch) }, modifier = Modifier.focusRequester(firstFocus)) {
                    Text(if (watch.live) "Watch Live" else "Find Streams")
                }
            }
            if (choosesSession) {
                OutlinedButton(onClick = onSessions, modifier = if (watch == null) Modifier.focusRequester(firstFocus) else Modifier) {
                    Text("Session: ${session?.sessionType ?: "Choose"}")
                    Icon(Icons.Filled.ArrowDropDown, contentDescription = null, modifier = Modifier.size(20.dp))
                }
            }
            OutlinedButton(
                onClick = { SportsSpoilers.toggle() },
                modifier = if (watch == null && !choosesSession) Modifier.focusRequester(firstFocus) else Modifier,
            ) { Text(if (spoilers) "Show Scores" else "Hide Scores") }
        }
        if (!spoilers) {
            Row(Modifier.padding(top = 4.dp).focusRestorer(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                state.tabs.forEach { tab ->
                    FilterChip(
                        selected = state.tab == tab, onClick = { state.tab = tab },
                        modifier = if (state.tab == tab) Modifier.focusRequester(tabFocus) else Modifier,
                    ) { Text(tab.label) }
                }
            }
        }
    }
}

/**
 * What Watch searches for (Expo `raceWatchTarget`): the session with its weekend, while it is
 * live or yet to run and neither is stale (the server then can't match it).
 */
private fun watchTarget(weekend: SportsRaceEvent, session: SportsRaceEvent): SportsWatchTarget? {
    if (weekend.stale || session.stale) return null
    if (session.lifecycle !in setOf(SportsLifecycle.LIVE, SportsLifecycle.SCHEDULED, SportsLifecycle.DELAYED)) return null
    return SportsWatchTarget(
        id = session.id, title = SportsRaceFormat.watchTitle(weekend, session), live = session.isLive,
        kind = "racing", parentId = weekend.id,
    )
}

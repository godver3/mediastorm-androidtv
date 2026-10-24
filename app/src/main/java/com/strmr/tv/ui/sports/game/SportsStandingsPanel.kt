package com.strmr.tv.ui.sports.game

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.tv.material3.ExperimentalTvMaterial3Api
import androidx.tv.material3.FilterChip
import androidx.tv.material3.Text
import com.strmr.tv.ui.sports.SportsMuted
import com.strmr.tv.ui.sports.SportsPanel
import com.strmr.tv.ui.sports.SportsReadout

/**
 * The Standings tab: the table snapshot sent with the game (Expo `SportsStandingsPanel`), one
 * group at a time when there are several (conferences), this game's sides highlighted. Soccer
 * tables show played, goal difference and points; others conference and overall records. Expo's
 * team search is left out on TV.
 */
@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
fun SportsStandingsPanel(page: SportsGamePage) {
    val groups = page.detail.standings
    var groupIndex by remember { mutableIntStateOf(0) }
    val index = if (groupIndex in groups.indices) groupIndex else 0
    val soccer = page.kind == SportsKind.SOCCER
    val chipFocus = remember { FocusRequester() }
    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        if (groups.size > 1) {
            SportsChipRow(chipFocus, scrolls = true) {
                groups.forEachIndexed { offset, group ->
                    FilterChip(
                        selected = offset == index, onClick = { groupIndex = offset },
                        modifier = if (offset == index) Modifier.focusRequester(chipFocus) else Modifier,
                    ) { Text(standingsChipLabel(group.title)) }
                }
            }
        }
        groups.getOrNull(index)?.let { group ->
            SportsPanel(
                group.title.ifEmpty { "Standings" }, Modifier.widthIn(max = 650.dp),
                note = "Table snapshot supplied with the feed · May be newer than this game",
            ) {
                StandingRow("#", "Team", if (soccer) listOf("GP", "GD", "PTS") else listOf("CONF", "OVERALL"), soccer, heading = true)
                group.rows.forEach { row ->
                    val playing = row.teamId == page.game.homeTeam.id || row.teamId == page.game.awayTeam.id
                    val values = if (soccer) listOf(row.played, row.goalDifference, row.points) else listOf(row.conferenceRecord, row.record)
                    SportsReadout(selected = playing) {
                        StandingRow(row.rank ?: "–", row.name, values.map { it?.ifEmpty { null } ?: "–" }, soccer, bold = playing)
                    }
                }
                Text(
                    if (soccer) "GP: games played · GD: goal difference · PTS: points. A dash means the value was not supplied."
                    else "CONF: conference record · OVERALL: overall record. A dash means the value was not supplied.",
                    fontSize = 11.sp, color = SportsMuted, modifier = Modifier.padding(start = 10.dp, end = 10.dp, top = 4.dp),
                )
            }
        }
    }
}

@Composable
private fun StandingRow(rank: String, name: String, values: List<String>, soccer: Boolean, heading: Boolean = false, bold: Boolean = false) {
    val size = if (heading) 11.sp else 14.sp
    val color = if (heading) SportsMuted else Color.White
    val weight = if (bold) FontWeight.Bold else FontWeight.Normal
    Row(if (heading) Modifier.padding(horizontal = 10.dp) else Modifier, verticalAlignment = Alignment.CenterVertically) {
        Text(rank, fontSize = size, color = color, fontWeight = weight, modifier = Modifier.width(35.dp))
        Text(name, fontSize = size, color = color, fontWeight = weight, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
        values.forEach {
            Text(
                it, fontSize = size, color = color, fontWeight = weight, maxLines = 1, textAlign = TextAlign.End,
                modifier = Modifier.width(if (soccer) 55.dp else 85.dp),
            )
        }
    }
}

/** A group's chip: its title without the season and "Standings" ("Big Ten Conference"). */
fun standingsChipLabel(title: String): String =
    title.replace(Regex("""^\d{4}(?:-\d{2})? """), "").replace(Regex(""" Standings$"""), "")

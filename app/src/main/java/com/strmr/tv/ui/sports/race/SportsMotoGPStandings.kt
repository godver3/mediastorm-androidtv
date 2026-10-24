package com.strmr.tv.ui.sports.race

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.tv.material3.Text
import com.strmr.tv.sports.SportsMotoGPEnrichment
import com.strmr.tv.sports.SportsMotoGPRider
import com.strmr.tv.sports.SportsRaceFormat
import com.strmr.tv.sports.parseInstant
import com.strmr.tv.ui.Accent
import com.strmr.tv.ui.sports.SportsColumnHeading
import com.strmr.tv.ui.sports.SportsMuted
import com.strmr.tv.ui.sports.SportsNotice
import com.strmr.tv.ui.sports.SportsPanel
import com.strmr.tv.ui.sports.SportsReadout

/**
 * The MotoGP riders' championship (Expo `SportsMotoGPStandings`): the season's table with
 * points, wins and podiums, notices while it loads or when it's late, and where it comes from.
 * Kept apart from the shown session's results.
 */
@Composable
fun SportsMotoGPStandings(standings: SportsMotoGPEnrichment?, failed: Boolean, onRetry: () -> Unit) {
    val riders = standings?.standings.orEmpty()
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Column {
            Text(listOfNotNull(standings?.season?.toString(), "MotoGP championship").joinToString(" "), fontSize = 20.sp, fontWeight = FontWeight.SemiBold)
            Text(
                "${if (standings?.captured == true) "Captured season standings" else "Latest season standings"} · Separate from the selected race's results",
                fontSize = 12.sp, color = SportsMuted,
            )
        }
        if (standings == null && !failed) SportsNotice("Loading championship", "Fetching official rider standings.", loading = true)
        if (failed || standings?.stale == true || standings?.unavailable == true) {
            SportsNotice(
                if (riders.isNotEmpty()) "Standings updates delayed" else "Standings unavailable",
                standings?.reason ?: "The latest standings could not be reached. Any displayed values are from the last successful update.",
                onRetry = onRetry,
            )
        }
        if (riders.isNotEmpty()) {
            SportsPanel(title = null) {
                Row(Modifier.padding(horizontal = 10.dp)) {
                    SportsColumnHeading("Pos", Modifier.width(40.dp))
                    SportsColumnHeading("Rider / Team", Modifier.weight(1f))
                    Text("POINTS", fontSize = 10.sp, fontWeight = FontWeight.SemiBold, color = SportsMuted)
                }
                riders.forEach { RiderRow(it) }
            }
        }
        standings?.let {
            val checked = it.updatedAt?.let(::parseInstant)?.let { at -> "Checked ${SportsRaceFormat.date(at)}" } ?: "Update time unavailable"
            Text("${it.source.ifEmpty { "MotoGP" }} · $checked", fontSize = 12.sp, color = SportsMuted)
        }
    }
}

@Composable
private fun RiderRow(rider: SportsMotoGPRider) {
    SportsReadout {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(rider.position?.toString() ?: "—", fontSize = 15.sp, fontWeight = FontWeight.Bold,
                 color = if (rider.position == 1) Accent else Color.White, modifier = Modifier.width(40.dp))
            Column(Modifier.weight(1f)) {
                Text(rider.name + (rider.number?.let { "  #$it" } ?: ""), fontSize = 15.sp, fontWeight = FontWeight.SemiBold,
                     maxLines = 1, overflow = TextOverflow.Ellipsis)
                val about = listOfNotNull(
                    rider.team?.ifEmpty { null },
                    rider.wins?.let { if (it == 1) "1 win" else "$it wins" },
                    rider.podiums?.let { if (it == 1) "1 podium" else "$it podiums" },
                )
                if (about.isNotEmpty()) Text(about.joinToString(" · "), fontSize = 12.sp, color = SportsMuted, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            Text(points(rider.points), fontSize = 19.sp, fontWeight = FontWeight.Bold, color = Accent, textAlign = TextAlign.End,
                 modifier = Modifier.width(64.dp))
        }
    }
}

/** Whole points without a decimal; MotoGP's half points keep theirs. */
private fun points(value: Double?): String = when {
    value == null -> "—"
    value % 1.0 == 0.0 -> value.toLong().toString()
    else -> value.toString()
}

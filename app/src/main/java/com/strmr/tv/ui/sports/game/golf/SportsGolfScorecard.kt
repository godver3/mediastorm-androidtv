package com.strmr.tv.ui.sports.game.golf

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.tv.material3.ExperimentalTvMaterial3Api
import androidx.tv.material3.FilterChip
import androidx.tv.material3.Text
import com.strmr.tv.sports.SportsGolfEntry
import com.strmr.tv.sports.SportsGolfRound
import com.strmr.tv.ui.sports.SportsMuted
import com.strmr.tv.ui.sports.SportsPanel
import com.strmr.tv.ui.sports.SportsReadout
import com.strmr.tv.ui.sports.game.SportsDigits
import com.strmr.tv.ui.sports.game.SportsChipRow
import com.strmr.tv.ui.sports.game.SportsGamePage

private val Under = Color(0xFF163D41)
private val Over = Color(0xFF473331)
private val Even = Color(0xFF213044)

/**
 * A golfer's scorecard (Expo `SportsGolfOverview`'s scorecard panel, tvOS `SportsGolfScorecard`):
 * the tournament score to par, a chip per round ("R2 · 68"), then the round's front and back nine
 * hole by hole, teal below par and warm above; the venue and channels under it. One stop per nine.
 * [round] is the round picked (none: the last).
 */
@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
fun SportsGolfScorecard(page: SportsGamePage, player: SportsGolfEntry?, round: Int?, onRound: (Int) -> Unit) {
    val rounds = player?.rounds.orEmpty()
    val shown = rounds.firstOrNull { it.round == round } ?: rounds.lastOrNull()
    val chosen = remember { FocusRequester() }
    SportsPanel(player?.let { "${it.name} · Round Scores" } ?: "Scorecard") {
        if (player == null) {
            SportsReadout { Text("Select a golfer when the leaderboard is available.", fontSize = 14.sp, color = SportsMuted) }
        } else {
            SportsReadout {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("Tournament · To par", fontSize = 12.sp, color = SportsMuted)
                    Spacer(Modifier.weight(1f))
                    Text(player.score ?: "–", fontSize = 26.sp, fontWeight = FontWeight.Bold, style = SportsDigits)
                }
            }
            if (rounds.isNotEmpty()) {
                SportsChipRow(chosen, Modifier.padding(horizontal = 10.dp, vertical = 4.dp)) {
                    rounds.forEach { item ->
                        FilterChip(
                            selected = item.round == shown?.round, onClick = { onRound(item.round) },
                            modifier = if (item.round == shown?.round) Modifier.focusRequester(chosen) else Modifier,
                        ) { Text("R${item.round} · ${item.strokes ?: "–"}") }
                    }
                }
            }
            if (shown != null) {
                Text(
                    listOfNotNull("Round ${shown.round}", "${shown.strokes ?: "–"} strokes", shown.toPar?.let { "$it to par" }).joinToString(" · "),
                    fontSize = 12.sp, color = SportsMuted, modifier = Modifier.padding(horizontal = 10.dp),
                )
                Nine(shown, 1, "Front Nine")
                Nine(shown, 10, "Back Nine")
                Text("Hole scores · Teal below par · Warm above par", fontSize = 12.sp, color = SportsMuted, modifier = Modifier.padding(horizontal = 10.dp))
            } else {
                Text("Round and hole scores have not been supplied.", fontSize = 14.sp, color = SportsMuted, modifier = Modifier.padding(horizontal = 10.dp))
            }
        }
        val place = listOfNotNull(page.game.venue, page.game.broadcasts?.joinToString(" · ")).filter { it.isNotEmpty() }
        if (place.isNotEmpty()) {
            Column(Modifier.padding(start = 10.dp, end = 10.dp, top = 6.dp)) { place.forEach { Text(it, fontSize = 12.sp, color = SportsMuted) } }
        }
    }
}

@Composable
private fun Nine(round: SportsGolfRound, start: Int, title: String) {
    SportsReadout(Modifier.semantics { contentDescription = "Round ${round.round}, holes $start to ${start + 8}" }) {
        Text(title, fontSize = 12.sp, color = SportsMuted)
        Row(horizontalArrangement = Arrangement.spacedBy(3.dp)) {
            (start until start + 9).forEach { number ->
                val hole = round.holes.firstOrNull { it.hole == number }
                val par = hole?.toPar.orEmpty()
                val fill = if (par.startsWith("-")) Under else if (par.startsWith("+")) Over else Even
                Column(
                    Modifier.weight(1f).clip(RoundedCornerShape(5.dp)).background(fill).padding(vertical = 5.dp),
                    horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(3.dp),
                ) {
                    Text("$number", fontSize = 10.sp, color = SportsMuted)
                    Text(hole?.strokes?.ifEmpty { null } ?: "–", fontSize = 14.sp, fontWeight = FontWeight.SemiBold, style = SportsDigits)
                }
            }
        }
    }
}

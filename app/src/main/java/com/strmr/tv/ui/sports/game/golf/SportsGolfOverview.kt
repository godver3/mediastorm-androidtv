package com.strmr.tv.ui.sports.game.golf

import androidx.compose.foundation.focusGroup
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.tv.material3.ExperimentalTvMaterial3Api
import androidx.tv.material3.FilterChip
import androidx.tv.material3.Text
import com.strmr.tv.sports.SportsGolfEntry
import com.strmr.tv.sports.SportsLifecycle
import com.strmr.tv.ui.sports.SportsMuted
import com.strmr.tv.ui.sports.SportsPanel
import com.strmr.tv.ui.sports.SportsReadout
import com.strmr.tv.ui.sports.game.SportsDigits
import com.strmr.tv.ui.sports.game.SportsColumn
import com.strmr.tv.ui.sports.game.SportsColumns
import com.strmr.tv.ui.sports.game.SportsGamePage
import kotlin.math.ceil

private const val PageSize = 8
private val RoundWidth = 32.dp

/**
 * The Overview of PGA and LPGA tournaments (Expo `SportsGolfOverview`, its TV layout, tvOS
 * `SportsGolfOverview`): the leaderboard in the feed's order, eight golfers a page with each
 * round's strokes and the score to par, beside the chosen golfer's scorecard. Select on a golfer
 * shows their card; the round chips switch rounds. Scores are the provider's; the row order isn't
 * presented as a rank.
 */
@Composable
fun SportsGolfOverview(page: SportsGamePage) {
    var selected by remember(page.game.id) { mutableStateOf<String?>(null) }
    var round by remember(page.game.id) { mutableStateOf<Int?>(null) }
    var pageIndex by remember(page.game.id) { mutableIntStateOf(0) }
    val players = if (page.lifecycle == SportsLifecycle.SCHEDULED) emptyList() else page.detail.leaderboard
    val player = players.firstOrNull { it.id == selected } ?: players.firstOrNull()
    SportsColumns(fullHeight = true) {
        SportsColumn(1.2f) {
            Leaderboard(page, players, player, pageIndex, onPage = { pageIndex = it }, onPick = { selected = it; round = null })
        }
        SportsColumn(1f) { SportsGolfScorecard(page, player, round, onRound = { round = it }) }
    }
}

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
private fun Leaderboard(
    page: SportsGamePage,
    players: List<SportsGolfEntry>,
    chosen: SportsGolfEntry?,
    pageIndex: Int,
    onPage: (Int) -> Unit,
    onPick: (String) -> Unit,
) {
    val pages = maxOf(1, ceil(players.size / PageSize.toDouble()).toInt())
    val current = minOf(pageIndex, pages - 1)
    val visible = players.drop(current * PageSize).take(PageSize)
    SportsPanel("Leaderboard", note = if (players.isEmpty()) null else "${players.size} golfers · Provider order") {
        if (pages > 1) {
            // Dimmed, not disabled, at either end: the chip keeps the remote's focus.
            Row(
                Modifier.padding(horizontal = 10.dp, vertical = 4.dp).focusGroup(),
                verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                FilterChip(selected = false, onClick = { onPage(maxOf(0, current - 1)) }, modifier = Modifier.alpha(if (current == 0) 0.45f else 1f)) {
                    Text("Previous Golfers")
                }
                FilterChip(
                    selected = false, onClick = { onPage(minOf(pages - 1, current + 1)) },
                    modifier = Modifier.alpha(if (current == pages - 1) 0.45f else 1f),
                ) { Text("Next Golfers") }
                Text("${current + 1} / $pages", fontSize = 12.sp, color = SportsMuted)
            }
        }
        Row(Modifier.padding(horizontal = 10.dp), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            Text("Player", fontSize = 12.sp, color = SportsMuted, modifier = Modifier.weight(1f))
            (1..4).forEach { Text("R$it", fontSize = 12.sp, color = SportsMuted, textAlign = TextAlign.Center, modifier = Modifier.width(RoundWidth)) }
            Text("To par", fontSize = 12.sp, color = SportsMuted, textAlign = TextAlign.Center, modifier = Modifier.width(50.dp))
        }
        if (visible.isEmpty()) {
            SportsReadout {
                Text(
                    if (page.lifecycle == SportsLifecycle.SCHEDULED) "Scores will appear when play begins." else "Leaderboard not available yet.",
                    fontSize = 14.sp, color = SportsMuted,
                )
            }
        }
        visible.forEach { golfer ->
            SportsReadout(
                Modifier.semantics { contentDescription = "${golfer.name}, ${golfer.score ?: "score unavailable"}, view scorecard" },
                selected = golfer.id == chosen?.id, onClick = { onPick(golfer.id) },
            ) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(
                        golfer.name, fontSize = 15.sp, fontWeight = FontWeight.SemiBold, maxLines = 2, overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f),
                    )
                    (1..4).forEach { number ->
                        Text(
                            golfer.rounds.firstOrNull { it.round == number }?.strokes ?: "–", fontSize = 14.sp, color = SportsMuted,
                            textAlign = TextAlign.Center, style = SportsDigits, modifier = Modifier.width(RoundWidth),
                        )
                    }
                    Text(
                        golfer.score ?: "–", fontSize = 15.sp, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center,
                        style = SportsDigits, modifier = Modifier.width(50.dp),
                    )
                }
            }
        }
    }
}

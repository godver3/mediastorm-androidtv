package com.strmr.mobile.ui.sports.game

import com.strmr.mobile.ui.sports.*
import com.strmr.mobile.ui.sports.SportsChip
import com.strmr.tv.sports.*
import com.strmr.tv.ui.sports.*
import com.strmr.tv.ui.sports.game.*
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.material3.Text
import com.strmr.mobile.ui.sports.SportsMuted
import com.strmr.mobile.ui.sports.SportsPanel
import com.strmr.mobile.ui.sports.SportsReadout
import com.strmr.mobile.ui.sports.game.football.SportsFootballPlayRow

/**
 * The Play-by-Play / Commentary tab (Expo `PlaysPanel`): filters (all or scoring plays, and
 * soccer's cards and substitutions or hockey's penalties and shots), the periods, then the plays
 * newest first, 16 to a page. Newer / Older page from the top; Older Plays under the last row
 * turns the page and puts focus on its first play, so the remote never has to climb back up.
 * NFL and college football list their plays on a timeline with each side's logo (Expo `FootballPlays`).
 */
@Composable
fun SportsPlayByPlay(page: SportsGamePage) {
    var filter by remember { mutableStateOf(SportsPlayFeed.Filter.ALL) }
    var period by remember { mutableStateOf<String?>(null) }
    var pageIndex by remember { mutableIntStateOf(0) }
    // Where focus goes once a turned page has been drawn.
    var landing by remember { mutableStateOf<FocusRequester?>(null) }
    val filterFocus = remember { FocusRequester() }
    val periodFocus = remember { FocusRequester() }
    val newer = remember { FocusRequester() }
    val older = remember { FocusRequester() }
    val firstPlay = remember { FocusRequester() }
    val feed = SportsPlayFeed(page.detail.plays, page.kind, filter, period, pageIndex)

    LaunchedEffect(landing, feed.currentPage) {
        val target = landing ?: return@LaunchedEffect
        withFrameNanos { }
        runCatching { target.requestFocus() }
        landing = null
    }
    /** Turns to [index]; a pager chip that can't turn any further hands focus to the other one. */
    fun turn(index: Int, then: FocusRequester?) {
        pageIndex = index.coerceIn(0, feed.pageCount - 1)
        landing = then
    }

    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(11.dp)) {
        SportsChipRow(filterFocus, scrolls = true) {
            feed.filters.forEach { choice ->
                SportsChip(
                    selected = choice == filter,
                    onClick = { filter = choice; pageIndex = 0 },
                    modifier = if (choice == filter) Modifier.focusRequester(filterFocus) else Modifier,
                ) { Text(feed.label(choice)) }
            }
        }
        if (feed.periods.size > 1) {
            SportsChipRow(periodFocus, scrolls = true) {
                (listOf(null) + feed.periods).forEach { choice ->
                    SportsChip(
                        selected = choice == feed.activePeriod,
                        onClick = { period = choice; pageIndex = 0 },
                        modifier = if (choice == feed.activePeriod) Modifier.focusRequester(periodFocus) else Modifier,
                    ) { Text(choice ?: "All Periods") }
                }
            }
        }
        SportsChipRow(null) {
            if (feed.pageCount > 1) {
                // A page chip that can't turn further is dimmed and skipped by the remote (TV
                // chips stay focusable when disabled, with no focus look).
                val first = feed.currentPage == 0
                val last = feed.currentPage == feed.pageCount - 1
                SportsChip(
                    selected = false, enabled = !first,
                    modifier = Modifier.focusRequester(newer).focusProperties { canFocus = !first },
                    onClick = { turn(feed.currentPage - 1, if (feed.currentPage - 1 == 0) older else null) },
                ) { Text("Newer Plays") }
                SportsChip(
                    selected = false, enabled = !last,
                    modifier = Modifier.focusRequester(older).focusProperties { canFocus = !last },
                    onClick = { turn(feed.currentPage + 1, if (feed.currentPage + 1 == feed.pageCount - 1) newer else null) },
                ) { Text("Older Plays") }
            }
            Text(feed.summary, fontSize = 12.sp, color = SportsMuted)
        }
        SportsPanel(null) {
            if (feed.visible.isEmpty()) SportsReadout { Text(feed.emptyText, fontSize = 14.sp, color = SportsMuted) }
            feed.pagePlays.forEachIndexed { index, play ->
                key(play.id) {
                    val modifier = if (index == 0) Modifier.focusRequester(firstPlay) else Modifier
                    if (page.isFootballLeague) SportsFootballPlayRow(page, play, modifier) else SportsPlayRow(page, play, modifier)
                }
            }
        }
        if (feed.currentPage < feed.pageCount - 1) {
            SportsChip(selected = false, onClick = { turn(feed.currentPage + 1, firstPlay) }) { Text("Older Plays") }
        }
    }
}

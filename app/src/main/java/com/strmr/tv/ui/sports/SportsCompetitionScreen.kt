package com.strmr.tv.ui.sports

import androidx.compose.foundation.focusGroup
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.tv.material3.Text
import com.strmr.tv.sports.SportsCycling
import com.strmr.tv.sports.SportsHubFilters
import com.strmr.tv.sports.isRacing
import com.strmr.tv.ui.Accent
import com.strmr.tv.ui.PageMargin
import com.strmr.tv.ui.pageBackground
import com.strmr.tv.ui.sports.cycling.SportsCyclingCalendar
import com.strmr.tv.ui.sports.race.SportsRacingCalendar
import com.strmr.tv.ui.sports.standings.SportsLeagueOverview

/**
 * The hub's Competition Details (Expo `SportsHubTV`'s details view) as a page: the cycling race
 * calendar ([SportsCyclingCalendar]), the race weekends and championships ([SportsRacingCalendar],
 * open on the chosen series), or a team competition's standings and games ([SportsLeagueOverview]).
 * Back returns to the hub.
 */
@Composable
fun SportsCompetitionScreen(sport: String, league: String) {
    val hub = SportsHub.state
    val chosen = hub.leagues[league]
    val racing = sport == SportsHubFilters.MOTORSPORT || chosen?.isRacing == true
    // The remote starts on the view's first control.
    val first = remember { FocusRequester() }
    LaunchedEffect(Unit) {
        withFrameNanos { }
        runCatching { first.requestFocus() }
    }
    val entry = Modifier.focusRequester(first).focusGroup()
    LazyColumn(
        Modifier.fillMaxSize().pageBackground(),
        contentPadding = PaddingValues(start = PageMargin, end = PageMargin, top = 40.dp, bottom = 40.dp),
        verticalArrangement = Arrangement.spacedBy(18.dp),
    ) {
        when {
            sport == SportsCycling.SPORT -> item { Box(entry) { SportsCyclingCalendar() } }
            racing -> item { Box(entry) { SportsRacingCalendar(initialSeries = league.takeIf { chosen?.isRacing == true }) } }
            else -> {
                item {
                    SportsBrowseHeading(
                        "COMPETITION DETAILS", hub.leagueName(league),
                        "Season standings and ${hub.day.label.lowercase()}'s games. Select a game to open it.",
                    )
                }
                item { Box(entry) { SportsLeagueOverview(league) } }
            }
        }
    }
}

/** A browse view's heading (Expo `SportsBrowseHeading`): accent eyebrow, title and a line about it. */
@Composable
fun SportsBrowseHeading(eyebrow: String, title: String, description: String) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(eyebrow, fontSize = 11.sp, fontWeight = FontWeight.Bold, color = Accent, letterSpacing = 2.sp)
        Text(title, fontSize = 30.sp, fontWeight = FontWeight.SemiBold)
        Text(description, fontSize = 13.sp, color = SportsMuted)
    }
}

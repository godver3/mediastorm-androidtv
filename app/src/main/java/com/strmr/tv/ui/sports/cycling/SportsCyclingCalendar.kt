package com.strmr.tv.ui.sports.cycling

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.tv.material3.ExperimentalTvMaterial3Api
import androidx.tv.material3.FilterChip
import androidx.tv.material3.Icon
import androidx.tv.material3.OutlinedButton
import androidx.tv.material3.Text
import com.strmr.tv.api.ApiClient
import com.strmr.tv.sports.SportsCycling
import com.strmr.tv.sports.SportsCyclingFeed
import com.strmr.tv.sports.SportsCyclingRace
import com.strmr.tv.sports.sportsCycling
import com.strmr.tv.ui.Accent
import com.strmr.tv.ui.LocalNavigator
import com.strmr.tv.ui.Page
import com.strmr.tv.ui.PickerDialog
import com.strmr.tv.ui.sports.SportsBrowseHeading
import com.strmr.tv.ui.sports.SportsHub
import com.strmr.tv.ui.sports.SportsMuted
import com.strmr.tv.ui.sports.SportsNotice
import com.strmr.tv.ui.sports.SportsPanel
import com.strmr.tv.ui.sports.SportsReadout
import com.strmr.tv.ui.sports.game.SportsChipRow
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import java.time.LocalDate

/** A calendar filter's choices: id to label. */
private val DATES = listOf("all" to "All Dates", "current" to "Current Race Dates", "upcoming" to "Upcoming Races", "past" to "Past Race Dates")
private val CATEGORIES = listOf("all" to "All Road Cycling", "men" to "Men's Races", "women" to "Women's Races")
private val FORMATS = listOf("all" to "All Formats", "stage-race" to "Stage Races", "one-day" to "One-Day Races")

/**
 * Cycling's Competition Details (Expo `SportsCyclingHub`): the organizers' race calendar, current
 * races first, then upcoming ones and recent history, narrowed by dates, category, format and
 * favourites; 24 at a time. A race opens its page. Reloads every 5 min.
 */
@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
fun SportsCyclingCalendar() {
    val hub = SportsHub.state
    var feed by remember { mutableStateOf(hub.cyclingFeed) }
    var failed by remember { mutableStateOf(false) }
    var retries by remember { mutableIntStateOf(0) }
    var dates by rememberSaveable { mutableStateOf("all") }
    var category by rememberSaveable { mutableStateOf("all") }
    var format by rememberSaveable { mutableStateOf("all") }
    var favorites by rememberSaveable { mutableStateOf(false) }
    var shown by remember { mutableIntStateOf(24) }
    var picking by remember { mutableStateOf<List<Pair<String, String>>?>(null) }
    LaunchedEffect(retries) {
        while (true) {
            try {
                feed = ApiClient.sportsCycling()
                failed = false
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                failed = true
            }
            delay(300_000)
        }
    }
    val today = SportsCycling.today()
    val followed = hub.preferences?.preferences?.competitionIds.orEmpty()
    val pinned = hub.preferences?.preferences?.pinnedSportIds.orEmpty()
    val races = feed?.races.orEmpty().filter { race ->
        (!favorites || SportsCycling.isFollowed(race, followed, pinned)) && (format == "all" || race.format == format) &&
            (dates == "all" || SportsCycling.period(race, today).name.lowercase() == dates) && (category == "all" || race.category == category)
    }.sortedWith(SportsCycling.calendarOrder(today).thenBy { if (it.followId in followed) 0 else 1 })

    Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
        SportsBrowseHeading("ROAD CYCLING / STAGE ATLAS", "The Race Calendar",
                            "Grand Tours, stage races and one-day classics. Published results, routes and standings.")
        SportsChipRow(null) {
            listOf(DATES to dates, CATEGORIES to category, FORMATS to format).forEach { (choices, value) ->
                OutlinedButton(onClick = { picking = choices }) {
                    Text(choices.first { it.first == value }.second)
                    Icon(Icons.Filled.ArrowDropDown, contentDescription = null, modifier = Modifier.size(20.dp))
                }
            }
            FilterChip(selected = favorites, onClick = { favorites = !favorites }) { Text("My Favorites") }
        }
        Text("Current dates first, then upcoming races and recent history. Dates do not indicate live coverage.", fontSize = 13.sp, color = SportsMuted)
        if (failed || feed?.delayed == true) {
            SportsNotice("Cycling updates delayed", "Some cycling updates are unavailable. Displayed races may be from an earlier update.",
                         onRetry = { retries++ })
        }
        if (feed == null && !failed) SportsNotice("Loading cycling coverage", "Fetching the organizers' calendars.", loading = true)
        if (feed?.state == "unsupported" || feed?.state == "pending") {
            SportsNotice("Cycling results are not connected yet",
                         "Race schedules, stage results and overall standings will appear here when coverage is connected.")
        }
        coverageLine(feed)?.let { Text(it, fontSize = 13.sp, color = SportsMuted) }
        races.take(shown).forEach { RaceRow(it, today) }
        if (races.size > shown) {
            OutlinedButton(onClick = { shown += 24 }) { Text("Show More Races (${minOf(shown, races.size)} of ${races.size})") }
        }
        if (feed != null && feed!!.races.isNotEmpty() && races.isEmpty()) {
            Text(if (favorites) "No favorite races match. Follow a race in Customize Sports, or show all cycling."
                 else "No races returned for this selection.", fontSize = 13.sp, color = SportsMuted)
        }
        if (feed?.races?.isNotEmpty() == true) {
            Text("Organizer schedules and published results · Grand Tours, stage races and spring classics. Live timing is not connected.",
                 fontSize = 12.sp, color = SportsMuted)
        }
    }
    picking?.let { choices ->
        val current = when (choices) { DATES -> dates; CATEGORIES -> category; else -> format }
        PickerDialog(
            title = when (choices) { DATES -> "Race Dates"; CATEGORIES -> "Category"; else -> "Format" },
            options = choices.map { it.second }, selected = choices.indexOfFirst { it.first == current },
            onPick = { i ->
                val id = choices[i].first
                when (choices) { DATES -> dates = id; CATEGORIES -> category = id; else -> format = id }
                shown = 24
            },
            onDismiss = { picking = null },
        )
    }
}

private fun coverageLine(feed: SportsCyclingFeed?): String? = feed?.coverage?.filter { it.state != "available" }
    ?.joinToString(" · ") { "${it.name}: ${if (it.state == "stale") "updates delayed" else "unavailable"}" }?.ifEmpty { null }

/**
 * A race of the calendar (Expo's race card): when it runs and for whom, a date stamp beside the
 * name, the dates, the race's current stage on a light strip with its state, the stage winner,
 * and what opening it offers.
 */
@Composable
private fun RaceRow(race: SportsCyclingRace, today: String) {
    val navigator = LocalNavigator.current
    val stage = SportsCycling.defaultStage(race, today)
    val winner = stage?.takeIf { !it.cancelled && it.results.state == "available" }?.results?.data?.firstOrNull { it.rank == 1 && it.result == null }
    val start = race.startDate?.let { runCatching { LocalDate.parse(it) }.getOrNull() }
    // On a panel, so each one reads as a card between its neighbours.
    SportsPanel(title = null) {
    SportsReadout(onClick = { navigator.open(Page.SportsCycling(race.id)) }) {
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(SportsCycling.period(race, today).eyebrow + if (race.category == "unknown") "ROAD RACE" else race.category.uppercase(),
                 fontSize = 12.sp, color = SportsMuted, letterSpacing = 1.sp)
            if (race.captured) Text("CAPTURED RESULTS", fontSize = 12.sp, color = Accent)
        }
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            if (start != null) {
                Column(Modifier.width(56.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(start.month.name.take(3), fontSize = 11.sp, color = SportsMuted)
                    Text("${start.dayOfMonth}", fontSize = 28.sp, fontWeight = FontWeight.Bold)
                    Text("${start.year}", fontSize = 11.sp, color = SportsMuted)
                }
            }
            Column(Modifier.weight(1f)) {
                Text(race.name.uppercase(), fontSize = 22.sp, fontWeight = FontWeight.SemiBold, maxLines = 2, overflow = TextOverflow.Ellipsis)
                Text(SportsCycling.dateRange(race), fontSize = 13.sp, color = SportsMuted)
            }
        }
        if (stage != null) StageStrip(stage)
        if (winner != null) {
            Text(if (race.oneDay) "RACE WINNER" else "${stage.name.uppercase()} WINNER", fontSize = 11.sp, color = SportsMuted, letterSpacing = 1.sp)
            Text(winner.name, fontSize = 17.sp, fontWeight = FontWeight.Bold)
            listOfNotNull(winner.team, winner.time).joinToString(" · ").ifEmpty { null }?.let { Text(it, fontSize = 13.sp, color = SportsMuted) }
        }
        Text(
            if (race.scheduleOnly) "Published schedule · Find streams →"
            else "${if (race.oneDay) "One-day race" else "${race.stages.size} stages available"} · View results & route guides →",
            fontSize = 13.sp, color = Accent,
        )
    }
    }
}

/** The race's current stage on the organizer-paper strip: name, route, date · distance · terrain, its state. */
@Composable
private fun StageStrip(stage: com.strmr.tv.sports.SportsCyclingStage) {
    val ink = Color(0xFF536071)
    Row(
        Modifier.fillMaxWidth().background(Color(0xFFF5F2E9), RoundedCornerShape(6.dp)).padding(12.dp),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(stage.name, fontSize = 12.sp, fontWeight = FontWeight.Bold, color = ink)
            stage.route.ifEmpty { null }?.let { Text(it, fontSize = 17.sp, fontWeight = FontWeight.SemiBold, color = Color(0xFF172337)) }
            Text(listOfNotNull(SportsCycling.date(stage.date), stage.distance, stage.terrain).joinToString(" · "), fontSize = 12.sp, color = ink)
        }
        Text(
            when {
                stage.cancelled -> "Stage cancelled"
                stage.results.state == "available" -> "Results available"
                stage.results.state == "stale" -> "Saved results"
                stage.status == "scheduled" -> "Scheduled"
                else -> "View stage coverage"
            },
            fontSize = 12.sp, color = ink,
        )
    }
}

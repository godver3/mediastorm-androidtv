package com.strmr.tv.ui.sports.game.soccer

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.focusGroup
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.foundation.gestures.LocalBringIntoViewSpec
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.tv.material3.Text
import com.strmr.tv.sports.SportsLifecycle
import com.strmr.tv.sports.SportsPlay
import com.strmr.tv.ui.MinimalScroll
import com.strmr.tv.ui.sports.SportsAccent
import com.strmr.tv.ui.sports.SportsMuted
import com.strmr.tv.ui.sports.SportsPanel
import com.strmr.tv.ui.sports.SportsReadout
import com.strmr.tv.ui.sports.game.SportsDigits
import com.strmr.tv.ui.sports.game.SportsEventKind
import com.strmr.tv.ui.sports.game.SportsGameFormat
import com.strmr.tv.ui.sports.game.SportsGamePage
import com.strmr.tv.ui.sports.game.SportsTeamColors

/** Which soccer events the Overview's timeline shows (Expo `sportsTimelineEvents`, compact). */
object SportsSoccerTimeline {
    /** The newest play with text. */
    fun latest(page: SportsGamePage): SportsPlay? {
        if (page.lifecycle == SportsLifecycle.SCHEDULED) return null
        return page.detail.plays.firstOrNull { it.title.isNotBlank() || !it.description.isNullOrBlank() }
    }

    /** Up to eight goals other than the latest event, oldest first. */
    fun goals(page: SportsGamePage): List<SportsPlay> {
        val latest = latest(page)?.id
        val seen = mutableSetOf<String>()
        return page.detail.plays.filter { play ->
            seen.add(play.id) && !play.shootout && SportsEventKind.of(play.title) == SportsEventKind.GOAL && play.id != latest
        }.take(8).reversed()
    }
}

/**
 * The soccer Overview's events (Expo `SportsMatchTimeline`, compact; tvOS `SportsSoccerTimeline`):
 * the match's goals in order (scorer, minute, side; own goals marked), then the newest event
 * ("Latest Event", "Last Reported Event" once final, "Last Available Event" when the data is old)
 * with its mark in the side's colour, the minute, title and narrative. Full history in
 * Commentary. One stop per goal and event; Left and Right move along the goals.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun SportsSoccerTimelinePanel(page: SportsGamePage) {
    val goals = SportsSoccerTimeline.goals(page)
    val title = when {
        page.lifecycle == SportsLifecycle.FINAL -> "Last Reported Event"
        page.isStale() -> "Last Available Event"
        else -> "Latest Event"
    }
    SportsPanel(title, note = "Latest update · Full history in Commentary") {
        if (goals.isNotEmpty()) {
            CompositionLocalProvider(LocalBringIntoViewSpec provides MinimalScroll) {
                Row(Modifier.focusGroup().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(7.dp)) {
                    goals.forEach { Goal(page, it) }
                }
            }
        }
        SportsSoccerTimeline.latest(page)?.let { Event(page, it) }
    }
}

private fun color(page: SportsGamePage, play: SportsPlay): Color =
    page.team(play.teamId)?.let { SportsTeamColors.color(it, page.game) } ?: SportsMuted

@Composable
private fun Goal(page: SportsGamePage, play: SportsPlay) {
    val team = page.team(play.teamId)
    val scorer = play.participants.firstOrNull()?.name ?: play.title
    val spoken = listOfNotNull(play.clock, team?.name, play.participants.firstOrNull()?.name, play.description ?: play.title).joinToString(" · ")
    SportsReadout(Modifier.widthIn(max = 130.dp).semantics { contentDescription = spoken }) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(5.dp)) {
            SportsSoccerEventIcon(play, color(page, play), 18.dp)
            Text(play.clock ?: play.periodLabel, fontSize = 12.sp, color = SportsAccent, style = SportsDigits)
            team?.let { Text(it.shortName, fontSize = 12.sp, color = SportsMuted) }
        }
        Text(scorer, fontSize = 14.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
        if (SportsGameFormat.matches("own", play.title)) Text("Own goal", fontSize = 11.sp, color = SportsAccent)
    }
}

@Composable
private fun Event(page: SportsGamePage, play: SportsPlay) {
    val team = page.team(play.teamId)
    val time = listOfNotNull(play.clock, play.periodLabel).filter { it.isNotEmpty() }.joinToString(" · ")
    SportsReadout {
        Row(horizontalArrangement = Arrangement.spacedBy(9.dp)) {
            SportsSoccerEventIcon(play, color(page, play), 20.dp)
            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(
                    listOfNotNull(time.ifEmpty { "Time not supplied" }, team?.shortName, if (play.shootout) "Shootout" else null).joinToString(" · "),
                    fontSize = 12.sp, color = SportsAccent, style = SportsDigits,
                )
                Text(play.title.ifEmpty { "Reported event" }, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
                when {
                    play.description != null -> Text(play.description, fontSize = 12.sp, color = SportsMuted, maxLines = 3)
                    play.participants.isNotEmpty() -> Text(play.participants.joinToString(" · ") { it.name }, fontSize = 12.sp, color = SportsMuted)
                }
            }
        }
    }
}

package com.strmr.tv.ui.sports.game.hockey

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.tv.material3.Text
import com.strmr.tv.sports.SportsLifecycle
import com.strmr.tv.sports.SportsPlay
import com.strmr.tv.ui.sports.SportsMuted
import com.strmr.tv.ui.sports.SportsPanel
import com.strmr.tv.ui.sports.SportsReadout
import com.strmr.tv.ui.sports.game.SportsEventKind
import com.strmr.tv.ui.sports.game.SportsGamePage
import com.strmr.tv.ui.sports.game.SportsTeamColors

/**
 * The hockey Overview's events (Expo `SportsMatchTimeline`, compact; tvOS `SportsHockeyTimeline`):
 * a final's last three goals ("Scoring Highlights"), otherwise the newest event ("Latest Event",
 * "Last Available Event" when the data is old), each with its type's mark in the side's colour,
 * "12:05 · P3 · BOS", the title, the goal badge and the narrative. Full history in Play-by-Play.
 * One stop per event.
 */
@Composable
fun SportsHockeyTimeline(page: SportsGamePage) {
    val events = hockeyTimelineEvents(page)
    val title = when {
        page.lifecycle == SportsLifecycle.FINAL -> "Scoring Highlights"
        page.isStale() -> "Last Available Event"
        else -> "Latest Event"
    }
    SportsPanel(title, note = "Latest update · Full history in Play-by-Play") {
        events.forEachIndexed { index, play -> Event(page, play, last = index == events.lastIndex) }
    }
}

/**
 * Expo `sportsTimelineEvents`: each play once and only with text, newest first; for a final, goals
 * and penalties (or scoring plays) when there are any.
 */
fun hockeyTimelineEvents(page: SportsGamePage): List<SportsPlay> {
    if (page.lifecycle == SportsLifecycle.SCHEDULED) return emptyList()
    val final = page.lifecycle == SportsLifecycle.FINAL
    val source = if (final) page.detail.plays.filter { it.scoring || SportsEventKind.of(it.title) == SportsEventKind.GOAL } else page.detail.plays
    val seen = HashSet<String>()
    val meaningful = source.filter { play -> (play.title.isNotBlank() || !play.description.isNullOrBlank()) && seen.add(play.id) }
    val key = meaningful.filter { it.scoring || SportsEventKind.of(it.title).let { kind -> kind == SportsEventKind.GOAL || kind == SportsEventKind.PENALTY } }
    val picked = if (final && key.isNotEmpty()) key else meaningful
    return picked.take(if (final) 3 else 1)
}

@Composable
private fun Event(page: SportsGamePage, play: SportsPlay, last: Boolean) {
    val team = page.team(play.teamId)
    val color = team?.let { SportsTeamColors.color(it, page.game) } ?: SportsMuted
    val time = listOfNotNull(play.clock, play.periodLabel).filter { it.isNotEmpty() }.joinToString(" · ")
    val connector = Color.White.copy(alpha = 0.15f)
    SportsReadout {
        Row(
            // The line down to the next event, under the mark.
            Modifier.drawBehind {
                if (!last) drawLine(connector, Offset(13.dp.toPx(), 30.dp.toPx()), Offset(13.dp.toPx(), size.height + 7.dp.toPx()), 1.dp.toPx())
            },
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Box(Modifier.size(26.dp).clip(CircleShape).background(Color.White.copy(alpha = 0.08f)), contentAlignment = Alignment.Center) {
                SportsHockeyEventMark(SportsHockeyEvent.of(play), color, 14.dp)
            }
            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                val meta = listOfNotNull(time.ifEmpty { "Time not supplied" }, team?.shortName, if (play.shootout) "Shootout" else null)
                Text(meta.joinToString(" · "), fontSize = 11.sp, color = SportsMuted)
                Text(play.title.ifEmpty { "Reported event" }, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
                SportsHockeyGoalBadge.of(play)?.let { SportsHockeyGoalBadgeTag(it) }
                val description = play.description?.takeIf { it.isNotEmpty() }
                if (description != null) Text(description, fontSize = 12.sp, color = SportsMuted, maxLines = 3, overflow = TextOverflow.Ellipsis)
                else if (play.participants.isNotEmpty()) Text(play.participants.joinToString(" · ") { it.name }, fontSize = 12.sp, color = SportsMuted)
            }
        }
    }
}

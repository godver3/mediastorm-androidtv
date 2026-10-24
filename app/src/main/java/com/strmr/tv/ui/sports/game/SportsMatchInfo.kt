package com.strmr.tv.ui.sports.game

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.tv.material3.Text
import com.strmr.tv.sports.SportsLifecycle
import com.strmr.tv.sports.SportsStat
import com.strmr.tv.ui.sports.SportsMuted
import com.strmr.tv.ui.sports.SportsPanel
import com.strmr.tv.ui.sports.SportsReadout

/**
 * The game's facts (Expo `EventInfo` with `SportsTeamContext` and the coverage note): venue,
 * court, format, broadcasts, the event and competition, the reported status, college records,
 * and what the feed covers for this league. Before the start, the availability notice carries
 * the venue and channels instead.
 */
@Composable
fun SportsMatchInfo(page: SportsGamePage, title: String = "Match Information") {
    SportsPanel(title) {
        page.matchInfo.forEach { item ->
            SportsReadout {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(item.label, fontSize = 12.sp, color = SportsMuted, modifier = Modifier.width(90.dp))
                    Text(item.value, fontSize = 14.sp)
                }
            }
        }
        val context = page.teamContext
        if (context.isNotEmpty()) SportsReadout { context.forEach { Text(it, fontSize = 12.sp) } }
        page.coverageNote?.let { note -> SportsReadout { Text(note, fontSize = 12.sp, color = SportsMuted) } }
    }
}

/** The match information's facts: what the feed says that the page shows nowhere else. */
val SportsGamePage.matchInfo: List<SportsStat>
    get() {
        val inNotice = lifecycle == SportsLifecycle.SCHEDULED && availabilityApplies
        return listOf(
            "Venue" to if (inNotice) null else game.venue,
            "Court" to game.courtName,
            "Format" to game.bestOf?.takeIf { it == 3 || it == 5 }?.let { "Best of $it sets" },
            "Broadcast" to if (inNotice) null else game.broadcasts?.joinToString(" · "),
            "Event" to game.eventContext,
            "Competition" to detail.tournamentContext,
            "Status" to if (kind == SportsKind.TENNIS || lifecycle == SportsLifecycle.SCHEDULED) null else game.statusDetail,
        ).mapNotNull { (label, value) -> value?.ifEmpty { null }?.let { SportsStat(label, it) } }
    }

val SportsGamePage.hasMatchNotes: Boolean get() = teamContext.isNotEmpty() || coverageNote != null

/** College records ("MICH · 7-1 overall · 4-1 conference") and where rankings come from. */
val SportsGamePage.teamContext: List<String>
    get() {
        if (!isCollege) return emptyList()
        val lines = teams.mapNotNull { team ->
            val parts = listOfNotNull(team.record?.let { "$it overall" }, team.conferenceRecord?.let { "$it conference" })
            if (parts.isEmpty()) null else (listOf(team.shortName) + parts).joinToString(" · ")
        }
        return if (teams.any { it.displayRank != null }) lines + "Rankings supplied by ESPN for this game" else lines
    }

/**
 * What the feed hasn't sent, or that play is held up (Expo `SportsMatchAvailability`): before the
 * start, when it starts, where and on which channels; for a postponed, canceled or delayed game,
 * the reported status; for a game without data, which panels are missing. Golf, cricket and
 * boxing have their own coverage notes instead.
 */
@Composable
fun SportsAvailabilityNotice(page: SportsGamePage) {
    SportsPanel(availabilityTitle(page)) {
        SportsReadout {
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                val status = page.game.statusDetail
                if (page.lifecycle.isScheduleChange && !status.isNullOrEmpty()) Text(status, fontSize = 14.sp)
                Text(availabilityMessage(page), fontSize = 14.sp, color = SportsMuted)
                availabilityFacts(page).forEach { fact ->
                    Column(Modifier.padding(top = 3.dp)) {
                        Text(fact.label, fontSize = 11.sp, color = SportsMuted)
                        Text(fact.value, fontSize = 14.sp)
                    }
                }
            }
        }
    }
}

/** Whether [SportsAvailabilityNotice] shows. */
val SportsGamePage.availabilityApplies: Boolean
    get() = kind != SportsKind.GOLF && kind != SportsKind.CRICKET && kind != SportsKind.BOXING &&
        (lifecycle == SportsLifecycle.SCHEDULED || lifecycle.isScheduleChange || !hasGameData)

private fun availabilityTitle(page: SportsGamePage): String {
    val match = page.kind.playsMatches
    return when (page.lifecycle) {
        SportsLifecycle.SCHEDULED -> if (match) "Before the Match" else "Before the Game"
        SportsLifecycle.POSTPONED -> if (match) "Match Postponed" else "Game Postponed"
        SportsLifecycle.CANCELED -> if (match) "Match Canceled" else "Game Canceled"
        SportsLifecycle.DELAYED -> "Play Delayed"
        SportsLifecycle.FINAL -> "Final Result"
        SportsLifecycle.LIVE -> "Following the Score"
    }
}

/** The panels this sport's feed fills when it has them. */
private fun modules(kind: SportsKind) = when (kind) {
    SportsKind.BASEBALL -> "Inning scores and play history"
    SportsKind.SOCCER -> "Match events and statistics"
    SportsKind.FOOTBALL -> "Drives, plays and team statistics"
    SportsKind.HOCKEY -> "Goal and penalty events, and team statistics"
    SportsKind.TENNIS -> "Set scores and serve statistics"
    else -> "Scoring, shooting and player statistics"
}

private fun availabilityMessage(page: SportsGamePage): String = when (page.lifecycle) {
    SportsLifecycle.SCHEDULED -> "${modules(page.kind)} will appear here when supplied by the feed."
    SportsLifecycle.POSTPONED ->
        "Check the latest scheduling notice for this event. Any retained statistics describe the reported game, not live play."
    SportsLifecycle.CANCELED -> "This event is marked canceled. Any retained statistics are historical; live play is not shown."
    SportsLifecycle.DELAYED -> "Scores and statistics are retained from the last update."
    SportsLifecycle.FINAL -> "${modules(page.kind)} are not included in this result. The scoreboard above retains the supplied result."
    SportsLifecycle.LIVE ->
        "${modules(page.kind)} are not included in this update. The scoreboard above shows the latest supplied information."
}

private fun availabilityFacts(page: SportsGamePage): List<SportsStat> {
    if (page.lifecycle != SportsLifecycle.SCHEDULED) return emptyList()
    val game = page.game
    val start = if (game.start == java.time.Instant.EPOCH) "Time to be announced"
    else "${SportsGameFormat.startDate(game)} · ${SportsGameFormat.startClock(game)}"
    return listOf(SportsStat("Start", start)) + listOf("Venue" to game.venue, "Broadcasts" to game.broadcasts?.joinToString(" · "))
        .mapNotNull { (label, value) -> value?.ifEmpty { null }?.let { SportsStat(label, it) } }
}

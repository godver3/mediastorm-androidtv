package com.strmr.tv.ui.sports.game.basketball

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.tv.material3.Text
import com.strmr.tv.sports.SportsBasketballShot
import com.strmr.tv.sports.SportsLifecycle
import com.strmr.tv.sports.liveBasketballSituation
import com.strmr.tv.sports.side
import com.strmr.tv.ui.sports.SportsBall
import com.strmr.tv.ui.sports.SportsMuted
import com.strmr.tv.ui.sports.SportsPanel
import com.strmr.tv.ui.sports.SportsReadout
import com.strmr.tv.ui.sports.basketball
import com.strmr.tv.ui.sports.game.SportsGameFormat
import com.strmr.tv.ui.sports.game.SportsGamePage
import com.strmr.tv.ui.sports.game.SportsPlayerPortrait
import com.strmr.tv.ui.sports.game.SportsTeamColors
import com.strmr.tv.ui.sports.game.charts.chartPair
import kotlin.math.roundToInt

/**
 * The Overview's court (Expo `SportsBasketballLiveCard` on TV, tvOS `SportsBasketballCourtCard`):
 * the shot chart, every attempt by side (made filled, missed a ring; the away side shoots at the
 * left basket), the latest shot marked with a basketball whose ring pulses while the game is live,
 * and its card ("Latest shot" / "Last shot": the player, "+2 PTS" or "Missed shot", when) under
 * the court (Expo's corner card needs a court twice a column's width). Not before the start, nor
 * without shots, shooting totals or plays. One stop for the remote, as in Expo.
 */
@Composable
fun SportsBasketballCourtCard(page: SportsGamePage) {
    val game = page.game
    val geometry = SportsCourtGeometry.of(game.league, game.homeTeam)
    val pair = SportsTeamColors.chartPair(game.awayTeam, game.homeTeam)
    val color = { team: String -> SportsTeamColors.parse(if (team == game.awayTeam.id) pair.away else pair.home) ?: Color.White }
    val shots = page.detail.basketballShots
    val marks = shots.mapNotNull { shot -> SportsCourtGeometry.position(shot, game)?.let { SportsCourtMark(it, color(shot.teamId), shot.made) } }
    val latest = shots.lastOrNull()
    val label = label(page)
    SportsPanel("Shot Chart", note = legend(page)) {
        SportsReadout(Modifier.semantics { contentDescription = spoken(page, label) }) {
            var width by remember { mutableIntStateOf(0) }
            Box(Modifier.fillMaxWidth().onSizeChanged { width = it.width }) {
                SportsBasketballCourt(geometry, game.homeTeam, marks)
                val spot = latest?.let { SportsCourtGeometry.position(it, game) }
                if (spot != null && width > 0) {
                    val scale = width / SportsCourtGeometry.WIDTH
                    val half = with(LocalDensity.current) { 9.dp.roundToPx() }
                    Marker(page.lifecycle == SportsLifecycle.LIVE, Modifier.offset {
                        IntOffset(((spot.x - 40) * scale).roundToInt() - half, ((spot.y - 40) * scale).roundToInt() - half)
                    })
                }
            }
            if (latest != null) ShotCard(page, latest, color(latest.teamId), label)
            else Text(label, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
            game.liveBasketballSituation?.bonus?.let { Text(it, fontSize = 12.sp, color = SportsMuted) }
        }
    }
}

/** Whether the court shows: once started, with shots, plays, a situation or shooting totals. */
fun sportsCourtShows(page: SportsGamePage): Boolean {
    if (page.lifecycle == SportsLifecycle.SCHEDULED) return false
    val detail = page.detail
    return detail.basketballShots.isNotEmpty() || detail.plays.isNotEmpty() || page.game.liveBasketballSituation != null ||
        detail.comparisons.any { SportsGameFormat.matches("""^field goals$|three.point|three pointer|3pt""", it.label) }
}

private fun label(page: SportsGamePage): String {
    val live = page.lifecycle == SportsLifecycle.LIVE
    if (page.detail.basketballShots.isEmpty()) return if (live) "Live court" else "Game snapshot"
    return if (live) "Latest shot" else "Last shot"
}

private fun legend(page: SportsGamePage): String? {
    if (page.detail.basketballShots.isEmpty()) return null
    return "${page.game.awayTeam.shortName} shoots left, ${page.game.homeTeam.shortName} right · Filled: made · Ring: missed"
}

private fun spoken(page: SportsGamePage, label: String): String {
    val situation = page.game.liveBasketballSituation?.let { "${page.game.side(it.possession)?.name ?: it.possession} possession. " }.orEmpty()
    val latest = page.detail.basketballShots.lastOrNull()?.description ?: page.detail.plays.firstOrNull()?.let { it.description ?: it.title }
    return "$label. $situation${latest ?: "Team shooting totals"}"
}

/** The latest shot: Expo's dark disk with a basketball, and a white ring that pulses while live. */
@Composable
private fun Marker(live: Boolean, modifier: Modifier) {
    Box(modifier.size(18.dp), contentAlignment = Alignment.Center) {
        if (live) {
            val pulse = rememberInfiniteTransition(label = "shot pulse")
            val grow by pulse.animateFloat(1f, 2.3f, infiniteRepeatable(tween(2200, easing = LinearEasing), RepeatMode.Restart), label = "grow")
            Box(
                Modifier.size(18.dp).graphicsLayer { scaleX = grow; scaleY = grow; alpha = 0.45f * (2.3f - grow) / 1.3f }
                    .border(1.dp, Color.White, CircleShape),
            )
        }
        Box(Modifier.size(18.dp).clip(CircleShape).background(Color(0xFF05080C)).border(1.25.dp, Color.White, CircleShape))
        Canvas(Modifier.size(10.dp)) { basketball(SportsBall, Color(0xFF05080C)) }
    }
}

/** The latest shot's card: the shooter, "+2 PTS" in the side's colour or "Missed shot", when, and the play's text. */
@Composable
private fun ShotCard(page: SportsGamePage, shot: SportsBasketballShot, color: Color, label: String) {
    val shape = RoundedCornerShape(8.dp)
    Row(
        Modifier.fillMaxWidth().clip(shape).background(Color(0xFF121A21).copy(alpha = 0.94f)).border(1.dp, Color.White.copy(alpha = 0.18f), shape)
            // The side's colour down the leading edge.
            .drawBehind { drawRect(color, Offset(0f, 6.dp.toPx()), Size(2.dp.toPx(), size.height - 12.dp.toPx())) },
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Row(Modifier.padding(start = 10.dp, top = 8.dp, end = 8.dp, bottom = 8.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            if (shot.headshotUrl != null || shot.jersey != null) SportsPlayerPortrait(shot.playerName.orEmpty(), shot.headshotUrl, page.team(shot.teamId), 36.dp)
            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(label, fontSize = 10.sp, color = SportsMuted)
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text(shot.playerName ?: "Shot details", fontSize = 14.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    shot.jersey?.let { Text("#$it", fontSize = 11.sp, color = SportsMuted) }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(if (shot.made) "+${shot.points} PTS" else "Missed shot", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = color)
                    Text(listOf(shot.periodLabel, shot.clock).filter { it.isNotEmpty() }.joinToString(" · "), fontSize = 12.sp, color = SportsMuted)
                }
                if (shot.description.isNotEmpty()) {
                    Text(shot.description, fontSize = 12.sp, color = SportsMuted, maxLines = 2, overflow = TextOverflow.Ellipsis)
                }
            }
        }
    }
}

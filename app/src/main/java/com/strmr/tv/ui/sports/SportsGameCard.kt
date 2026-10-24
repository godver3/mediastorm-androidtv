package com.strmr.tv.ui.sports

import androidx.compose.foundation.background
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.text.TextAutoSize
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.foundation.layout.heightIn
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.semantics.invisibleToUser
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.withStyle
import com.strmr.tv.ui.sports.game.SportsDigits
import com.strmr.tv.ui.sports.cards.SportsCardHeading
import com.strmr.tv.ui.sports.cards.SportsCardVariant
import com.strmr.tv.ui.sports.cards.SportsCombatCard
import com.strmr.tv.ui.sports.cards.SportsFieldCard
import com.strmr.tv.ui.sports.cards.SportsTennisCard
import com.strmr.tv.ui.sports.cards.monogram
import com.strmr.tv.ui.sports.game.SportsKind
import com.strmr.tv.ui.sports.game.baseball.SportsBasesGlyph
import kotlinx.coroutines.delay
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.tv.material3.Card
import androidx.tv.material3.CardDefaults
import androidx.tv.material3.Text
import coil.compose.SubcomposeAsyncImage
import com.strmr.tv.sports.SportsGame
import com.strmr.tv.sports.SportsLifecycle
import com.strmr.tv.sports.SportsTeam
import com.strmr.tv.ui.CardFocusScale
import com.strmr.tv.ui.cardFocusBorder
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle

internal val SportsMuted = Color.White.copy(alpha = 0.6f)
internal val SportsLive = Color(0xFFFF334D)
internal val SportsAmber = Color(0xFFE6B75A)
/** A hub card's surface. */
internal val SportsCardColor = Color(0xFF26262A)

/**
 * A game on the Sports hub (tvOS `SportsGameCard`): league and status across the top, then the
 * sport's body ([SportsCardVariant]: tennis's set grid, a fight's portraits, a field event's
 * leaders) or the two sides with their logos and scores (home first for team sports, Expo `sportsOrderedTeams`;
 * or a non-matchup event's title), a live game's context line ([SportsCardFooter]), and when it
 * starts or how it stands, with "Updates delayed" when its league's scoreboard is [stale]. A score
 * that changes between refreshes lights the card for two seconds (Expo `SportsCardFrame`), focus
 * staying where it is. With scores hidden it keeps names, times and the LIVE / FINAL state only.
 */
@Composable
fun SportsGameCard(
    game: SportsGame,
    leagueName: String,
    kind: SportsKind,
    spoilers: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    stale: Boolean = false,
) {
    val flashing = rememberScoreFlash(game, spoilers)
    val flash by animateFloatAsState(if (flashing) 1f else 0f, tween(300), label = "score flash")
    Card(
        onClick = onClick,
        scale = CardDefaults.scale(focusedScale = CardFocusScale), border = cardFocusBorder(),
        // The body colour on the card too: a card stretched to its row's height stays one surface.
        colors = CardDefaults.colors(containerColor = SportsCardColor, focusedContainerColor = SportsCardColor),
        modifier = modifier.width(300.dp),
    ) {
        // As tall as the row's tallest card (tv Card's own column drops a minimum height), the status line at the foot.
        Box(Modifier.fillMaxWidth().heightIn(min = LocalSportsCardMinHeight.current), propagateMinConstraints = true) {
            // Each body emits its top and its foot, spread over the card's height.
            Column(Modifier.fillMaxSize().background(SportsCardColor).padding(14.dp), verticalArrangement = Arrangement.SpaceBetween) {
                when (SportsCardVariant.of(game, kind)) {
                    SportsCardVariant.TENNIS -> SportsTennisCard(game, leagueName, spoilers, stale)
                    SportsCardVariant.COMBAT -> SportsCombatCard(game, leagueName, spoilers, stale)
                    SportsCardVariant.FIELD -> SportsFieldCard(game, leagueName, kind, spoilers, stale)
                    SportsCardVariant.MATCHUP -> Matchup(game, leagueName, kind, spoilers, stale)
                }
            }
            if (flash > 0f) ScoreFlash(Modifier.matchParentSize().alpha(flash))
        }
    }
}

/** A team game's body: the sides with logos and scores (cricket's score text under each name), the context line and the status. */
@Composable
private fun Matchup(game: SportsGame, leagueName: String, kind: SportsKind, spoilers: Boolean, stale: Boolean) {
    val showScore = !spoilers && game.showsScore
    val cricket = kind == SportsKind.CRICKET
    Column(Modifier.padding(bottom = 8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        SportsCardHeading(leagueName, game.lifecycle)
        if (game.isMatchup) {
            val sides = if (kind.awayFirst) listOf(game.awayTeam, game.homeTeam) else listOf(game.homeTeam, game.awayTeam)
            sides.forEach { team ->
                val leads = !spoilers && game.lifecycle == SportsLifecycle.FINAL && team.winner == true
                val score = if (showScore) team.score else null
                // A record (from the scoreboard or the league's table) while there is no score to show.
                SportsTeamLine(
                    team, if (cricket) null else score, leads, record = team.record?.takeIf { !spoilers && !showScore },
                    detail = if (cricket) score else null,
                )
            }
        } else {
            Text(game.displayTitle, fontSize = 15.sp, fontWeight = FontWeight.SemiBold, maxLines = 2, overflow = TextOverflow.Ellipsis)
            game.combat?.division?.ifEmpty { null }?.let { Text(it, fontSize = 12.sp, color = SportsMuted) }
        }
    }
    Column(verticalArrangement = Arrangement.spacedBy(5.dp)) {
        // Cricket's reported state ("RCB need 36 runs in 30 balls") is already the status line.
        SportsCardFooter.of(game, kind, spoilers)?.takeIf { it.text != game.statusDetail }?.let { SportsCardFooterLine(it) }
        StatusLine(game, spoilers, stale)
    }
}

/** The height of a row's tallest card, which every card in the row takes (C's even rows). */
internal val LocalSportsCardMinHeight = compositionLocalOf { 0.dp }

/** Whether the card is lit: for two seconds after its live score changes between refreshes. */
@Composable
private fun rememberScoreFlash(game: SportsGame, spoilers: Boolean): Boolean {
    val key = SportsScoreChange.key(game, spoilers)
    var previous by remember(game.id) { mutableStateOf(key) }
    var flashes by remember(game.id) { mutableIntStateOf(0) }
    var flashing by remember(game.id) { mutableStateOf(false) }
    LaunchedEffect(key) {
        if (SportsScoreChange.changed(previous, key)) flashes++
        previous = key
    }
    LaunchedEffect(flashes) {
        if (flashes == 0) return@LaunchedEffect
        flashing = true
        delay(SportsScoreChange.DURATION_MS)
        flashing = false
    }
    return flashing
}

/** The score-change cue: an accent outline inside the card and a bar along its foot. */
@Composable
private fun ScoreFlash(modifier: Modifier) {
    Box(modifier.border(3.dp, SportsAccent, RoundedCornerShape(8.dp)).semantics { invisibleToUser() }) {
        Box(
            Modifier.align(Alignment.BottomCenter).padding(start = 12.dp, end = 12.dp, bottom = 6.dp).fillMaxWidth().height(3.dp)
                .clip(CircleShape).background(SportsAccent),
        )
    }
}

/** The state line; a scheduled game's countdown is redrawn every 30 s. Amber "Updates delayed" when the league is stale. */
@Composable
private fun StatusLine(game: SportsGame, spoilers: Boolean, stale: Boolean) {
    val scheduled = game.lifecycle == SportsLifecycle.SCHEDULED
    val now by produceState(Instant.now(), scheduled) {
        while (scheduled) {
            delay(30_000)
            value = Instant.now()
        }
    }
    val text = buildAnnotatedString {
        append(SportsFormat.statusLine(game, spoilers, now))
        if (stale) {
            append(" · ")
            withStyle(SpanStyle(color = SportsAmber)) { append("Updates delayed") }
        }
    }
    BasicText(
        text, maxLines = 1, overflow = TextOverflow.Ellipsis,
        style = TextStyle(color = SportsMuted, fontSize = 12.sp),
        autoSize = TextAutoSize.StepBased(minFontSize = 10.sp, maxFontSize = 12.sp),
    )
}

/** A live card's context: MLB's bases glyph or the sport's mark, then the situation. */
@Composable
fun SportsCardFooterLine(footer: SportsCardFooter) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(7.dp)) {
        when {
            footer.bases != null -> SportsBasesGlyph(footer.bases, 9.dp)
            footer.glyph != null -> SportsGlyphMark(footer.glyph)
        }
        if (footer.text.isNotEmpty()) {
            Text(footer.text, fontSize = 12.sp, fontWeight = FontWeight.Medium, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
    }
}

/** One side of a matchup: logo, name (with a [detail] line under it, cricket's score) and score (or the side's [record], muted). */
@Composable
fun SportsTeamLine(
    team: SportsTeam, score: String?, leads: Boolean, logo: Dp = 28.dp, nameSize: TextUnit = 14.sp, scoreSize: TextUnit = 18.sp,
    record: String? = null, detail: String? = null,
) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        SportsTeamLogo(team, logo)
        Column(Modifier.weight(1f)) {
            Text(
                team.name, fontSize = nameSize, fontWeight = if (leads) FontWeight.Bold else FontWeight.Medium,
                maxLines = 1, overflow = TextOverflow.Ellipsis,
            )
            if (!detail.isNullOrEmpty()) {
                Text(
                    detail, fontSize = 12.sp, color = if (leads) Color.White else SportsMuted, style = SportsDigits,
                    maxLines = 1, overflow = TextOverflow.Ellipsis,
                )
            }
        }
        if (score == null && record != null) Text(record, fontSize = 12.sp, color = SportsMuted, maxLines = 1)
        if (score != null) {
            Text(score, fontSize = scoreSize, fontWeight = if (leads) FontWeight.Bold else FontWeight.SemiBold,
                 color = if (leads) Color.White else SportsMuted)
        }
    }
}

@Composable
fun SportsTeamLogo(team: SportsTeam, size: Dp) {
    val fallback = @Composable {
        Box(Modifier.size(size).clip(CircleShape).background(teamColor(team.color) ?: Color.White.copy(alpha = 0.15f)),
            contentAlignment = Alignment.Center) {
            Text(team.monogram, fontSize = (size.value * 0.3f).sp, fontWeight = FontWeight.Bold)
        }
    }
    val logo = team.logo
    if (logo == null) fallback()
    else SubcomposeAsyncImage(model = logo, contentDescription = team.name, contentScale = ContentScale.Fit,
                              error = { fallback() }, modifier = Modifier.size(size))
}

@Composable
fun SportsStatusPill(lifecycle: SportsLifecycle) =
    SportsStatusPill(lifecycle.pill, live = lifecycle == SportsLifecycle.LIVE, amber = lifecycle.isScheduleChange)

/** A status pill with its own text ("REST DAY", "SCHEDULE"): red dot when [live], amber text when [amber]. */
@Composable
fun SportsStatusPill(text: String, live: Boolean = false, amber: Boolean = false) {
    Row(
        Modifier.clip(RoundedCornerShape(50)).background(if (live) SportsLive.copy(alpha = 0.22f) else Color.White.copy(alpha = 0.12f))
            .padding(horizontal = 8.dp, vertical = 2.dp),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        if (live) Box(Modifier.size(6.dp).clip(CircleShape).background(SportsLive))
        Text(text, fontSize = 10.sp, fontWeight = FontWeight.Bold, color = if (amber) SportsAmber else Color.White)
    }
}

private fun teamColor(hex: String?): Color? {
    val s = hex?.removePrefix("#")?.takeIf { it.length == 6 } ?: return null
    return s.toLongOrNull(16)?.let { Color(0xFF000000 or it) }
}

object SportsFormat {
    private val time = DateTimeFormatter.ofLocalizedTime(FormatStyle.SHORT)
    private val day = DateTimeFormatter.ofPattern("EEE, MMM d")
    private val fullDay = DateTimeFormatter.ofLocalizedDate(FormatStyle.FULL)

    /** "7:05 PM · Starts in 2h 5m" (Expo's countdown), the live clock, or the final line. */
    fun statusLine(game: SportsGame, spoilers: Boolean, now: Instant = Instant.now()): String = when (game.lifecycle) {
        SportsLifecycle.SCHEDULED -> {
            val countdown = countdown(game.start, now)
            if (game.start == Instant.EPOCH) countdown else "${time.format(game.start.atZone(ZoneId.systemDefault()))} · $countdown"
        }
        SportsLifecycle.LIVE -> if (spoilers) "In progress · scores hidden"
            else game.statusDetail ?: listOfNotNull(game.period, game.clock).joinToString(" · ")
        SportsLifecycle.FINAL -> if (spoilers) "Result hidden" else game.statusDetail ?: "Final"
        else -> game.statusDetail ?: startTime(game)
    }

    fun startTime(game: SportsGame): String {
        val local = game.start.atZone(ZoneId.systemDefault())
        val today = java.time.LocalDate.now()
        return if (local.toLocalDate() == today) time.format(local) else "${day.format(local)} · ${time.format(local)}"
    }

    fun date(game: SportsGame): String = fullDay.format(game.start.atZone(ZoneId.systemDefault()))
}

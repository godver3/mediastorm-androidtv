package com.strmr.tv.ui.sports.game.soccer

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Warning
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.invisibleToUser
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.tv.material3.Icon
import com.strmr.tv.sports.SportsPlay
import com.strmr.tv.ui.AppIcons
import com.strmr.tv.ui.sports.SportsGlyph
import com.strmr.tv.ui.sports.SportsGlyphMark
import com.strmr.tv.ui.sports.game.SportsEventKind
import com.strmr.tv.ui.sports.game.SportsGameFormat

private val Yellow = Color(0xFFFBBF24)
private val Red = Color(0xFFEF4444)

/**
 * A soccer event's mark from its title (Expo `EventIcon` over `sportsTimelineKind`, tvOS
 * `SportsSoccerEventIcon`): a ball for goals, the card itself (both for a second yellow), a swap
 * for substitutions, a warning for penalties and fouls, a flag for corners and offsides, a pause
 * for the whistle; a dot otherwise. Drawn or Material marks on a faint circle, in the side's [color].
 */
@Composable
fun SportsSoccerEventIcon(play: SportsPlay, color: Color, size: Dp = 20.dp) {
    Box(
        Modifier.size(size).clip(CircleShape).background(Color.White.copy(alpha = 0.08f)).semantics { invisibleToUser() },
        contentAlignment = Alignment.Center,
    ) {
        val mark = size * 0.52f
        when (SportsEventKind.of(play.title)) {
            SportsEventKind.YELLOW_CARD -> Card(Yellow, size)
            SportsEventKind.RED_CARD -> if (SportsGameFormat.matches("""^(second yellow( card)?|yellow red card)$""", play.title.trim())) {
                Box(Modifier.offset(-size * 0.1f, -size * 0.06f)) { Card(Yellow, size) }
                Box(Modifier.offset(size * 0.1f, size * 0.06f)) { Card(Red, size) }
            } else {
                Card(Red, size)
            }
            SportsEventKind.GOAL -> SportsGlyphMark(SportsGlyph.SOCCER, mark, color)
            SportsEventKind.SUBSTITUTION -> Canvas(Modifier.size(mark)) { swap(color) }
            SportsEventKind.PENALTY -> Symbol(Icons.Filled.Info, Yellow, mark)
            SportsEventKind.OTHER -> Other(play.title.lowercase(), color, size)
        }
    }
}

@Composable
private fun Other(title: String, color: Color, size: Dp) {
    val mark = size * 0.52f
    when {
        SportsGameFormat.matches("^(corner|corner kick|offside|offsides)$", title) -> Canvas(Modifier.size(mark)) { flag(color) }
        SportsGameFormat.matches("^(foul|foul committed|foul conceded)$", title) -> Symbol(Icons.Filled.Warning, color, mark)
        SportsGameFormat.matches("^(kick ?off|half ?time|full time|end of match)$", title) -> Symbol(AppIcons.Pause, color, mark)
        else -> Box(Modifier.size(size * 0.25f).clip(CircleShape).background(color))
    }
}

@Composable
private fun Symbol(icon: ImageVector, tint: Color, size: Dp) = Icon(icon, contentDescription = null, tint = tint, modifier = Modifier.size(size))

@Composable
private fun Card(fill: Color, size: Dp) {
    Box(Modifier.size(size * 0.38f, size * 0.56f).clip(RoundedCornerShape(size * 0.06f)).background(fill))
}

/** Two arrows passing each other (a substitution). */
private fun DrawScope.swap(color: Color) {
    val w = size.width
    val h = size.height
    val stroke = w * 0.12f
    fun arrow(y: Float, from: Float, to: Float) {
        drawLine(color, Offset(from, y), Offset(to, y), stroke, StrokeCap.Round)
        val back = if (to > from) -w * 0.22f else w * 0.22f
        drawLine(color, Offset(to, y), Offset(to + back, y - h * 0.18f), stroke, StrokeCap.Round)
        drawLine(color, Offset(to, y), Offset(to + back, y + h * 0.18f), stroke, StrokeCap.Round)
    }
    arrow(h * 0.3f, w * 0.1f, w * 0.9f)
    arrow(h * 0.7f, w * 0.9f, w * 0.1f)
}

/** A corner flag. */
private fun DrawScope.flag(color: Color) {
    val w = size.width
    val h = size.height
    drawLine(color, Offset(w * 0.25f, h * 0.08f), Offset(w * 0.25f, h * 0.95f), w * 0.1f, StrokeCap.Round)
    drawPath(Path().apply { moveTo(w * 0.25f, h * 0.08f); lineTo(w * 0.85f, h * 0.28f); lineTo(w * 0.25f, h * 0.5f); close() }, color)
}

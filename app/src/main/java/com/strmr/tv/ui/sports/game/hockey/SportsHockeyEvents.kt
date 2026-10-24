package com.strmr.tv.ui.sports.game.hockey

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowForward
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Warning
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.tv.material3.Icon
import androidx.tv.material3.Text
import com.strmr.tv.sports.SportsPlay
import com.strmr.tv.ui.AppIcons
import com.strmr.tv.ui.sports.SportsAccent
import com.strmr.tv.ui.sports.game.SportsEventKind

/**
 * A hockey play's type from the feed's title (Expo `hockeyEventKind`, tvOS `SportsHockeyEvent`),
 * never its narrative, and the mark the timeline gives it ([SportsHockeyEventMark]).
 */
enum class SportsHockeyEvent {
    GOAL, PENALTY, SHOT, SAVE, BLOCKED, MISSED, TURNOVER, FACEOFF, HIT, WHISTLE, OTHER;

    companion object {
        private val kinds = mapOf(
            "shot" to SHOT, "shot on goal" to SHOT, "shot on target" to SHOT, "save" to SAVE, "saved shot" to SAVE,
            "blocked shot" to BLOCKED, "blocked" to BLOCKED, "missed shot" to MISSED, "missed" to MISSED,
            "giveaway" to TURNOVER, "takeaway" to TURNOVER, "faceoff" to FACEOFF, "face off" to FACEOFF, "hit" to HIT, "hits" to HIT,
            "stoppage" to WHISTLE, "whistle" to WHISTLE, "icing" to WHISTLE, "end of period" to WHISTLE, "period end" to WHISTLE,
        )

        fun of(play: SportsPlay): SportsHockeyEvent {
            kinds[normalized(play.title)]?.let { return it }
            if (play.scoring) return GOAL
            return when (SportsEventKind.of(play.title)) {
                SportsEventKind.GOAL -> GOAL
                SportsEventKind.PENALTY -> PENALTY
                else -> OTHER
            }
        }

        internal fun normalized(title: String) = title.lowercase().trim().replace(Regex("[_-]+"), " ").replace(Regex("""\s+"""), " ")
    }
}

/**
 * A goal's special-teams tag from its title (Expo `hockeyGoalBadge` / `HockeyGoalBadge`): "PP ·
 * Power-play goal", "SH · Shorthanded goal", "EN · Empty-net goal"; none for shootout kicks.
 */
data class SportsHockeyGoalBadge(val short: String, val label: String) {
    val text: String get() = "$short · $label"

    companion object {
        fun of(play: SportsPlay): SportsHockeyGoalBadge? {
            if (play.shootout) return null
            return when (SportsHockeyEvent.normalized(play.title)) {
                "power play goal" -> SportsHockeyGoalBadge("PP", "Power-play goal")
                "short handed goal", "shorthanded goal" -> SportsHockeyGoalBadge("SH", "Shorthanded goal")
                "empty net goal" -> SportsHockeyGoalBadge("EN", "Empty-net goal")
                else -> null
            }
        }
    }
}

/** The badge as a small accent tag (Play-by-Play rows and the timeline). */
@Composable
fun SportsHockeyGoalBadgeTag(badge: SportsHockeyGoalBadge) {
    Text(
        badge.text, fontSize = 10.sp, fontWeight = FontWeight.Bold, color = SportsAccent,
        modifier = Modifier.semantics { contentDescription = badge.label },
    )
}

/**
 * An event's mark (tvOS SF Symbols): a puck for goals, a warning for penalties, a target for
 * shots, a check for saves, a cross for blocks, an arrow off target for misses, arrows for
 * turnovers, rings for faceoffs, a star for hits, a pause for whistles, else a dot.
 */
@Composable
fun SportsHockeyEventMark(event: SportsHockeyEvent, color: Color, size: Dp = 16.dp) {
    val icon = when (event) {
        SportsHockeyEvent.PENALTY -> Icons.Filled.Warning
        SportsHockeyEvent.SAVE -> Icons.Filled.Check
        SportsHockeyEvent.BLOCKED -> Icons.Filled.Close
        SportsHockeyEvent.MISSED -> Icons.Filled.ArrowForward
        SportsHockeyEvent.TURNOVER -> Icons.Filled.Refresh
        SportsHockeyEvent.WHISTLE -> AppIcons.Pause
        else -> null
    }
    if (icon != null) {
        val tilt = if (event == SportsHockeyEvent.MISSED) Modifier.rotate(-45f) else Modifier
        Icon(icon, contentDescription = null, tint = color, modifier = Modifier.size(size).then(tilt))
        return
    }
    Canvas(Modifier.size(size)) {
        val r = this.size.minDimension / 2
        when (event) {
            SportsHockeyEvent.GOAL -> {
                drawOval(color, Offset(0f, r * 0.55f), Size(r * 2, r * 0.9f))
                drawRect(color, Offset(0f, r * 0.75f), Size(r * 2, r * 0.5f))
                drawOval(color, Offset(0f, r * 0.95f), Size(r * 2, r * 0.9f))
            }
            SportsHockeyEvent.SHOT -> {
                drawCircle(color, r * 0.8f, style = Stroke(r * 0.18f))
                drawCircle(color, r * 0.22f)
                drawLine(color, Offset(r, 0f), Offset(r, r * 0.45f), r * 0.16f)
                drawLine(color, Offset(r, r * 1.55f), Offset(r, r * 2), r * 0.16f)
            }
            SportsHockeyEvent.FACEOFF -> {
                drawCircle(color, r * 0.85f, style = Stroke(r * 0.18f))
                drawCircle(color, r * 0.35f, style = Stroke(r * 0.18f))
            }
            SportsHockeyEvent.HIT -> for (step in 0 until 8) {
                val angle = Math.toRadians(step * 45.0)
                val from = Offset(r + (r * 0.3f * kotlin.math.cos(angle)).toFloat(), r + (r * 0.3f * kotlin.math.sin(angle)).toFloat())
                val to = Offset(r + (r * kotlin.math.cos(angle)).toFloat(), r + (r * kotlin.math.sin(angle)).toFloat())
                drawLine(color, from, to, r * 0.2f)
            }
            else -> drawCircle(color, r * 0.45f)
        }
    }
}

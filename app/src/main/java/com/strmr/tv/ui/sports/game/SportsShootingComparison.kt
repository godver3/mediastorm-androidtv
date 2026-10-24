package com.strmr.tv.ui.sports.game

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.tv.material3.Text
import com.strmr.tv.sports.SportsComparison
import com.strmr.tv.sports.SportsLifecycle
import com.strmr.tv.ui.sports.SportsAccent
import com.strmr.tv.ui.sports.SportsMuted
import com.strmr.tv.ui.sports.SportsPanel
import com.strmr.tv.ui.sports.SportsReadout
import kotlin.math.roundToInt

/**
 * The Box Score's shooting panel for hockey and basketball (Expo `ShootingComparison`, tvOS
 * `SportsShootingComparison`): "Shots & Special Teams" (shots on goal, power-play goals) or
 * "Shooting Splits" (field goals, three-pointers, free throws) from the game totals, over a rink or
 * court sketch with the first figure of each side, home on the left; basketball rows add each
 * side's made share as a bar. One stop.
 */
@Composable
fun SportsShootingComparison(page: SportsGamePage) {
    val rows = SportsShooting.rows(page)
    val game = page.game
    val hockey = game.league == "nhl"
    val spoken = (listOf("Shooting comparison") + rows.map { "${it.label}: ${game.homeTeam.shortName} ${it.home}, ${game.awayTeam.shortName} ${it.away}" })
        .joinToString(". ")
    SportsPanel(if (hockey) "Shots & Special Teams" else "Shooting Splits") {
        SportsReadout(Modifier.semantics { contentDescription = spoken }) {
            Row {
                Text(game.homeTeam.shortName, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
                Spacer(Modifier.weight(1f))
                Text(game.awayTeam.shortName, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
            }
            rows.firstOrNull()?.let { first ->
                Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) { Sketch(first, hockey) }
                Text("Game totals · shot locations not supplied.", fontSize = 12.sp, color = SportsMuted)
            }
            rows.forEach { row ->
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(row.home, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
                        Text(row.label, fontSize = 11.sp, color = SportsMuted, textAlign = TextAlign.Center, modifier = Modifier.weight(1f))
                        Text(row.away, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
                    }
                    if (!hockey) Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        Share(row.home, SportsAccent, Modifier.weight(1f))
                        Share(row.away, ShootingAmber, Modifier.weight(1f))
                    }
                }
            }
        }
    }
}

/** The shooting panel's rows and figures. */
object SportsShooting {
    /** The rows the panel shows, in Expo's order; empty for other sports or before the start. */
    fun rows(page: SportsGamePage): List<SportsComparison> {
        if (page.lifecycle == SportsLifecycle.SCHEDULED) return emptyList()
        val fields = when {
            page.game.league == "nhl" -> listOf("shots on goal", "power-play goals")
            page.kind == SportsKind.BASKETBALL -> listOf("field goals", "three-pointers", "free throws")
            else -> emptyList()
        }
        return fields.flatMap { field -> page.detail.comparisons.filter { it.label.lowercase() == field } }
    }

    /** A made–attempted figure's share ("39-87" → 0.45); null otherwise. */
    fun share(value: String): Double? {
        val match = Regex("""(\d+)-(\d+)""").matchEntire(value.trim()) ?: return null
        val made = match.groupValues[1].toDouble()
        val attempts = match.groupValues[2].toDouble()
        return if (attempts > 0 && made <= attempts) made / attempts else null
    }
}

private val ShootingAmber = Color(0xFFFBBF24)

@Composable
private fun Share(value: String, color: Color, modifier: Modifier) {
    val share = SportsShooting.share(value)
    Column(modifier, verticalArrangement = Arrangement.spacedBy(2.dp)) {
        if (share != null) {
            Box(Modifier.fillMaxWidth().height(4.dp).clip(CircleShape).background(Color.White.copy(alpha = 0.12f))) {
                Box(Modifier.fillMaxWidth(share.toFloat()).fillMaxHeight().clip(CircleShape).background(color))
            }
            Text("${(share * 100).roundToInt()}%", fontSize = 11.sp, color = SportsMuted)
        }
    }
}

/** Expo's 600 × 170 sketch: a rink or a court, the first row's figures over each half. */
@Composable
private fun Sketch(row: SportsComparison, hockey: Boolean) {
    val measurer = rememberTextMeasurer()
    Canvas(Modifier.widthIn(max = 360.dp).fillMaxWidth().aspectRatio(600f / 170f)) {
        val s = size.width / 600f
        fun at(x: Float, y: Float) = Offset(x * s, y * s)
        val line = Color(0xFF4A6370)
        val red = Color(0xFFA64752)
        drawRoundRect(Color(0xFF10222D), at(10f, 10f), Size(580 * s, 150 * s), CornerRadius((if (hockey) 65 else 4) * s))
        drawRoundRect(line, at(10f, 10f), Size(580 * s, 150 * s), CornerRadius((if (hockey) 65 else 4) * s), style = Stroke(2 * s))
        drawLine(if (hockey) red else line, at(300f, 10f), at(300f, 160f), 2 * s)
        drawCircle(line, 24 * s, at(300f, 85f), style = Stroke(2 * s))
        if (hockey) {
            for (x in listOf(195f, 405f)) drawLine(Color(0xFF3878AD), at(x, 10f), at(x, 160f), 3 * s)
            for (x in listOf(25f, 557f)) drawRect(red, at(x, 65f), Size(18 * s, 40 * s), style = Stroke(s))
        } else {
            for (x in listOf(10f, 505f)) drawRect(line, at(x, 50f), Size(85 * s, 70 * s), style = Stroke(s))
            drawArc(line, -76f, 152f, false, at(30f - 72, 13f), Size(144 * s, 144 * s), style = Stroke(s))
            drawArc(line, 104f, 152f, false, at(570f - 72, 13f), Size(144 * s, 144 * s), style = Stroke(s))
            for (x in listOf(35f, 565f)) drawCircle(Color(0xFFFF9500), 7 * s, at(x, 85f), style = Stroke(1.5f * s))
        }
        val caption = if (hockey) "SHOTS ON GOAL" else "FIELD GOALS"
        for ((x, value, color) in listOf(Triple(210f, row.home, SportsAccent), Triple(390f, row.away, ShootingAmber))) {
            centred(measurer, value, TextStyle(color = color, fontSize = (30 * s).toSp(), fontWeight = FontWeight.Bold), at(x, 70f))
            centred(measurer, caption, TextStyle(color = Color(0xFFB0BACC), fontSize = (13 * s).toSp()), at(x, 104f))
        }
    }
}

private fun DrawScope.centred(measurer: TextMeasurer, text: String, style: TextStyle, centre: Offset) {
    val layout = measurer.measure(text, style)
    drawText(layout, topLeft = Offset(centre.x - layout.size.width / 2f, centre.y - layout.size.height / 2f))
}

package com.strmr.tv.ui.sports.game

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.tv.material3.Text
import com.strmr.tv.sports.SportsComparison
import com.strmr.tv.ui.sports.SportsMuted
import com.strmr.tv.ui.sports.SportsPanel
import com.strmr.tv.ui.sports.SportsReadout
import com.strmr.tv.ui.sports.SportsTeamLogo
import kotlin.math.max
import kotlin.math.roundToInt

/**
 * Team statistics side by side (Expo `StatsPanel` and `SportsPriorityStats`): each side's figure
 * around the statistic's name, with bars in the team colours growing out from the centre when
 * both are plain numbers, each scaled to the larger. The Overview shows the sport's six key rows
 * ([SportsTeamStats.priorityRows]); the Box Score tab shows them all. American football splits one
 * bar by each side's share ([SportsComparisonStyle.SHARE], Expo `FootballComparison`). [note] goes under the
 * title (hockey's reported strength while live).
 */
@Composable
fun SportsTeamComparison(
    page: SportsGamePage,
    rows: List<SportsComparison>,
    title: String,
    style: SportsComparisonStyle = SportsComparisonStyle.CENTRE,
    note: String? = null,
) {
    val teams = page.teams
    val colors = teams.map { SportsTeamColors.color(it, page.game) }
    SportsPanel(title, note = note) {
        Row(Modifier.padding(horizontal = 10.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            SportsTeamLogo(teams[0], 20.dp)
            Text(teams[0].shortName, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
            Spacer(Modifier.weight(1f))
            Text(teams[1].shortName, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
            SportsTeamLogo(teams[1], 20.dp)
        }
        rows.forEach { row ->
            val values = teams.map { SportsTeamStats.display(page, page.value(row, it), row.label) }
            SportsReadout {
                // Expo's 1 : 2 : 1 row.
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(values[0], fontSize = 14.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
                    Text(row.label, fontSize = 11.sp, color = SportsMuted, textAlign = TextAlign.Center, modifier = Modifier.weight(2f))
                    Text(values[1], fontSize = 14.sp, fontWeight = FontWeight.SemiBold, textAlign = TextAlign.End, modifier = Modifier.weight(1f))
                }
                SportsTeamStats.numbers(values[0], values[1])?.let {
                    if (style == SportsComparisonStyle.SHARE) Share(it, colors) else Bars(it, colors)
                }
            }
        }
    }
}

@Composable
private fun Bars(numbers: Pair<Double, Double>, colors: List<Color>) {
    val top = max(numbers.first, numbers.second)
    Row(Modifier.fillMaxWidth().height(4.dp), horizontalArrangement = Arrangement.spacedBy(7.dp)) {
        Bar(if (top > 0) numbers.first / top else 0.0, colors[0], Alignment.CenterEnd, Modifier.weight(1f))
        Bar(if (top > 0) numbers.second / top else 0.0, colors[1], Alignment.CenterStart, Modifier.weight(1f))
    }
}

/** One track split by each side's share of the total. */
@Composable
private fun Share(numbers: Pair<Double, Double>, colors: List<Color>) {
    val total = numbers.first + numbers.second
    Row(Modifier.fillMaxWidth().height(4.dp).clip(CircleShape).background(Color.White.copy(alpha = 0.1f))) {
        if (total > 0) {
            if (numbers.first > 0) Box(Modifier.weight((numbers.first / total).toFloat()).fillMaxHeight().background(colors[0]))
            if (numbers.second > 0) Box(Modifier.weight((numbers.second / total).toFloat()).fillMaxHeight().background(colors[1]))
        }
    }
}

/** How [SportsTeamComparison] draws a row's figures. */
enum class SportsComparisonStyle {
    /** Two bars growing out from the centre, each scaled to the larger figure. */
    CENTRE,

    /** One bar split by each side's share of the total. */
    SHARE,
}

@Composable
private fun Bar(share: Double, color: Color, alignment: Alignment, modifier: Modifier) {
    Box(modifier.fillMaxHeight().clip(CircleShape).background(Color.White.copy(alpha = 0.1f))) {
        Box(Modifier.fillMaxWidth(share.toFloat()).fillMaxHeight().align(alignment).clip(CircleShape).background(color))
    }
}

/** The team comparison's rules (Expo `overviewComparisons`, `footballNumeric`). */
object SportsTeamStats {
    /** Both figures as numbers ("12", "45.5", "38%"), else null: records, times and ratios get no bars. */
    fun numbers(left: String, right: String): Pair<Double, Double>? {
        fun number(text: String): Double? {
            val trimmed = text.trim()
            if (!SportsGameFormat.matches("""^\d+(?:\.\d+)?%?$""", trimmed)) return null
            return trimmed.replace("%", "").toDoubleOrNull()
        }
        val a = number(left) ?: return null
        val b = number(right) ?: return null
        return a to b
    }

    /** Rugby possession and territory, and soccer accuracy, come as fractions: shown as percent. */
    fun display(page: SportsGamePage, value: String, label: String): String {
        val fraction = (page.kind.isRugby && SportsGameFormat.matches("possession|territory", label)) ||
            (page.kind == SportsKind.SOCCER && SportsGameFormat.matches("accuracy %", label))
        val number = value.trim().toDoubleOrNull()
        if (!fraction || number == null || number !in 0.0..1.0) return value
        return "${(number * 100).roundToInt()}%"
    }

    fun title(kind: SportsKind): String = when (kind) {
        SportsKind.SOCCER, SportsKind.HOCKEY -> "Match Stats"
        SportsKind.TENNIS -> "Serve & Return"
        SportsKind.RUGBY_UNION, SportsKind.RUGBY_LEAGUE -> "Match Comparison"
        else -> "Team Comparison"
    }

    /** The sport's six key statistics first, in the feed's order otherwise. */
    fun priorityRows(page: SportsGamePage): List<SportsComparison> {
        val order = priorities[page.kind].orEmpty()
        fun rank(label: String) = order.indexOfFirst { SportsGameFormat.matches(it, label) }.takeIf { it >= 0 } ?: order.size
        return page.detail.comparisons.sortedBy { rank(it.label) }.take(6) // stable
    }

    private val priorities = mapOf(
        SportsKind.BASEBALL to listOf("^hits$", "errors", "home runs", "strikeouts", "walks", "left on base"),
        SportsKind.SOCCER to listOf("possession", "shots on target|shots on goal", "^shots$", "corners", "red cards", "yellow cards"),
        SportsKind.HOCKEY to listOf("shots on goal", "power.play goals", "power.play chances", "saves", "faceoff", "hits"),
        SportsKind.TENNIS to listOf("aces", "double faults", "first serve", "break point", "winner", "unforced"),
        SportsKind.MMA to listOf("knockdown", "significant strikes landed", "takedowns landed", "control time", "total strikes landed"),
        SportsKind.RUGBY_UNION to listOf("possession", "territory", "^tackles$", "met(?:er|re)s", "tries", "conversions"),
        SportsKind.RUGBY_LEAGUE to listOf("possession", "met(?:er|re)s", "^tackles$", "missed tackles", "clean breaks", "tries"),
    )
}

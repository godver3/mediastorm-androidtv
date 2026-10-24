package com.strmr.tv.ui.sports.game.football

import com.strmr.tv.sports.SportsFootballPlay
import com.strmr.tv.sports.SportsGame
import com.strmr.tv.sports.SportsTeam
import com.strmr.tv.ui.sports.game.SportsGameFormat
import kotlin.math.abs

/**
 * How one play of a drive reads (Expo `classifyFootballPlay`, `footballReplaySituation`,
 * `footballPlayResult`, `footballPlayType` and `footballPlayFieldLabel`): its kind from the feed's
 * type and type id, never guessed from the narrative; the down and distance it was run on; a short
 * result; and its net movement on the field, measured from the offense's own goal (it attacks
 * right), not a player's credited yards.
 */
data class SportsFootballPlayInfo(
    val kind: Kind,
    /** "3rd & 4", "1st & Goal"; null for kickoffs, extra points and the like. */
    val down: String?,
    /** Where the play started ("NE 27"). */
    val spot: String?,
    /** "7-yard completion · First down", "Incomplete pass", or the feed's type. */
    val result: String,
    /** "Pass", "Run", "Sack", "Field goal", "Penalty" or the feed's type. */
    val type: String,
    /** Start and end from the offense's own goal. */
    val start: Double?,
    val end: Double?,
) {
    enum class Kind { PENALTY, TURNOVER, SACK, INCOMPLETE, PASS, RUSH, KICK, OTHER }

    /** Net movement toward the opponent's goal: "+7 YD", "−3 YD", "0 YD", "—" when unplaced. */
    val movement: String
        get() {
            val start = start ?: return "—"
            val end = end ?: return "—"
            val yards = SportsFootballYards.rounded(end - start).toInt()
            return "${if (yards > 0) "+" else if (yards < 0) "−" else ""}${abs(yards)} YD"
        }

    companion object {
        fun of(play: SportsFootballPlay, offense: SportsTeam, game: SportsGame): SportsFootballPlayInfo {
            val kind = kind(play)
            val side = listOf(game.awayTeam, game.homeTeam).firstOrNull { it.id == play.possessionTeamId } ?: offense
            val direction = if (side.id == game.awayTeam.id) 1 else -1
            val yard = play.start?.takeIf { SportsFootballYards.isValid(it) }
            val downNumber = play.down
            val distance = play.distance
            // Kickoffs are never run on a down, whatever the feed says.
            val down = if (downNumber != null && downNumber in 1..4 && distance != null && distance.isFinite() && distance >= 0 &&
                !SportsGameFormat.matches("kickoff", play.type)
            ) {
                val goal = yard?.let { distance >= if (direction == 1) 100 - it else it } ?: false
                "${listOf("1st", "2nd", "3rd", "4th")[downNumber - 1]} & ${if (goal) "Goal" else number(distance)}"
            } else {
                null
            }
            fun own(value: Double?) = value?.takeIf { SportsFootballYards.isValid(it) }?.let { SportsFootballYards.own(it, offense, game) }
            return SportsFootballPlayInfo(
                kind = kind,
                down = down,
                spot = yard?.let { SportsFootballYards.label(it, game) },
                result = result(play, kind),
                type = type(play.type),
                start = own(play.start),
                end = own(play.end),
            )
        }

        /** Not a snap: kickoffs, timeouts, period ends and the two-minute warning stay off the field. */
        fun isFieldPlay(play: SportsFootballPlay): Boolean =
            !SportsGameFormat.matches("kickoff|timeout|end period|end of|two.minute warning", play.type)

        fun kind(play: SportsFootballPlay): Kind {
            val type = play.type.lowercase()
            val id = play.typeId.orEmpty()
            return when {
                SportsGameFormat.matches("""\bno play\b""", play.description) || "penalty" in type -> Kind.PENALTY
                id == "26" || SportsGameFormat.matches("interception|fumble", type) -> Kind.TURNOVER
                id == "7" || "sack" in type -> Kind.SACK
                id == "3" || SportsGameFormat.matches("incompletion|incomplete", type) -> Kind.INCOMPLETE
                id in setOf("24", "67") || SportsGameFormat.matches("pass reception|passing touchdown|pass complete", type) -> Kind.PASS
                id in setOf("5", "68") || SportsGameFormat.matches("rush|run", type) -> Kind.RUSH
                SportsGameFormat.matches("kick|punt|field goal", type) -> Kind.KICK
                else -> Kind.OTHER
            }
        }

        private fun result(play: SportsFootballPlay, kind: Kind): String {
            if (kind == Kind.INCOMPLETE) return "Incomplete pass"
            if (kind != Kind.PASS && kind != Kind.RUSH) return play.type
            val yards = play.yards?.takeIf { it.isFinite() }
            val label = yards?.let { if (kind == Kind.PASS) "${number(it)}-yard completion" else "${number(it)}-yard run" }
                ?: if (kind == Kind.PASS) "Completed pass" else "Run"
            val distance = play.distance
            return when {
                SportsGameFormat.matches("touchdown", play.type) -> "$label · Touchdown"
                play.scoring == true -> "$label · Scoring play"
                yards != null && distance != null && distance > 0 && yards >= distance -> "$label · First down"
                else -> label
            }
        }

        private fun type(type: String): String = when {
            SportsGameFormat.matches("^pass", type) -> "Pass"
            SportsGameFormat.matches("^(rush|run)", type) -> "Run"
            SportsGameFormat.matches("sack", type) -> "Sack"
            SportsGameFormat.matches("field goal", type) -> "Field goal"
            SportsGameFormat.matches("penalty", type) -> "Penalty"
            else -> type.ifEmpty { "Play" }
        }

        private fun number(value: Double): String =
            if (value == Math.rint(value) && abs(value) < 1e6) value.toLong().toString() else value.toString()
    }
}

/**
 * Where a play lane's label sits on the field drawing (Expo `footballRibbonLabel`, in the drawing's
 * 840-wide units): inside the ribbon when it is long enough, else beside its tail, else above it.
 */
data class SportsRibbonLabel(val x: Double, val anchor: Anchor, val inside: Boolean, val above: Boolean) {
    enum class Anchor { LEADING, CENTER, TRAILING }

    companion object {
        fun of(start: Double?, end: Double?, text: String): SportsRibbonLabel {
            val width = text.length * 7.0
            if (start == null || end == null) return SportsRibbonLabel(423.0, Anchor.CENTER, inside = false, above = true)
            val x1 = SportsFieldSpace.x(start)
            val x2 = SportsFieldSpace.x(end)
            val sign = if (x2 >= x1) 1 else -1
            if (abs(x2 - x1) >= width + 24) return SportsRibbonLabel((x1 + x2 - sign * 8) / 2, Anchor.CENTER, inside = true, above = false)
            val tail = x1 - sign * 12
            return if (if (sign > 0) tail - width >= 82 else tail + width <= 764) {
                SportsRibbonLabel(tail, if (sign > 0) Anchor.TRAILING else Anchor.LEADING, inside = false, above = false)
            } else {
                SportsRibbonLabel((82 + width / 2).coerceAtLeast(minOf(764 - width / 2, (x1 + x2) / 2)), Anchor.CENTER, inside = false, above = true)
            }
        }
    }
}

/**
 * The football drawings' units (Expo's 840 × 360 view box): goal lines at x 78 and 768, end zones
 * 60 wide outside them, the turf from y 28 to 334.
 */
object SportsFieldSpace {
    const val WIDTH = 840f
    const val HEIGHT = 360f
    const val TOP = 28f
    const val BOTTOM = 334f

    /** The x of a spot measured from the left goal line. */
    fun x(yard: Double): Double = 78 + yard / 100 * 690
}

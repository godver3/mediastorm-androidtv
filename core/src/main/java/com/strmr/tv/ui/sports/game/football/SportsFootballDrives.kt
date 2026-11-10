package com.strmr.tv.ui.sports.game.football

import com.strmr.tv.sports.SportsFootballDrive
import com.strmr.tv.sports.SportsFootballPlay
import com.strmr.tv.sports.SportsGame
import com.strmr.tv.sports.SportsTeam
import com.strmr.tv.ui.sports.game.SportsGameFormat
import kotlin.math.abs
import kotlin.math.floor

/**
 * Spots on an American football field (Expo `football-presentation.ts`). The feed measures from
 * the away side's goal line (0) to the home side's (100); the page draws every offense attacking
 * right, so a spot is first turned to the offense's own goal line.
 */
object SportsFootballYards {
    /** A spot on the field, 0 to 100 (Expo `validFootballYard`). */
    fun isValid(yard: Double?): Boolean = yard != null && yard.isFinite() && yard in 0.0..100.0

    /** A spot measured from [team]'s own goal line (Expo `footballOwnYard`). */
    fun own(yard: Double, team: SportsTeam, game: SportsGame): Double = if (team.id == game.homeTeam.id) 100 - yard else yard

    /** "NE 27", "CHI 12" or "50": a spot on the feed's scale named by its half of the field. */
    fun label(yard: Double, game: SportsGame): String {
        if (yard == 50.0) return "50"
        val side = if (yard < 50) game.awayTeam else game.homeTeam
        return "${side.shortName} ${rounded(if (yard < 50) yard else 100 - yard).toInt()}"
    }

    /** Halves round up, as Expo's `Math.round`. */
    fun rounded(value: Double): Double = floor(value + 0.5)
}

/** Where the feed placed the drive's start, unless it says it couldn't. */
val SportsFootballDrive.knownStart: Double? get() = start.takeIf { startKnown != false && SportsFootballYards.isValid(it) }
val SportsFootballDrive.knownEnd: Double? get() = end.takeIf { endKnown != false && SportsFootballYards.isValid(it) }

/**
 * Whether the start and end make a credible path (Expo `hasReliableDrivePath`): both placed on the
 * field and apart. Equal ends, the feed's goal-line placeholders among them, aren't movement.
 */
val SportsFootballDrive.hasReliablePath: Boolean
    get() {
        val start = knownStart ?: return false
        val end = knownEnd ?: return false
        return start != end
    }

/** The feed's complete, labelled summary "6 plays, 23 yards, 3:30" (Expo `footballDriveSummary`). */
val SportsFootballDrive.summary: SportsDriveSummary? get() = SportsDriveSummary.of(description)

val SportsFootballDrive.playsRun: Int? get() = playCount ?: summary?.plays
val SportsFootballDrive.netYards: Int? get() = yards?.let { SportsFootballYards.rounded(it).toInt() } ?: summary?.yards
val SportsFootballDrive.duration: String? get() = elapsed ?: summary?.duration

/** "12 plays · 51 yards · 4:48", from what the feed sent. */
val SportsFootballDrive.statsLine: String?
    get() = listOfNotNull(
        playsRun?.let { if (it == 1) "1 play" else "$it plays" },
        netYards?.let { if (abs(it) == 1) "$it yard" else "$it yards" },
        duration,
    ).ifEmpty { null }?.joinToString(" · ")

/** "CHI 9 → NE 40", dashes where the feed has no label. */
val SportsFootballDrive.span: String get() = "${startLabel.ifEmpty { "—" }} → ${endLabel.ifEmpty { "—" }}"

/** A drive's plays, yards and time read from its description; null for any other wording. */
data class SportsDriveSummary(val plays: Int, val yards: Int, val duration: String) {
    companion object {
        private val pattern = Regex("""(\d+) plays?,\s*(-?\d+) yards?,\s*(\d+:\d{2})""", RegexOption.IGNORE_CASE)

        fun of(description: String): SportsDriveSummary? {
            val match = pattern.matchEntire(description.trim()) ?: return null
            val (plays, yards, duration) = match.destructured
            return SportsDriveSummary(plays.toIntOrNull() ?: return null, yards.toIntOrNull() ?: return null, duration)
        }
    }
}

/**
 * How a drive ended, in words (Expo `footballDriveResult`): "In progress" for the offense's drive
 * while the game is live with fresh data (not at halftime), "Touchdown" and "Field goal" for TD
 * and FG, else the feed's own ("PUNT", "DOWNS"). Touchdowns, field goals and safeties score.
 */
data class SportsDriveResult(val text: String, val scoring: Boolean, val current: Boolean) {
    companion object {
        fun of(drive: SportsFootballDrive, possession: SportsFootballPossession?): SportsDriveResult {
            val current = drive.current && possession != null && possession.isLive &&
                possession.pause?.kind != SportsFootballPause.Kind.HALFTIME && possession.offense.id == drive.teamId
            val raw = drive.result.trim()
            val scoring = !current && SportsGameFormat.matches("""^(touchdown|td|field goal|fg|safety)$""", raw)
            val text = when {
                current -> "In progress"
                else -> mapOf("TD" to "Touchdown", "FG" to "Field goal")[raw.uppercase()] ?: raw.ifEmpty { "Result unavailable" }
            }
            return SportsDriveResult(text, scoring, current)
        }
    }
}

/**
 * The Drives tab's list and choice: both sides' drives or one side's, newest first, numbered in
 * game order. The chosen drive stays chosen while it is listed; otherwise the drive in progress,
 * else the newest one listed.
 */
data class SportsDriveExplorer(
    /** In game order, as the feed sends them. */
    val drives: List<SportsFootballDrive>,
    /** The side shown; null for both. */
    val teamId: String? = null,
    val chosenId: String? = null,
    /** The drive in progress, chosen when nothing else is. */
    val currentId: String? = null,
) {
    val listed: List<SportsFootballDrive> get() = drives.filter { teamId == null || it.teamId == teamId }.asReversed()

    val selected: SportsFootballDrive?
        get() {
            val listed = listed
            return listed.firstOrNull { it.id == chosenId } ?: listed.firstOrNull { it.id == currentId } ?: listed.firstOrNull()
        }

    fun number(drive: SportsFootballDrive): Int = number(drive, drives)

    companion object {
        /** A drive's number in the game, from 1 (Expo numbers drives by their place in the feed). */
        fun number(drive: SportsFootballDrive, drives: List<SportsFootballDrive>): Int =
            drives.indexOfFirst { it.id == drive.id }.let { if (it < 0) drives.size else it } + 1

        /** The plays drawn as lanes on the chosen drive's field: snaps with both ends placed, the latest [limit]. */
        fun lanes(plays: List<SportsFootballPlay>, limit: Int = 16) =
            plays.filter { SportsFootballPlayInfo.isFieldPlay(it) && SportsFootballYards.isValid(it.start) && SportsFootballYards.isValid(it.end) }
                .takeLast(limit)
    }
}

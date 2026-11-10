package com.strmr.tv.ui.sports.game.football

import com.strmr.tv.sports.SportsFootballDrive
import com.strmr.tv.sports.SportsFootballPlay
import com.strmr.tv.sports.SportsFootballSituation
import com.strmr.tv.sports.SportsGame
import com.strmr.tv.sports.SportsLifecycle
import com.strmr.tv.sports.SportsTeam
import com.strmr.tv.sports.liveFootballSituation
import com.strmr.tv.sports.periodLabel
import com.strmr.tv.ui.sports.game.SportsGameFormat
import com.strmr.tv.ui.sports.game.SportsGamePage
import java.time.Instant

/**
 * The ball's spot and the line to gain on the feed's scale, from a live situation (Expo
 * `footballSituationGeometry`): "CHI 12" or "Midfield" placed by the side it names, then "3rd & 4"
 * or "1st & Goal" measured toward the offense's goal. Null when the spot can't be placed.
 */
data class SportsFootballGeometry(
    val ball: Double,
    /** 1 when the away side has the ball (it attacks the home goal at 100), −1 for the home side. */
    val direction: Double,
    val target: Double?,
    val offense: SportsTeam,
) {
    companion object {
        private val spotPattern = Regex("""(?:Ball (?:at|on)\s+)?([\w.-]+)\s+(\d{1,2})""", RegexOption.IGNORE_CASE)
        private val midfieldPattern = Regex("midfield|50", RegexOption.IGNORE_CASE)
        private val downPattern = Regex("""\b([1-4])(?:st|nd|rd|th)?\s*(?:&|and)\s*(goal|\d+)\b""", RegexOption.IGNORE_CASE)

        fun of(situation: SportsFootballSituation, game: SportsGame): SportsFootballGeometry? {
            val offense = situation.offense(game) ?: return null
            val spot = spotPattern.matchEntire(situation.fieldPosition)
            val midfield = midfieldPattern.matches(situation.fieldPosition)
            val side = spot?.let { match ->
                listOf(game.awayTeam, game.homeTeam).firstOrNull { it.abbreviation.orEmpty().lowercase() == match.groupValues[1].lowercase() }
            }
            val yards = spot?.groupValues?.get(2)?.toDoubleOrNull() ?: 50.0
            if (!(midfield || side != null) || yards > 50) return null
            val ball = if (midfield) 50.0 else if (side?.id == game.awayTeam.id) yards else 100 - yards
            val direction = if (offense.id == game.awayTeam.id) 1.0 else -1.0
            val distance = downPattern.find(situation.downDistance)?.let { match ->
                val value = match.groupValues[2]
                if (value.lowercase() == "goal") (if (direction == 1.0) 100 - ball else ball) else value.toDoubleOrNull()
            }
            val target = distance?.let { ball + direction * it }?.takeIf { it in 0.0..100.0 }
            return SportsFootballGeometry(ball, direction, target, offense)
        }
    }
}

/**
 * What holds the field still (Expo `footballPauseStatus` and `footballScoringStatus`): halftime, a
 * timeout, a score just made, or the field waiting for data.
 */
data class SportsFootballPause(val kind: Kind, val title: String, val subtitle: String? = null) {
    enum class Kind { HALFTIME, TIMEOUT, SCORE, WAITING }

    companion object {
        private val remainingPattern =
            Regex("""(?:halftime|intermission).*?(\d{1,2}:\d{2})\s*(?:remaining|left)""", RegexOption.IGNORE_CASE)

        /**
         * Halftime (with a reported "12:00 remaining"), or a timeout the status or the latest play
         * reports, credited to a side only when exactly one is named.
         */
        fun interval(page: SportsGamePage): SportsFootballPause? {
            val game = page.game
            val status = listOfNotNull(game.periodLabel, game.statusDetail).filter { it.isNotEmpty() }.joinToString(" ")
            if (SportsGameFormat.matches("halftime|intermission", status)) {
                val remaining = remainingPattern.find(status)?.groupValues?.get(1)
                return SportsFootballPause(Kind.HALFTIME, "Halftime", remaining?.let { "$it remaining" })
            }
            val latest = page.detail.plays.firstOrNull()
            val playText = latest?.let { listOfNotNull(it.title, it.description).filter { text -> text.isNotEmpty() }.joinToString(" ") }.orEmpty()
            val clock = clock(game)
            val timeoutPlay = SportsGameFormat.matches("time[ -]?out", playText) && clock != null &&
                latest?.clock == clock && latest.periodLabel == game.periodLabel
            if (!SportsGameFormat.matches("time[ -]?out", status) && !timeoutPlay) return null
            val text = "$status ${if (timeoutPlay) playText else ""}"
            fun named(names: List<String?>) = names.filterNotNull().filter { it.isNotEmpty() }.any { name ->
                val escaped = Regex.escape(name)
                SportsGameFormat.matches("""(?:\bby\s+$escaped\b|\b$escaped\s+time[ -]?out\b)""", text)
            }
            val teams = listOf(game.awayTeam, game.homeTeam)
            val exact = teams.filter { named(listOf(it.name, it.abbreviation)) }
            val candidates = exact.ifEmpty { teams.filter { named(listOf(it.location, it.nickname)) } }
            val caller = candidates.singleOrNull()
            val title = when {
                SportsGameFormat.matches("official|injury", text) -> "Official timeout"
                caller != null -> "Timeout · ${caller.nickname?.ifEmpty { null } ?: caller.name}"
                else -> "Timeout"
            }
            return SportsFootballPause(Kind.TIMEOUT, title)
        }

        /** The score the latest play made at this very moment of a live game ("Touchdown", "Patriots · 6 points"). */
        fun score(page: SportsGamePage, live: Boolean): SportsFootballPause? {
            val game = page.game
            val play = page.detail.plays.firstOrNull() ?: return null
            if (!live || !play.scoring || play.clock == null || play.clock != clock(game) || play.periodLabel != game.periodLabel) return null
            val kinds = listOf(
                Triple("touchdown", "Touchdown", 6), Triple("field goal", "Field goal", 3), Triple("safety", "Safety", 2),
                Triple("two.point|2.point", "Two-point conversion", 2), Triple("extra point", "Extra point", 1),
            )
            val (_, title, points) = kinds.firstOrNull { SportsGameFormat.matches(it.first, play.title) } ?: return null
            val side = page.team(play.teamId)?.let { it.nickname?.ifEmpty { null } ?: it.name }
            val worth = if (points == 1) "1 point" else "$points points"
            return SportsFootballPause(Kind.SCORE, title, listOfNotNull(side, worth).joinToString(" · "))
        }

        /** The game clock the page reads while live (Expo `detail.clock`). */
        private fun clock(game: SportsGame): String? = game.clock?.ifEmpty { null }?.takeIf { game.lifecycle == SportsLifecycle.LIVE }
    }
}

/**
 * The Overview field's state (Expo `footballPossessionView`): the offense (the side the live
 * situation names, else the away side so both end zones still show), its current drive and that
 * drive's plays, the ball and the line to gain turned to the offense's own goal, and what holds the
 * field still. A final says Final and a scheduled game "Kickoff upcoming" even when their data is
 * old (Expo shows "Updates delayed" for those too); otherwise old data waits for news.
 */
data class SportsFootballPossession(
    val offense: SportsTeam,
    val opponent: SportsTeam,
    val pause: SportsFootballPause?,
    val situation: SportsFootballSituation?,
    val currentDrive: SportsFootballDrive?,
    val plays: List<SportsFootballPlay>,
    val ball: Double?,
    val target: Double?,
    /** Live with fresh data. */
    val isLive: Boolean,
) {
    companion object {
        fun of(page: SportsGamePage, now: Instant = Instant.now()): SportsFootballPossession? {
            if (!page.isFootballLeague) return null
            val game = page.game
            val stale = page.isStale(now)
            val live = page.lifecycle == SportsLifecycle.LIVE && !stale
            val situation = game.liveFootballSituation
            val known = situation?.offense(game)
            val offense = known ?: game.awayTeam
            val geometry = if (live && known != null) situation?.let { SportsFootballGeometry.of(it, game) } else null
            val drive = if (live) page.detail.footballDrives.firstOrNull { it.current && it.teamId == offense.id } else null
            fun waiting(title: String, subtitle: String? = null) = SportsFootballPause(SportsFootballPause.Kind.WAITING, title, subtitle)
            val pause = when {
                page.lifecycle == SportsLifecycle.FINAL -> waiting("Final")
                page.lifecycle == SportsLifecycle.SCHEDULED -> waiting("Kickoff upcoming")
                stale -> waiting("Updates delayed", "Waiting for the latest field position")
                else -> SportsFootballPause.interval(page) ?: SportsFootballPause.score(page, live)
                    ?: if (live && known != null) null else waiting("Waiting for field position")
            }
            return SportsFootballPossession(
                offense = offense,
                opponent = if (offense.id == game.awayTeam.id) game.homeTeam else game.awayTeam,
                pause = pause,
                situation = situation,
                currentDrive = drive,
                plays = drive?.plays.orEmpty(),
                ball = geometry?.let { SportsFootballYards.own(it.ball, offense, game) },
                target = geometry?.target?.let { SportsFootballYards.own(it, offense, game) },
                isLive = live,
            )
        }
    }
}

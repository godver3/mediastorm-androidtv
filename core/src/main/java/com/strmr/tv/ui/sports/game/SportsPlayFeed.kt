package com.strmr.tv.ui.sports.game

import com.strmr.tv.sports.SportsPlay

/**
 * The Play-by-Play tab's filters and pages (Expo `PlaysPanel`): every play or only scoring ones
 * (goals for soccer and hockey), soccer cards and substitutions, hockey penalties and shots,
 * one period ([period], null for all), and pages of [PAGE_SIZE], newest first as the feed sends
 * them.
 */
data class SportsPlayFeed(
    val plays: List<SportsPlay>,
    val kind: SportsKind,
    val filter: Filter = Filter.ALL,
    val period: String? = null,
    val page: Int = 0,
) {
    enum class Filter { ALL, SCORING, CARDS, SUBSTITUTIONS, PENALTIES, SHOTS }

    /** The filters this sport offers. */
    val filters: List<Filter>
        get() = when (kind) {
            SportsKind.SOCCER -> listOf(Filter.ALL, Filter.SCORING, Filter.CARDS, Filter.SUBSTITUTIONS)
            SportsKind.HOCKEY -> listOf(Filter.ALL, Filter.SCORING, Filter.PENALTIES, Filter.SHOTS)
            else -> listOf(Filter.ALL, Filter.SCORING)
        }

    fun label(filter: Filter): String {
        val goals = kind == SportsKind.SOCCER || kind == SportsKind.HOCKEY
        return when (filter) {
            Filter.ALL -> if (kind == SportsKind.SOCCER) "All Key Events" else "All Plays"
            Filter.SCORING -> if (goals) "Goals" else "Scoring Plays"
            Filter.CARDS -> "Cards"
            Filter.SUBSTITUTIONS -> "Substitutions"
            Filter.PENALTIES -> "Penalties"
            Filter.SHOTS -> "Shots"
        }
    }

    /** The feed's periods in order of appearance; the period chips show only with two or more. */
    val periods: List<String> = plays.map { it.periodLabel }.filter { it.isNotEmpty() }.distinct()

    /** The chosen period while the feed still has it. */
    val activePeriod: String? = period?.takeIf { it in periods }

    val visible: List<SportsPlay> = plays.filter { (activePeriod == null || it.periodLabel == activePeriod) && matches(it) }

    val pageCount: Int get() = maxOf(1, (visible.size + PAGE_SIZE - 1) / PAGE_SIZE)
    val currentPage: Int get() = page.coerceIn(0, pageCount - 1)

    val pagePlays: List<SportsPlay>
        get() {
            val start = currentPage * PAGE_SIZE
            return if (start < visible.size) visible.subList(start, minOf(start + PAGE_SIZE, visible.size)) else emptyList()
        }

    /** "Newest first · 12 plays" on one page, "Newest first · 17–32 of 180" across several. */
    val summary: String
        get() {
            val count = visible.size
            if (pageCount == 1) return "Newest first · $count ${if (kind == SportsKind.SOCCER) "key events" else "plays"}"
            val first = currentPage * PAGE_SIZE + 1
            return "Newest first · $first–${minOf(first + PAGE_SIZE - 1, count)} of $count"
        }

    val emptyText: String
        get() = if (filter == Filter.SCORING) "No scoring plays in the loaded feed." else "No plays in the loaded feed."

    private fun matches(play: SportsPlay): Boolean {
        val event = SportsEventKind.of(play.title)
        return when (filter) {
            Filter.ALL -> true
            Filter.SCORING ->
                if (kind == SportsKind.SOCCER || kind == SportsKind.HOCKEY) event == SportsEventKind.GOAL || play.scoring else play.scoring
            Filter.CARDS -> event == SportsEventKind.YELLOW_CARD || event == SportsEventKind.RED_CARD
            Filter.SUBSTITUTIONS -> event == SportsEventKind.SUBSTITUTION
            Filter.PENALTIES -> event == SportsEventKind.PENALTY
            Filter.SHOTS -> event == SportsEventKind.GOAL ||
                SportsGameFormat.matches("""^(shot|shot on goal|shot on target|saved shot|missed shot|blocked shot)$""", play.title.trim())
        }
    }

    companion object {
        const val PAGE_SIZE = 16
    }
}

/** A soccer or hockey event's type from the feed's title, never from its narrative (Expo `sportsTimelineKind`). */
enum class SportsEventKind {
    GOAL, YELLOW_CARD, RED_CARD, SUBSTITUTION, PENALTY, OTHER;

    companion object {
        fun of(title: String): SportsEventKind {
            val normalized = title.lowercase().trim().replace(Regex("[_-]+"), " ").replace(Regex("""\s+"""), " ")
            return when (normalized) {
                in goals -> GOAL
                "yellow card", "yellowcard", "booking" -> YELLOW_CARD
                "red card", "redcard", "second yellow card", "second yellow", "yellow red card" -> RED_CARD
                "substitution", "substitution on", "substitution off", "substitutionon", "substitutionoff" -> SUBSTITUTION
                in penalties -> PENALTY
                else -> OTHER
            }
        }

        private val goals = setOf(
            "goal", "own goal", "goal scored", "goal header", "goal free kick", "goal volley", "goal left footed shot",
            "goal right footed shot", "penalty scored", "penalty goal", "power play goal", "short handed goal",
            "shorthanded goal", "empty net goal",
        )

        private val penalties = setOf(
            "penalty", "minor penalty", "major penalty", "misconduct penalty", "penalty shot", "penalty missed",
            "penalty saved", "penalty awarded", "penalty conceded",
        )
    }
}

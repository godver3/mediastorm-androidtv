package com.strmr.tv.api

import java.util.Locale

/**
 * The confirmation a bulk Watch State change asks for before it is sent, shared by the TV panel
 * and the mobile sheet. Every whole-show choice asks; a single season asks only from
 * [SEASON_THRESHOLD] episodes; one episode never asks.
 */
object BulkWatchConfirmation {
    const val SEASON_THRESHOLD = 50

    /** Title, message and the confirming button's label. */
    data class Prompt(val title: String, val message: String, val confirm: String)

    /** A whole-show choice; [exceptSeason] is the season "All except Season N" leaves alone. */
    fun allEpisodes(watched: Boolean, count: Int, show: String, exceptSeason: Int? = null): Prompt {
        val scope = if (exceptSeason != null) "$show except Season $exceptSeason" else show
        return prompt(watched, count, if (watched) "Every aired episode of $scope" else "every episode of $scope")
    }

    /** A season toggle, or null when it changes too few episodes to ask. */
    fun season(watched: Boolean, count: Int, show: String, seasonName: String): Prompt? {
        if (count < SEASON_THRESHOLD) return null
        return prompt(watched, count, if (watched) "Every aired episode in $seasonName of $show" else "every episode in $seasonName of $show")
    }

    private fun prompt(watched: Boolean, count: Int, what: String): Prompt {
        val episodes = if (count == 1) "1 episode" else String.format(Locale.US, "%,d episodes", count)
        return if (watched) {
            Prompt("Mark $episodes watched?", "$what will be marked watched. Episodes you haven't played won't appear in watch history.", "Mark Watched")
        } else {
            Prompt("Mark $episodes unwatched?", "This clears the watched state of $what.", "Mark Unwatched")
        }
    }
}

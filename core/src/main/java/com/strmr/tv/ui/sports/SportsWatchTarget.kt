package com.strmr.tv.ui.sports

import com.strmr.tv.sports.SportsGame
import com.strmr.tv.sports.SportsLifecycle

/**
 * What the stream picker looks for (Expo `SportsWatchTarget`): a game, or a race session with
 * the [kind] (`racing`) and weekend ([parentId]) the server resolves it by.
 */
data class SportsWatchTarget(
    val id: String,
    val title: String,
    val live: Boolean,
    val kind: String? = null,
    val parentId: String? = null,
) {
    companion object {
        fun game(game: SportsGame) = SportsWatchTarget(game.id, game.displayTitle, game.lifecycle == SportsLifecycle.LIVE)
    }
}

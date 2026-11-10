package com.strmr.tv.ui.sports.game

import androidx.compose.runtime.staticCompositionLocalOf

/** A player's line in the Box Score: their side, their id and the line's category ("Batting"). */
data class SportsPlayerTarget(val teamId: String, val playerId: String, val category: String)

/**
 * Opens the Box Score on a player's line (Expo `onPlayerSelect`: the batter or pitcher on MLB's
 * Overview; tvOS `sportsShowPlayer`); null where the page can't.
 */
val LocalSportsShowPlayer = staticCompositionLocalOf<((SportsPlayerTarget) -> Unit)?> { null }

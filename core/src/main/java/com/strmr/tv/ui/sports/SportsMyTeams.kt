package com.strmr.tv.ui.sports

import com.strmr.tv.sports.SportsTeamFollows

/** The hub's My Teams filter: only games a followed team plays in (never races or cycling). */
object SportsMyTeams {
    const val NO_TEAMS = "Follow a team from its game page or Customize to see only its games."

    fun games(items: List<SportsHubItem>, teamIds: List<String>): List<SportsHubItem> =
        items.filter { it is SportsHubItem.Match && SportsTeamFollows.plays(teamIds, it.game) }
}

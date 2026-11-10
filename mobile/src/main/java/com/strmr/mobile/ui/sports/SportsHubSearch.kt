package com.strmr.mobile.ui.sports

import com.strmr.tv.ui.sports.SportsHubItem
import com.strmr.tv.ui.sports.SportsSection

/**
 * The hub's search (the iPhone's): a team's name or abbreviation, the event's title, or its
 * competition, ignoring case. Sections left empty drop out.
 */
object SportsHubSearch {
    fun matches(item: SportsHubItem, query: String, league: (String) -> String): Boolean {
        val fields = when (item) {
            is SportsHubItem.Match -> listOfNotNull(
                item.game.displayTitle, item.game.homeTeam.name, item.game.awayTeam.name,
                item.game.homeTeam.abbreviation, item.game.awayTeam.abbreviation, league(item.game.league),
            )
            is SportsHubItem.Race -> listOfNotNull(item.listing.weekend.title, item.listing.session.sessionType, league(item.listing.session.league))
            is SportsHubItem.Cycling -> listOfNotNull(item.listing.race.name, item.listing.stage?.name, "Cycling")
        }
        return fields.any { it.contains(query, ignoreCase = true) }
    }

    fun filter(sections: List<SportsSection>, query: String, league: (String) -> String): List<SportsSection> {
        val words = query.trim()
        if (words.isEmpty()) return sections
        return sections.mapNotNull { s -> s.items.filter { matches(it, words, league) }.ifEmpty { null }?.let { s.copy(items = it) } }
    }
}

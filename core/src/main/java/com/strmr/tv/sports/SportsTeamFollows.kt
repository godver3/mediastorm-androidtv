package com.strmr.tv.sports

/**
 * Followed teams, saved in the sports preferences' `teamIds` as `"<league>:<team id>"` (the Expo
 * format: the game's league and the team's id). Standings rows can carry a provider-unprefixed id
 * while the scoreboard's is prefixed (`espn:13` vs `13`), so a stored id matches a team of the
 * same league whose id ends the same way (as hub records match table rows on the last id part).
 */
object SportsTeamFollows {
    /** The id a team is saved under: [teamId] exactly as the game or standings row sends it. */
    fun id(league: String, teamId: String) = "$league:$teamId"

    /** Whether the stored [teamIds] follow the team [teamId] of [league]. */
    fun isFollowed(teamIds: Collection<String>, league: String, teamId: String): Boolean {
        if (league.isEmpty() || teamId.isEmpty()) return false
        val exact = id(league, teamId)
        val prefix = "$league:"
        val last = teamId.substringAfterLast(':')
        return teamIds.any { it == exact || (it.startsWith(prefix) && it.removePrefix(prefix).substringAfterLast(':') == last) }
    }

    /** Whether a followed team plays in [game] (either side). */
    fun plays(teamIds: Collection<String>, game: SportsGame): Boolean =
        teamIds.isNotEmpty() &&
            (isFollowed(teamIds, game.league, game.homeTeam.id) || isFollowed(teamIds, game.league, game.awayTeam.id))

    /**
     * [preferences] following the team or not, every other field as it was. Following adds the
     * exact id unless the team is already followed; unfollowing drops every stored id that
     * matches it.
     */
    fun update(preferences: SportsPreferences, league: String, teamId: String, follow: Boolean): SportsPreferences {
        val ids = preferences.teamIds
        val next = if (follow) {
            if (isFollowed(ids, league, teamId)) ids else ids + id(league, teamId)
        } else {
            ids.filterNot { isFollowed(listOf(it), league, teamId) }
        }
        return if (next == ids) preferences else preferences.copy(teamIds = next)
    }

    /** A stored id's league and team id (`"nba:13"` → `nba`, `13`); null when it isn't one. */
    fun parse(stored: String): Pair<String, String>? {
        val league = stored.substringBefore(':', "")
        val team = stored.substringAfter(':', "")
        return if (league.isEmpty() || team.isEmpty()) null else league to team
    }

    /** Leagues whose events are matchups of two teams (not tennis, golf, combat, racing or cycling). */
    fun isTeamLeague(league: SportsLeague): Boolean =
        !league.isRacing && league.supportsTeams != false && SportsHubFilters.family(league) !in individualSports

    private val individualSports = setOf("tennis", "golf", "mma", "boxing", SportsCycling.SPORT, SportsHubFilters.MOTORSPORT)

    /**
     * A followed team's name: from the league's standings, else from a game seen in the hub,
     * else the stored id as it is.
     */
    fun name(stored: String, tables: Map<String, List<SportsLeagueTable>>, games: List<SportsGame>): String {
        val league = parse(stored)?.first ?: return stored
        tables[league].orEmpty().asSequence().flatMap { it.rows }
            .firstOrNull { it.kind == "team" && isFollowed(listOf(stored), league, it.id) }?.let { return it.name }
        games.asSequence().filter { it.league == league }.flatMap { sequenceOf(it.homeTeam, it.awayTeam) }
            .firstOrNull { it.name.isNotEmpty() && isFollowed(listOf(stored), league, it.id) }?.let { return it.name }
        return stored
    }

    /** A league's teams from its standings: team rows, once each by id, by name. */
    fun teams(tables: List<SportsLeagueTable>): List<SportsTableRow> =
        tables.flatMap { it.rows }.filter { it.kind == "team" && it.id.isNotEmpty() }
            .distinctBy { it.id }.sortedBy { it.name.lowercase() }
}

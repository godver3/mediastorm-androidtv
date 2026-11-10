package com.strmr.tv.ui.sports.game

/**
 * A tab of a game's page, in the Expo TV page's order (`SportsGameDetailTV`): Overview, the
 * sport's own tabs (Drives for NFL and college football, Lineups for soccer), Box Score or Match
 * Stats, Play-by-Play or Commentary, Standings.
 */
enum class SportsGameTab {
    OVERVIEW, DRIVES, LINEUPS, STATS, PLAYS, STANDINGS;

    fun label(kind: SportsKind): String = when (this) {
        OVERVIEW -> "Overview"
        DRIVES -> "Drives"
        LINEUPS -> "Lineups"
        STATS -> if (kind == SportsKind.SOCCER) "Match Stats" else "Box Score"
        PLAYS -> if (kind == SportsKind.SOCCER) "Commentary" else "Play-by-Play"
        STANDINGS -> "Standings"
    }

    /**
     * Whether the feed can fill this tab (Expo `availableTab`): drives and lineups when sent,
     * stats and plays by the detail's capabilities (no stats tab for golf tournaments or cricket,
     * whose Overview carries their scorecards), standings when a table came along.
     */
    fun isAvailable(page: SportsGamePage): Boolean {
        val detail = page.detail
        return when (this) {
            OVERVIEW -> true
            DRIVES -> page.isFootballLeague && detail.footballDrives.isNotEmpty()
            LINEUPS -> page.kind == SportsKind.SOCCER && detail.lineups.isNotEmpty()
            STATS -> detail.capabilities.stats && !page.isIndividualGolf && page.kind != SportsKind.CRICKET
            PLAYS -> detail.capabilities.plays
            STANDINGS -> detail.standings.isNotEmpty()
        }
    }

    companion object {
        /**
         * The page's tabs. None with scores hidden: the page then shows only what was known
         * before the start (Expo hides the tab rail while stats are suppressed).
         */
        fun available(page: SportsGamePage, spoilers: Boolean): List<SportsGameTab> =
            if (spoilers) emptyList() else entries.filter { it.isAvailable(page) }

        /**
         * The tab to show: the one picked while it has data, else the Overview. The pick is kept,
         * so a tab that empties for a refresh comes back with its data.
         */
        fun shown(picked: SportsGameTab, tabs: List<SportsGameTab>): SportsGameTab = if (picked in tabs) picked else OVERVIEW
    }
}

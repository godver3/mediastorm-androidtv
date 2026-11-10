package com.strmr.tv.sports

import com.strmr.tv.api.ApiClient
import com.strmr.tv.api.LossyListSerializer
import kotlinx.serialization.Serializable

/** A league's season tables (Expo `getSportsLeagueStandings`). */
suspend fun ApiClient.sportsStandings(league: String): SportsLeagueStandings =
    send("GET", "/api/sports/standings/${segment(league)}", timeoutMs = 60_000)

/**
 * `GET /api/sports/standings/{league}` (Expo `LeagueStandings`): the provider's season tables, one
 * per conference, division or league table. [state] is `available`, `stale` or `unavailable`
 * (with a [reason]); a stale answer keeps the last tables.
 */
@Serializable
data class SportsLeagueStandings(
    val league: String = "",
    val state: String = "unavailable",
    val source: String = "",
    val sourceUrl: String = "",
    val updatedAt: String? = null,
    val reason: String? = null,
    @Serializable(with = LossyListSerializer::class) val groups: List<SportsLeagueTable> = emptyList(),
) {
    val available: Boolean get() = state == "available"
    val delayed: Boolean get() = state == "stale" || state == "unavailable"
}

/** One table: its [columns] in the provider's order and its rows, leader first. */
@Serializable
data class SportsLeagueTable(
    val id: String,
    val title: String = "",
    val season: Int = 0,
    val seasonLabel: String = "",
    @Serializable(with = LossyListSerializer::class) val columns: List<SportsTableColumn> = emptyList(),
    @Serializable(with = LossyListSerializer::class) val rows: List<SportsTableRow> = emptyList(),
)

/** A statistic of the table: [key] (`overall`, `playoffSeed`, `vs. Conf.`…) and its heading. */
@Serializable
data class SportsTableColumn(val key: String, val label: String = "")

/** A team's (or an athlete's) line: its values by column key, as display text. */
@Serializable
data class SportsTableRow(
    val id: String,
    val name: String,
    val abbreviation: String? = null,
    val kind: String = "team",
    val values: Map<String, String> = emptyMap(),
)

/**
 * League standings rules (Expo `SportsLeagueStandings` and `sports-hub-records.ts`): the columns a
 * table shows, the leading entry, and records for hub cards that lack one.
 */
object SportsStandings {
    private val NUMBER = Regex("""^\d+$""")

    /**
     * The columns a table shows, at most [limit], in Expo's order per kind of league: soccer
     * rank, points, played, goal difference, record; college record, conference, rank, seed;
     * the rest rank, seed, record, points, win %, games behind.
     */
    fun columns(league: String, table: SportsLeagueTable, limit: Int = 4): List<SportsTableColumn> {
        val order = when {
            league.startsWith("soccer-") -> listOf("rank", "points", "gamesPlayed", "pointDifferential", "overall")
            "college" in league -> listOf("overall", "vs. Conf.", "rank", "playoffSeed", "winPercent")
            else -> listOf("rank", "playoffSeed", "overall", "championshipPts", "points", "winPercent", "gamesBehind")
        }
        return order.mapNotNull { key -> table.columns.firstOrNull { it.key == key } }.take(limit)
    }

    /** A value as shown: a rank or seed of 0 means none; a missing value is a dash. */
    fun value(row: SportsTableRow, key: String): String {
        val value = row.values[key]
        return if ((key == "rank" || key == "playoffSeed") && value == "0") "–" else value ?: "–"
    }

    /** The table's leader: rank 1, else seed 1. */
    fun leader(table: SportsLeagueTable): SportsTableRow? =
        table.rows.firstOrNull { it.values["rank"] == "1" || it.values["playoffSeed"] == "1" }

    /** "LEADING ENTRY · RANK 1" or "… SEED 1". */
    fun leaderEyebrow(row: SportsTableRow): String = "LEADING ENTRY · ${if (row.values["rank"] == "1") "RANK 1" else "SEED 1"}"

    /** "41 points · 12-3 record": championship points, else table points, and the record. */
    fun leaderSummary(row: SportsTableRow, table: SportsLeagueTable): String = listOfNotNull(
        row.values["championshipPts"]?.let { "$it points" }
            ?: row.values["points"]?.takeIf { table.columns.any { c -> c.key == "points" } }?.let { "$it points" },
        row.values["overall"]?.let { "$it record" },
    ).joinToString(" · ")

    /** The group picker's name: college tables are conferences. */
    fun groupLabel(league: String): String = if ("college" in league) "Conference" else "Table"

    /**
     * A season record for a hub card's side that has none (Expo `participantWithStanding`): the
     * table row of the same team id (a provider-prefixed id matches on its last part), its
     * `overall`, else wins–losses[–ties] when they are whole numbers. Table position is never used.
     */
    fun record(team: SportsTeam, tables: List<SportsLeagueTable>): String? {
        if (!team.record.isNullOrEmpty() || team.id.isEmpty()) return null
        val id = team.id.substringAfterLast(':')
        val row = tables.asSequence().flatMap { it.rows }.firstOrNull { it.kind == "team" && (it.id == team.id || it.id == id) } ?: return null
        row.values["overall"]?.trim()?.ifEmpty { null }?.let { return it }
        val wins = row.values["wins"]?.takeIf(NUMBER::matches) ?: return null
        val losses = row.values["losses"]?.takeIf(NUMBER::matches) ?: return null
        return listOfNotNull(wins, losses, row.values["ties"]?.takeIf(NUMBER::matches)).joinToString("–")
    }

    /** The game with each side's record filled from [tables] where the scoreboard sent none. */
    fun withRecords(game: SportsGame, tables: List<SportsLeagueTable>): SportsGame {
        val away = record(game.awayTeam, tables)
        val home = record(game.homeTeam, tables)
        if (away == null && home == null) return game
        return game.copy(
            awayTeam = away?.let { game.awayTeam.copy(record = it) } ?: game.awayTeam,
            homeTeam = home?.let { game.homeTeam.copy(record = it) } ?: game.homeTeam,
        )
    }
}

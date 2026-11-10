package com.strmr.tv.sports

/** A choice of the hub's Sport or League filter. [sport] is the league's sport family. */
data class SportsFilterOption(val id: String, val label: String, val sport: String? = null)

/** What a hub card is, for the Sport and League filters: its sport family, league, and college flag. */
data class SportsFilterSubject(val sport: String?, val league: String, val college: Boolean = false)

/**
 * The hub's Sport and League filters (Expo `SportsHubTV`'s `SPORTS`, its league list and the
 * filter predicate, with `prioritizeSportsHub`'s order): which sports and leagues can be chosen,
 * pinned sports and followed competitions first, and which cards a choice keeps.
 */
object SportsHubFilters {
    const val ALL = "all"
    const val COLLEGE = "college"
    const val MOTORSPORT = "motorsport"

    /** Every sport in Expo's order; only those with a league turned on are offered. */
    val sports = listOf(
        "american-football" to "Football", "basketball" to "Basketball", "baseball" to "Baseball",
        "australian-football" to "Australian Football", "lacrosse" to "Lacrosse", "volleyball" to "Volleyball",
        "field-hockey" to "Field Hockey", "water-polo" to "Water Polo", "softball" to "Softball", "golf" to "Golf",
        "cricket" to "Cricket", "boxing" to "Boxing", "ice-hockey" to "Hockey", "soccer" to "Soccer", "tennis" to "Tennis",
        "mma" to "MMA", "rugby-union" to "Rugby Union", "rugby-league" to "Rugby League", MOTORSPORT to "Motorsport",
        SportsCycling.SPORT to "Cycling", COLLEGE to "College",
    )

    fun sportLabel(id: String): String = if (id == ALL) "All Sports" else sports.firstOrNull { it.first == id }?.second ?: id

    /** A league's sport family: the catalog's, else the feed sport's name for it. */
    fun family(league: SportsLeague): String? = league.applicationSport?.ifEmpty { null } ?: familyOf(league.sport)

    /** The Expo family for a feed sport name (football, hockey, racing, rugby…). */
    fun familyOf(sport: String?): String? = when (sport) {
        null, "" -> null
        "football" -> "american-football"
        "hockey" -> "ice-hockey"
        "racing" -> MOTORSPORT
        "rugby" -> "rugby-union"
        else -> sport
    }

    /** The sports with a usable league: pinned ones first in pin order, then Expo's order. */
    fun sportOptions(leagues: Collection<SportsLeague>, pinned: List<String>): List<SportsFilterOption> {
        val usable = leagues.filter { it.isUsable }
        val present = usable.mapNotNull(::family).toMutableSet()
        if (usable.any { it.college == true }) present += COLLEGE
        val offered = sports.filter { it.first in present }.map { SportsFilterOption(it.first, it.second) }
        val pinIndex = { option: SportsFilterOption -> pinned.indexOf(option.id).let { if (it < 0) Int.MAX_VALUE else it } }
        return offered.sortedBy(pinIndex) // stable: Expo's order among the unpinned
    }

    /**
     * The leagues of [sport] (all sports: every one): team competitions in follow order (followed,
     * then pinned sports, then the catalog's order), then racing series, then the cycling feed's
     * races (by their follow id, once each).
     */
    fun leagueOptions(
        sport: String,
        catalog: List<SportsLeague>,
        cyclingRaces: List<SportsCyclingRace>,
        followed: List<String>,
        pinned: List<String>,
    ): List<SportsFilterOption> {
        val usable = catalog.filter { it.isUsable }
        fun inSport(league: SportsLeague, family: String?) =
            sport == ALL || (if (sport == COLLEGE) league.college == true else family == sport)
        val teams = usable.filter { !it.isRacing && family(it) != SportsCycling.SPORT && inSport(it, family(it)) }
            .sortedBy { rank(it, followed, pinned) }
            .map { SportsFilterOption(it.id, it.label, family(it)) }
        val racing = if (sport == ALL || sport == MOTORSPORT) {
            usable.filter { it.isRacing }.map { SportsFilterOption(it.id, it.label, MOTORSPORT) }
        } else emptyList()
        val cycling = if ((sport == ALL || sport == SportsCycling.SPORT) && usable.any { family(it) == SportsCycling.SPORT }) {
            cyclingRaces.distinctBy { it.followId }.map { SportsFilterOption(it.followId, it.name, SportsCycling.SPORT) }
        } else emptyList()
        return (teams + racing + cycling).distinctBy { it.id }
    }

    /** A competition's place in follow order (Expo `prioritizeSportsHub`). */
    fun rank(league: SportsLeague, followed: List<String>, pinned: List<String>): Int {
        val follow = followed.indexOf(league.followId)
        if (follow >= 0) return follow
        val pin = family(league)?.let(pinned::indexOf) ?: -1
        return if (pin >= 0) followed.size + pin else Int.MAX_VALUE
    }

    /** Whether a card passes the chosen [sport] and [league] (each [ALL] for any). */
    fun matches(subject: SportsFilterSubject, sport: String, league: String): Boolean {
        val sportOk = sport == ALL || (if (sport == COLLEGE) subject.college else subject.sport == sport)
        return sportOk && (league == ALL || league == subject.league)
    }

    /**
     * Whether the hub offers "Competition Details" for the choice (Expo: motorsport, cycling, or
     * a team competition): a league's standings and games, the race weekends and championships,
     * or the cycling calendar. A racing series chosen under All Sports opens the racing view too.
     */
    fun hasDetails(sport: String, league: String, catalog: Map<String, SportsLeague>): Boolean {
        if (sport == MOTORSPORT || sport == SportsCycling.SPORT) return true
        val chosen = catalog[league] ?: return false
        return chosen.isRacing || family(chosen) != SportsCycling.SPORT
    }
}

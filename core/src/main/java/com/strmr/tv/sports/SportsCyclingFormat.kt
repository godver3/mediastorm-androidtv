package com.strmr.tv.sports

/** A ranked line of a cycling card: position, name and gap (or time, or DNF…). */
data class SportsCyclingCardRow(val rank: Int, val name: String, val value: String?)

/**
 * Every cycling text the hub card and the stage page show (Expo `SportsHubShowcase` cycling card,
 * `SportsCyclingDetailTV` and `SportsCyclingHub`).
 */
object SportsCyclingFormat {
    /** The card's heading. */
    fun cardLabel(race: SportsCyclingRace): String = if (race.scheduleOnly) "Cycling · Published schedule" else "Cycling"

    /** The card's status pill: REST DAY, CANCELED, SCHEDULE (dates only), else the lifecycle's. */
    fun cardPill(listing: SportsCyclingListing): String = when {
        listing.restDay -> "REST DAY"
        listing.stage?.cancelled == true -> SportsLifecycle.CANCELED.pill
        listing.race.scheduleOnly -> "SCHEDULE"
        else -> listing.lifecycle.pill
    }

    /** "Rest day", the stage's name, or "Race schedule" when the day has no stage. */
    fun stageTitle(listing: SportsCyclingListing): String =
        if (listing.restDay) "Rest day" else listing.stage?.name?.ifEmpty { null } ?: "Race schedule"

    /** Under the race's name: country and class for a calendar listing, else the stage. */
    fun cardSubtitle(listing: SportsCyclingListing): String =
        if (listing.race.calendarOnly) listOfNotNull(listing.race.country, listing.race.classification).filter { it.isNotEmpty() }.joinToString(" · ")
        else stageTitle(listing)

    /**
     * How the day stands: "Racing resumes Jul 14, 2026" on a rest day, a calendar listing's dates,
     * else the stage's distance and terrain (terrain left out when the title says it), else its date.
     */
    fun cardProgress(listing: SportsCyclingListing): String {
        val stage = listing.stage
        if (listing.restDay) return "Racing resumes ${SportsCycling.date(listing.nextStage?.date)}"
        if (listing.race.calendarOnly) return SportsCycling.dateRange(listing.race)
        val title = stageTitle(listing).lowercase()
        val metadata = listOfNotNull(
            stage?.distance?.ifEmpty { null },
            stage?.terrain?.ifEmpty { null }?.takeUnless { title.contains(it.lowercase()) },
        ).joinToString(" · ")
        return metadata.ifEmpty { SportsCycling.date(stage?.date) }
    }

    /** The top two ranked finishers; none with Hide Scores on. */
    fun cardRows(listing: SportsCyclingListing, spoilers: Boolean): List<SportsCyclingCardRow> {
        if (spoilers) return emptyList()
        return listing.stage?.results?.rows.orEmpty().filter { it.rank != null }.sortedBy { it.rank }.take(2)
            .map { SportsCyclingCardRow(it.rank!!, it.name, it.gap ?: it.time ?: it.result) }
    }

    /** What the card says when it lists nobody. */
    fun cardEmpty(listing: SportsCyclingListing, spoilers: Boolean): String = when {
        listing.restDay -> "No racing scheduled today"
        listing.stage?.cancelled == true -> "Stage canceled"
        listing.race.scheduleOnly -> "Start time TBC · Find streams in event details"
        listing.lifecycle == SportsLifecycle.SCHEDULED -> SportsCycling.date(listing.stage?.date)
        spoilers -> "Hide Scores is on"
        else -> "Results unavailable"
    }

    /** The stage picker's line: "Stage 21 · Jul 26, 2026". */
    fun stageOption(stage: SportsCyclingStage): String = "${stage.name} · ${SportsCycling.date(stage.date)}"

    /** "Jul 26, 2026 · Thoiry → Paris · 89 km · Flat". */
    fun stageLine(stage: SportsCyclingStage): String =
        listOf(SportsCycling.date(stage.date), stage.route.ifEmpty { null }, stage.distance?.ifEmpty { null }, stage.terrain?.ifEmpty { null })
            .filterNotNull().joinToString(" · ")

    /** The page's state line for the shown classification. */
    fun stageState(race: SportsCyclingRace, stage: SportsCyclingStage, classification: SportsCyclingResults?): String = when {
        stage.cancelled -> "Cancelled"
        classification != null && classification.rows.isNotEmpty() -> if (classification.stale) "Saved classification" else "Results"
        race.scheduleOnly -> "Published schedule"
        stage.status == "scheduled" -> "Scheduled"
        else -> "Classification not available"
    }

    /** The first tab: a stage race's "Stage Overview", else "Race Overview". */
    fun overviewTab(race: SportsCyclingRace): String =
        if (race.scheduleOnly || race.oneDay || race.championship) "Race Overview" else "Stage Overview"

    /** The results panel's title. */
    fun resultsTitle(race: SportsCyclingRace, overall: Boolean, teams: Boolean): String = when {
        overall && !race.oneDay -> "General Classification"
        race.oneDay || race.championship -> "Race Classification"
        teams -> "Team Results"
        else -> "Stage Results"
    }

    /** When there is no classification to list: title and message (Expo's empty notice). */
    fun emptyNotice(race: SportsCyclingRace, stage: SportsCyclingStage, classification: SportsCyclingResults?): Pair<String, String> {
        val event = if (race.oneDay || race.championship) "Race" else "Stage"
        val title = when {
            stage.cancelled -> "$event cancelled"
            stage.status == "scheduled" -> "$event scheduled"
            else -> "Classification not available"
        }
        val message = when {
            stage.cancelled -> "The organizer reports this event as cancelled."
            race.captured -> "This capture does not contain the selected classification. Check the organizer for current results."
            classification?.state == "unavailable" && !classification.reason.isNullOrEmpty() -> classification.reason
            else -> "The selected classification is not available in this feed."
        }
        return title to message
    }

    /** A rider's second line: team, nationality, bib. */
    fun riderLine(result: SportsCyclingResult): String =
        listOfNotNull(result.team?.ifEmpty { null }, result.nationality?.ifEmpty { null }, result.bib?.let { "Bib $it" }).joinToString(" · ")

    /** The leaders panel's value: the winner's time, the others' gap. */
    fun leaderValue(result: SportsCyclingResult, gapFirst: Boolean): String? =
        if (gapFirst || result.rank != 1) result.gap ?: result.time else result.time ?: result.gap

    /** "ASO race center · Retrieved Sep 9, 2026 · Provider update Aug 17, 2026". */
    fun sourceLine(evidence: SportsCyclingEvidence?): String? {
        evidence ?: return null
        return listOfNotNull(
            evidence.provider.ifEmpty { "Organizer" },
            "Retrieved ${SportsCycling.date(evidence.observedAt)}",
            evidence.updatedAt?.let { "Provider update ${SportsCycling.date(it)}" },
        ).joinToString(" · ")
    }
}

package com.strmr.mobile.ui.shell

import com.strmr.tv.api.DetailsTarget
import com.strmr.tv.api.LibraryEntry
import com.strmr.tv.api.PersonRef
import com.strmr.tv.home.BrowseList

/** A page pushed over a section's root, in that section's own back stack. */
sealed interface Route {
    data class Details(val target: DetailsTarget, val autoplay: Boolean = false) : Route
    data class Grid(val list: BrowseList, val query: Map<String, String> = emptyMap()) : Route
    data class Person(val person: PersonRef) : Route
    data class LibraryShow(val entry: LibraryEntry) : Route
    /** A section opened from More (phones), drawn as a page with a back arrow. */
    data class OpenSection(val section: Section) : Route
    data class SportsGame(val id: String) : Route
    data class SportsRace(val weekendId: String, val sessionId: String? = null) : Route
    data class SportsCycling(val raceId: String, val stageId: String? = null) : Route
    data class SportsCompetition(val sport: String, val league: String) : Route
    data object SportsPreferences : Route
    /** A settings sub page, by key (each settings file owns its keys). */
    data class SettingsPage(val key: String, val title: String) : Route
    data class RecordingDetail(val id: String) : Route
    data object RecordingRules : Route
}

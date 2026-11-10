package com.strmr.mobile.ui.shell

import androidx.compose.runtime.Composable
import com.strmr.mobile.ui.calendar.CalendarScreen
import com.strmr.mobile.ui.details.DetailsScreen
import com.strmr.mobile.ui.downloads.DownloadsScreen
import com.strmr.mobile.ui.home.HomeScreen
import com.strmr.mobile.ui.library.LibraryShowScreen
import com.strmr.mobile.ui.lists.ListGridScreen
import com.strmr.mobile.ui.lists.ListsScreen
import com.strmr.mobile.ui.live.LiveScreen
import com.strmr.mobile.ui.person.PersonScreen
import com.strmr.mobile.ui.profiles.ProfilesScreen
import com.strmr.mobile.ui.recordings.RecordingDetailScreen
import com.strmr.mobile.ui.recordings.RecordingRulesScreen
import com.strmr.mobile.ui.recordings.RecordingsScreen
import com.strmr.mobile.ui.search.SearchScreen
import com.strmr.mobile.ui.settings.SettingsPageScreen
import com.strmr.mobile.ui.settings.SettingsScreen
import com.strmr.mobile.ui.sports.SportsCompetitionScreen
import com.strmr.mobile.ui.sports.cycling.SportsCyclingScreen
import com.strmr.mobile.ui.sports.game.SportsGameScreen
import com.strmr.mobile.ui.sports.SportsPreferencesScreen
import com.strmr.mobile.ui.sports.race.SportsRaceScreen
import com.strmr.mobile.ui.sports.SportsScreen
import com.strmr.mobile.ui.watchlist.WatchlistScreen

/**
 * A section's root screen. [asPage] is true when it was opened from More, so it shows a back
 * arrow in its top bar.
 */
@Composable
fun SectionRoot(section: Section, more: List<Section>, asPage: Boolean = false) {
    when (section) {
        Section.HOME -> HomeScreen()
        Section.WATCHLIST -> WatchlistScreen(asPage)
        Section.SEARCH -> SearchScreen(asPage)
        Section.LISTS -> ListsScreen(asPage)
        Section.CALENDAR -> CalendarScreen(asPage)
        Section.RECORDINGS -> RecordingsScreen(asPage)
        Section.LIVE -> LiveScreen(asPage)
        Section.SPORTS -> SportsScreen(asPage)
        Section.PROFILES -> ProfilesScreen(asPage)
        Section.DOWNLOADS -> DownloadsScreen(asPage)
        Section.SETTINGS -> SettingsScreen(asPage)
        Section.MORE -> MoreScreen(more)
    }
}

@Composable
fun RouteScreen(route: Route) {
    when (route) {
        is Route.Details -> DetailsScreen(route.target, route.autoplay)
        is Route.Grid -> ListGridScreen(route.list, route.query)
        is Route.Person -> PersonScreen(route.person)
        is Route.LibraryShow -> LibraryShowScreen(route.entry)
        is Route.OpenSection -> SectionRoot(route.section, emptyList(), asPage = true)
        is Route.SportsGame -> SportsGameScreen(route.id)
        is Route.SportsRace -> SportsRaceScreen(route.weekendId, route.sessionId)
        is Route.SportsCycling -> SportsCyclingScreen(route.raceId, route.stageId)
        is Route.SportsCompetition -> SportsCompetitionScreen(route.sport, route.league)
        Route.SportsPreferences -> SportsPreferencesScreen()
        is Route.SettingsPage -> SettingsPageScreen(route.key, route.title)
        is Route.RecordingDetail -> RecordingDetailScreen(route.id)
        Route.RecordingRules -> RecordingRulesScreen()
    }
}

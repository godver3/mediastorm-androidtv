package com.strmr.tv.sports

import com.strmr.tv.ui.sports.SportsHubItem
import com.strmr.tv.ui.sports.SportsMyTeams
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

/** Followed teams (`teamIds`, Expo's `"<league>:<team id>"`), their preference updates and the hub's My Teams filter. */
class SportsTeamFollowsTest {
    // SportsGameFixtures' NFL game: Chicago Bears (3) host New England Patriots (17).
    private val game = SportsGameFixtures.game()

    @Test fun matchesExactAndOnTheLastIdPartWithinTheLeague() {
        assertTrue(SportsTeamFollows.isFollowed(listOf("nfl:3"), "nfl", "3"))
        // A stored provider-prefixed id and an unprefixed team id, and the other way round.
        assertTrue(SportsTeamFollows.isFollowed(listOf("nfl:espn:3"), "nfl", "3"))
        assertTrue(SportsTeamFollows.isFollowed(listOf("nfl:3"), "nfl", "espn:3"))
        assertFalse(SportsTeamFollows.isFollowed(listOf("nba:3"), "nfl", "3"))
        assertFalse(SportsTeamFollows.isFollowed(listOf("nfl:33"), "nfl", "3"))
        assertFalse(SportsTeamFollows.isFollowed(listOf("nfl:"), "nfl", ""))
        assertEquals("nfl:3", SportsTeamFollows.id("nfl", "3"))
    }

    @Test fun findsGamesAFollowedTeamPlays() {
        assertTrue(SportsTeamFollows.plays(listOf("nfl:17"), game))
        assertTrue(SportsTeamFollows.plays(listOf("nhl:9", "nfl:3"), game))
        assertFalse(SportsTeamFollows.plays(emptyList(), game))
        assertFalse(SportsTeamFollows.plays(listOf("nba:3"), game))
    }

    @Test fun updatesTeamsAndKeepsEveryOtherField() {
        val prefs = SportsPreferences(
            pinnedSportIds = listOf("soccer"), competitionIds = listOf("nfl", "future:race"), teamIds = listOf("future:team"),
            athleteIds = listOf("a1"), schools = listOf(SportsSchoolFollow("school-1", "all")),
        )
        val followed = SportsTeamFollows.update(prefs, "nfl", "3", follow = true)
        assertEquals(listOf("future:team", "nfl:3"), followed.teamIds)
        assertEquals(prefs.copy(teamIds = followed.teamIds), followed)
        // Already followed under another form: unchanged; unfollowing drops every matching form.
        assertSame(followed, SportsTeamFollows.update(followed, "nfl", "espn:3", follow = true))
        val both = followed.copy(teamIds = followed.teamIds + "nfl:espn:3")
        assertEquals(listOf("future:team"), SportsTeamFollows.update(both, "nfl", "3", follow = false).teamIds)
        assertSame(prefs, SportsTeamFollows.update(prefs, "nfl", "3", follow = false))
    }

    @Test fun namesFollowedTeamsAndListsALeaguesTeams() {
        val standings = SportsLeagueStandings(
            league = "nfl", state = "available",
            groups = listOf(
                SportsLeagueTable("afc", rows = listOf(SportsTableRow("17", "New England Patriots"), SportsTableRow("12", "Kansas City Chiefs"))),
                SportsLeagueTable("all", rows = listOf(SportsTableRow("12", "Kansas City Chiefs"), SportsTableRow("x", "Driver", kind = "athlete"))),
            ),
        )
        assertEquals(listOf("Kansas City Chiefs", "New England Patriots"), SportsTeamFollows.teams(standings.groups).map { it.name })
        val tables = mapOf("nfl" to standings.groups)
        assertEquals("Kansas City Chiefs", SportsTeamFollows.name("nfl:espn:12", tables, emptyList()))
        assertEquals("Chicago Bears", SportsTeamFollows.name("nfl:3", tables, listOf(game)))
        assertEquals("nhl:9", SportsTeamFollows.name("nhl:9", tables, listOf(game)))
    }

    @Test fun myTeamsKeepsOnlyGamesOfFollowedTeams() {
        val other = SportsGameFixtures.game(""""id":"2","homeTeam":{"id":"8","name":"Detroit Lions"},"awayTeam":{"id":"9","name":"Green Bay Packers"}""")
        val items: List<SportsHubItem> = listOf(SportsHubItem.Match(game), SportsHubItem.Match(other))
        assertEquals(listOf(game), SportsMyTeams.games(items, listOf("nfl:espn:17")).map { (it as SportsHubItem.Match).game })
        assertTrue(SportsMyTeams.games(items, emptyList()).isEmpty())
    }

    @Test fun teamLeaguesLeaveOutIndividualSports() {
        fun league(json: String) = SportsGameFixtures.league(json)
        assertTrue(SportsTeamFollows.isTeamLeague(league("""{"id":"nfl","enabled":true,"sport":"football"}""")))
        assertFalse(SportsTeamFollows.isTeamLeague(league("""{"id":"atp","enabled":true,"sport":"tennis"}""")))
        assertFalse(SportsTeamFollows.isTeamLeague(league("""{"id":"ufc","enabled":true,"sport":"mma"}""")))
        assertFalse(SportsTeamFollows.isTeamLeague(league("""{"id":"x","enabled":true,"sport":"soccer","supportsTeams":false}""")))
    }
}

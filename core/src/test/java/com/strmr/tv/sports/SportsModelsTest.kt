package com.strmr.tv.sports

import com.strmr.tv.api.ApiJson
import java.time.Instant
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.encodeToJsonElement
import kotlinx.serialization.json.jsonObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The Sports hub's reading of `/api/sports` answers, after the Expo normaliser
 * (`features/sports/sports-backend-source.ts`) and follow ids (`sports-follows.ts`). The same
 * cases as the tvOS `SportsModelTests`.
 */
class SportsModelsTest {
    private fun game(extra: String = "", status: String = "final", detail: String = "Final"): SportsGame = ApiJson.decodeFromString(
        """{"id":"401873010","league":"nfl","startTime":"2026-10-23T00:15:00Z","endTime":"0001-01-01T00:00:00Z",
           "status":"$status","statusDetail":"$detail",
           "homeTeam":{"id":"3","name":"Chicago Bears","abbreviation":"CHI","color":"0b1c3a","score":"30","winner":true,
                       "logoUrl":"https://a.espncdn.com/i/teamlogos/nfl/500/scoreboard/chi.png"},
           "awayTeam":{"id":"17","name":"New England Patriots","abbreviation":"NE","score":"24","logoUrl":"http://insecure.example/ne.png"}
           $extra}""",
    )

    @Test fun decodesAScoreboardGame() {
        val g = game()
        assertEquals(SportsLifecycle.FINAL, g.lifecycle)
        assertTrue(g.isMatchup)
        assertTrue(g.showsScore)
        assertEquals(Instant.parse("2026-10-23T00:15:00Z"), g.start)
        assertNull("Go's zero time is no end", g.end)
        assertNotNull(g.homeTeam.logo)
        assertNull("only https logos load", g.awayTeam.logo)
    }

    @Test fun lifecycleFromStatusText() {
        assertEquals(SportsLifecycle.POSTPONED, SportsLifecycle.of("scheduled", "Postponed"))
        assertEquals(SportsLifecycle.CANCELED, SportsLifecycle.of("scheduled", "Canceled"))
        assertEquals(SportsLifecycle.DELAYED, SportsLifecycle.of("live", "Rain Delay"))
        assertEquals(SportsLifecycle.LIVE, SportsLifecycle.of("live", "Q3 4:12"))
        assertEquals(SportsLifecycle.SCHEDULED, SportsLifecycle.of("pre", "7:05 PM"))
    }

    @Test fun noScoreBeforeTheStart() {
        assertFalse(game(status = "scheduled", detail = "7:05 PM").showsScore)
    }

    @Test fun fightCardShowsItsName() {
        val card = game(""","combat":{"cardName":"UFC 333: Volkanovski vs. Evloev"}""")
        assertFalse(card.isMatchup)
        assertEquals("UFC 333: Volkanovski vs. Evloev", card.displayTitle)
    }

    @Test fun followIdsMatchExpo() {
        assertEquals("premier-league", SportsLeague("soccer-eng.1", enabled = true).followId)
        assertEquals("formula-one", SportsLeague("f1", enabled = true).followId)
        assertEquals("nhl", SportsLeague("nhl", enabled = true).followId)
    }

    @Test fun usableLeagues() {
        assertTrue("older servers send no status", SportsLeague("nba", enabled = true).isUsable)
        assertTrue(SportsLeague("nba", enabled = true, implementationStatus = "limited", capabilities = listOf("schedule")).isUsable)
        assertFalse(SportsLeague("nba", enabled = true, implementationStatus = "planned").isUsable)
        assertFalse(SportsLeague("nba", enabled = true, capabilities = listOf("standings")).isUsable)
        assertFalse(SportsLeague("nba", enabled = false).isUsable)
    }

    /** The server rejects a preferences document missing any field, so defaults are sent too. */
    @Test fun preferencesEncodeEveryField() {
        val json = Json(from = ApiJson) { encodeDefaults = true }
        val body = json.encodeToJsonElement(SportsPreferencesEnvelope(revision = 2)).jsonObject
        val prefs = body["preferences"]!!.jsonObject
        assertEquals(setOf("version", "pinnedSportIds", "competitionIds", "teamIds", "athleteIds", "schools"), prefs.keys)
        assertEquals("2", body["revision"].toString())
    }
}

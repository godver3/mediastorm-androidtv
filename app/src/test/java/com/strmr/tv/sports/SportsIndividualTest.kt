package com.strmr.tv.sports

import com.strmr.tv.api.ApiJson
import com.strmr.tv.ui.sports.cards.SportsCardVariant
import com.strmr.tv.ui.sports.cards.SportsFieldLeaders
import com.strmr.tv.ui.sports.cards.monogram
import com.strmr.tv.ui.sports.game.SportsGamePage
import com.strmr.tv.ui.sports.game.SportsKind
import com.strmr.tv.ui.sports.game.combat.SportsCombatRules
import com.strmr.tv.ui.sports.game.cricket.SportsCricket
import com.strmr.tv.ui.sports.game.rugby.SportsRugby
import com.strmr.tv.ui.sports.game.soccer.SportsSoccerFormation
import com.strmr.tv.ui.sports.game.soccer.SportsSoccerStatRules
import com.strmr.tv.ui.sports.game.soccer.SportsSoccerTimeline
import com.strmr.tv.ui.sports.game.tennis.SportsTennis
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The individual and other sports: golf's leaderboard, cricket's innings and scorecards, fight
 * cards and profiles (Go `SportsGolfEntry`, `SportsCricketInnings`, `SportsCricketScorecard`,
 * `SportsCombat`), and the rules ported from Expo `sports-tennis.ts`, `sports-combat-card.ts`,
 * `sports-rugby.ts`, `SportsSoccerPitch` and `SportsCricketOverview` (the tvOS
 * `SportsIndividualTests` cases).
 */
class SportsIndividualTest {
    private val f = SportsGameFixtures

    private fun page(league: String, sport: String, overrides: String = "", detail: String? = null): SportsGamePage =
        f.page(listOf(""""league":"$league","sport":"$sport"""", overrides).filter { it.isNotEmpty() }.joinToString(","), detail)

    private fun team(json: String) = ApiJson.decodeFromString(SportsTeam.serializer(), json)

    private fun play(id: String, clock: String?, title: String) = SportsPlay(id = id, clock = clock, title = title)

    // Decoding

    @Test
    fun decodesGolfCricketAndCombatDetail() {
        val detail = page("pga", "golf", detail = """{
            "leaderboard":[
              {"id":"9478","name":"Scottie Scheffler","score":"-19","rounds":[
                {"round":1,"strokes":"70","toPar":"-2","holes":[{"hole":1,"strokes":"4","toPar":"E"},{"hole":"x"}]},
                {"round":2,"strokes":68}]},
              {"name":"no id: skipped"}],
            "innings":[{"teamId":"1298769","number":1,"runs":155,"wickets":8,"overs":20,"description":"complete"},
                       {"teamId":"335970","number":"bad"}],
            "cricketScorecards":[{"kind":"bowling","innings":2,"teamName":"Gujarat Titans","players":[
                {"id":"940973","name":"Mohammed Siraj","stats":[{"label":"Wickets","value":"1"}]}]},{"kind":"fielding","innings":1}],
            "fighterProfiles":[{"id":"1","teamId":"1","name":"Jean Silva","category":"Fighter profile",
                                "stats":[{"label":"Reach","value":"70.5\""}]}]}""").detail
        assertEquals(listOf("Scottie Scheffler"), detail.leaderboard.map { it.name })
        assertEquals(listOf("70", "68"), detail.leaderboard[0].rounds.map { it.strokes })
        assertEquals(1, detail.leaderboard[0].rounds[0].holes.size)
        assertEquals(2, detail.leaderboard[0].latestRound)
        assertEquals(1, detail.innings.size)
        assertEquals("20", detail.innings[0].overs)
        assertEquals(listOf(SportsCricketScorecard.Kind.BOWLING), detail.cricketScorecards.map { it.kind })
        assertEquals("1", detail.cricketScorecards[0].players[0].value("Wickets"))
        assertEquals("70.5\"", detail.fighterProfiles[0].value("Reach"))
    }

    @Test
    fun decodesFightCardAndParticipants() {
        val game = f.game(""""league":"ufc","sport":"mma","combat":{
            "cardName":"Noche UFC","division":"Featherweight","scheduledRounds":5,"bouts":[
              {"id":"b1","away":{"id":"1","name":"Rodrigo Sezinando"},"home":{"id":"2","name":"Daniil Donchenko","winner":true},
               "division":"Welterweight","status":"final","period":"Round 1","clock":"4:27"},
              {"id":"b2","away":{"id":"3"}}]},
            "participants":[{"id":9478,"name":"Scottie Scheffler","score":"-19"},{"id":"x"},"bad"]""")
        assertEquals(5, game.combat?.scheduledRounds)
        assertEquals(listOf("b1"), game.combat?.bouts?.map { it.id })
        assertEquals(SportsLifecycle.FINAL, game.combat?.bouts?.get(0)?.lifecycle)
        assertEquals(listOf("9478"), game.participants?.map { it.id })
        assertEquals("-19", game.participants?.firstOrNull()?.score)
    }

    // Tennis

    @Test
    fun tennisContextTiebreaksAndLatestSet() {
        val titled = f.game(""""league":"atp","title":"US Open: Chak Lam Coleman Wong vs Chris Rodesch",
            "awayTeam":{"id":"1","name":"Chak Lam Coleman Wong"},"homeTeam":{"id":"2","name":"Chris Rodesch"}""")
        assertEquals("US Open", SportsTennis.eventContext(titled))
        assertEquals("US Open · Men's Singles", SportsTennis.eventContext(f.game(""""league":"atp","eventContext":" US Open · Men's Singles """")))
        assertNull(SportsTennis.eventContext(f.game()))
        assertEquals("¹⁰", SportsTennis.tiebreak(10))
        assertEquals("⁰", SportsTennis.tiebreak(0))
        assertEquals("", SportsTennis.tiebreak(null))
        val periods = page("atp", "tennis", detail = """{"periods":[
            {"label":"Set 1","away":"6","home":"7","awayTiebreak":5,"homeTiebreak":7,"homeWinner":true},
            {"label":"Set 2","away":"3","home":"2"},{"label":"Set 3","away":"","home":""}]}""").detail.periods
        assertEquals(1, SportsTennis.latestSet(periods))
        assertEquals("6⁵", SportsTennis.games(periods[0], away = true))
        assertEquals("–", SportsTennis.games(periods[2], away = false))
        assertTrue(SportsTennis.won(periods[0], away = false))
        assertEquals("A Smith\nB Jones [3]", SportsTennis.name(team("""{"id":"1","name":"A Smith / B Jones","seed":3}""")))
    }

    // Combat

    @Test
    fun combatResultsPortraitsAndBars() {
        val final = f.game(""""league":"ufc","statusDetail":"Final","period":"Round 2","clock":"4:48",
            "awayTeam":{"id":"5145766","name":"Jean Silva"},"homeTeam":{"id":"4881999","name":"Diego Lopes","winner":true}""")
        assertEquals("Diego Lopes wins · Round 2 · 4:48", SportsCombatRules.result(final, hidden = false))
        assertEquals("Result hidden", SportsCombatRules.result(final, hidden = true))
        val live = f.game(""""league":"ufc","status":"live","statusDetail":"","period":"Round 3","clock":"–"""")
        assertEquals("Round 3", SportsCombatRules.result(live, hidden = false))
        assertEquals("https://a.espncdn.com/i/headshots/mma/players/full/4881999.png", SportsCombatRules.portrait("ufc", final.homeTeam))
        assertNull(SportsCombatRules.portrait("boxing", final.homeTeam))
        assertNull(SportsCombatRules.portrait("boxing", team("""{"id":"1","name":"X","logoUrl":"https://a.espncdn.com/i/teamlogos/x.png"}""")))
        assertEquals(105.0 to 158.0, SportsCombatRules.barValues("Control time", "1:45", "2:38"))
        assertNull(SportsCombatRules.barValues("Significant strike %", "45%", "52%"))
        assertNull(SportsCombatRules.barValues("Knockdowns", "", "0"))
    }

    // Rugby

    @Test
    fun rugbyStatusMarksAndOrder() {
        val live = page("rugby-180659", "rugby", """"status":"live","statusDetail":"64'","period":"2nd","clock":"64'"""")
        assertEquals("Second half · 64'", SportsRugby.status(live))
        assertEquals("Half time", SportsRugby.status(page("rugby-180659", "rugby", """"status":"live","statusDetail":"HT","period":"1st"""")))
        assertEquals("Full time", SportsRugby.status(page("rugby-180659", "rugby")))
        assertEquals(
            listOf("T", "PT", "C", "PG", "×", "YC", "↔"),
            listOf("try", "Penalty Try", "conversion", "penalty goal", "missed conversion", "yellow card", "substitute on").map(SportsRugby::mark),
        )
        val plays = listOf(play("a", "12'", "try"), play("b", "40+2'", "try"), play("c", "33'", "penalty goal"))
        assertEquals(listOf("b", "c", "a"), SportsRugby.latest(plays).map { it.id })
        assertEquals(listOf("a", "b", "c", "d"), SportsRugby.latest(plays + play("d", null, "try")).map { it.id })
    }

    // Soccer

    @Test
    fun soccerFormationsAndPossession() {
        assertEquals(listOf(1, 4, 2, 3, 1), SportsSoccerFormation.rows("4-2-3-1"))
        assertNull(SportsSoccerFormation.rows("4-4-3"))
        assertNull(SportsSoccerFormation.rows("4-6"))
        assertNull(SportsSoccerFormation.rows(null))
        assertEquals(19.0 to 81.0, SportsSoccerStatRules.possession("19%", "81"))
        assertNull(SportsSoccerStatRules.possession("40", "40"))
        assertEquals("83%", SportsSoccerStatRules.display("0.83", "Pass accuracy %"))
        assertEquals("12", SportsSoccerStatRules.display("12", "Shots"))
    }

    @Test
    fun soccerTimelineGoalsExcludeLatestAndRunOldestFirst() {
        val page = page("soccer-eng.1", "soccer", """"status":"live"""", """{"plays":[
            {"id":"4","clock":"66'","title":"Yellow Card"},{"id":"3","clock":"61'","title":"Goal - Header"},
            {"id":"2","clock":"52'","title":"Own Goal"},{"id":"1","clock":"29'","title":"Goal","shootout":false},
            {"id":"0","clock":"120'","title":"Penalty - Scored","shootout":true}]}""")
        assertEquals("4", SportsSoccerTimeline.latest(page)?.id)
        assertEquals(listOf("1", "2", "3"), SportsSoccerTimeline.goals(page).map { it.id })
    }

    // Cricket

    @Test
    fun cricketChaseAndLabels() {
        val page = page(
            "cricket-8048", "cricket",
            """"status":"live","statusDetail":"RCB need 36 runs in 30 balls",
               "awayTeam":{"id":"1298769","name":"Gujarat Titans"},"homeTeam":{"id":"335970","name":"Royal Challengers Bengaluru"}""",
            """{"innings":[{"teamId":"1298769","number":1,"runs":155,"wickets":8,"overs":"20"},
                           {"teamId":"335970","number":2,"runs":120,"wickets":3,"overs":"15","target":156,"batting":true}]}""",
        )
        val lines = SportsCricket.lines(page)
        assertEquals(listOf(2, 1), lines.map { it.innings.number })
        assertTrue(lines[0].current)
        assertEquals("120/3", lines[0].score)
        assertEquals("1st innings", lines[0].label)
        assertEquals("Target 156 · 36 runs needed", lines[0].chase)
        assertEquals(120.0 / 156, lines[0].progress ?: 0.0, 0.001)
        assertEquals("1st innings", lines[1].state)
        val missing = page("cricket-8048", "cricket", detail = """{"innings":[{"teamId":"1","number":3,"runs":10}]}""")
        assertEquals("Match innings 3", SportsCricket.lines(missing)[0].label)
        assertEquals("10/–", SportsCricket.lines(missing)[0].score)
    }

    @Test
    fun cricketHighlightsOnlyLatestInningsOfAFinal() {
        fun card(kind: String, innings: Int, players: String) = """{"kind":"$kind","innings":$innings,"teamName":"T","players":[$players]}"""
        fun player(id: String, name: String, label: String, value: String) =
            """{"id":"$id","name":"$name","stats":[{"label":"$label","value":"$value"}]}"""
        val detail = """{"cricketScorecards":[${card("batting", 1, player("a", "Early", "Runs", "90"))},
            ${card("batting", 2, player("b", "Kohli", "Runs", "75") + "," + player("c", "Iyer", "Runs", "32"))},
            ${card("bowling", 2, player("d", "Siraj", "Wickets", "0"))}]}"""
        val (innings, players) = SportsCricket.highlights(page("cricket-8048", "cricket", detail = detail))
        assertEquals(2, innings)
        assertEquals(listOf("Kohli"), players.map { it.player.name })
        assertEquals("runs", players.firstOrNull()?.metric)
        assertTrue(SportsCricket.highlights(page("cricket-8048", "cricket", """"status":"live"""", detail)).second.isEmpty())
    }

    // Hub card

    @Test
    fun cardVariantsAndMonograms() {
        assertEquals(SportsCardVariant.TENNIS, SportsCardVariant.of(f.game(""""league":"atp""""), SportsKind.TENNIS))
        assertEquals(SportsCardVariant.COMBAT, SportsCardVariant.of(f.game(""""league":"ufc","combat":{"cardName":"UFC"}"""), SportsKind.MMA))
        val field = f.game(""""league":"pga","homeTeam":{"id":"","name":""},"awayTeam":{"id":"","name":""},
            "participants":[{"id":"1","name":"Scottie Scheffler","score":"-19"},{"id":"2","name":"Ben Griffin"},{"id":"3","name":"Third"}]""")
        assertEquals(SportsCardVariant.FIELD, SportsCardVariant.of(field, SportsKind.GOLF))
        assertEquals(SportsCardVariant.FIELD, SportsCardVariant.of(field, SportsKind.BOXING))
        assertEquals(listOf("Scottie Scheffler", "Ben Griffin"), SportsFieldLeaders.of(field).map { it.name })
        assertEquals(SportsCardVariant.MATCHUP, SportsCardVariant.of(f.game(), SportsKind.FOOTBALL))
        fun side(abbreviation: String, name: String) = team("""{"id":"1","name":"$name","abbreviation":"$abbreviation"}""")
        assertEquals("CR", side("C. Rodesch", "Chris Rodesch").monogram)
        assertEquals("JS", side("J.J. Spaun", "J.J. Spaun").monogram)
        assertEquals("RCB", side("RCB", "Royal Challengers Bengaluru").monogram)
    }
}

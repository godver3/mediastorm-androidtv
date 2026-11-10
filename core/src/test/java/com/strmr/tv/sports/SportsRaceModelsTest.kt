package com.strmr.tv.sports

import com.strmr.tv.api.ApiJson
import com.strmr.tv.api.LossyList
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant

/**
 * The race board, sessions and MotoGP answers as the race page reads them (Expo
 * `sports-racing.ts`, `SportsRaceTable`, `SportsRaceDetailTV`), from trimmed excerpts of the
 * recorded racing fixtures.
 */
class SportsRaceModelsTest {
    /** Entries the lenient decode skipped (reported here rather than to the device log). */
    private val skipped = mutableListOf<String>()

    init {
        LossyList.report = { skipped += it }
    }

    private val board: SportsRaceBoard = ApiJson.decodeFromString(
        """{"events":[
             {"id":"f1:600057442","providerEventId":"600057442","title":"Pirelli Italian Grand Prix","league":"f1","sport":"racing",
              "eventKind":"race","startTime":"2026-09-06T13:00:00Z","status":"final","statusDetail":"Final","participants":null,
              "updatedAt":"0001-01-01T00:00:00Z","unknownField":{"nested":true},
              "circuit":{"id":"it-1922","name":"Autodromo Nazionale Monza","location":"Monza","referenceLengthMeters":5793,
                         "coordinates":[[9.2811,45.6156],[9.2869,45.6189],[9.2905,45.6249],[9.2811,45.6156]],
                         "sourceUrl":"https://example.org/it-1922.geojson","attribution":"Circuit geometry"},
              "subEvents":[
                {"id":"f1:600057442:401839101","providerEventId":"600057442","sessionId":"401839101","sessionType":"Qual",
                 "title":"Qual","league":"f1","eventKind":"race-session","startTime":"2026-09-05T14:00:00Z","status":"final",
                 "updatedAt":"2026-09-09T03:06:52.960345786Z",
                 "participants":[{"number":"10","team":"Alpine","result":"Classified","id":"5501","name":"Pierre Gasly","position":1,
                   "statistics":[{"name":"lapsCompleted","label":"Laps","value":"18"},{"name":"qual3TimeMS","label":"Q3","value":"1:21.786"}]}]},
                {"id":"f1:600057442:401839102","providerEventId":"600057442","sessionId":"401839102","sessionType":"Race",
                 "title":"Race","league":"f1","eventKind":"race-session","startTime":"2026-09-06T13:00:00Z","status":"final",
                 "participants":[
                   {"number":"12","team":"Mercedes","id":"5829","name":"Kimi Antonelli","position":1,"winner":true,
                    "statistics":[{"name":"lapsCompleted","label":"Laps","value":"53"},{"name":"totalTime","label":"Elapsed time","value":"1:51:15.281"}]},
                   {"id":"bad","name":"Broken","position":"not a number"},
                   {"number":"63","team":"Mercedes","id":"5503","name":"George Russell","position":2,
                    "statistics":[{"name":"behindTime","label":"Time behind","value":"+3.857"}]}]},
                {"id":"f1:600057442:401839098","providerEventId":"600057442","sessionId":"401839098","sessionType":"FP1",
                 "title":"FP1","league":"f1","eventKind":"race-session","startTime":"2026-09-04T10:30:00Z","status":"final","participants":[]}
              ]},
             {"id":"nascar:202508313891:202508313891","providerEventId":"202508313891","sessionId":"202508313891","sessionType":"Race",
              "title":"Race","league":"nascar","eventKind":"race-session","startTime":"2025-08-31T22:00:00Z","status":"scheduled",
              "participants":[]}],
           "leagues":[{"league":"f1","stale":false,"unavailable":false},{"league":"motogp","stale":true,"unavailable":false}]}""",
    )

    private val monza get() = board.events[0]

    @Test fun decodesTheBoardLeniently() {
        assertEquals(2, board.events.size)
        assertTrue("a null participants list is empty", monza.participants.isEmpty())
        assertNull("Go's zero time is no update", monza.updated)
        assertEquals(3, monza.subEvents.size)
        val race = monza.subEvents[1]
        assertEquals("an entry that fails to decode is skipped", listOf("5829", "5503"), race.participants.map { it.id })
        assertEquals(1, skipped.size)
        assertTrue(board.delayed)
        assertEquals(5793, monza.circuit?.referenceLengthMeters)
        assertEquals(Instant.parse("2026-09-06T13:00:00Z"), monza.start)
    }

    @Test fun resolvesWeekendsAndSessions() {
        assertEquals(monza to null, board.resolve("f1:600057442"))
        assertEquals(monza to "f1:600057442:401839101", board.resolve("f1:600057442:401839101"))
        val lone = board.events[1]
        assertTrue(lone.isSession)
        assertEquals("a lone session opens on itself", lone to lone.id, board.resolve(lone.id))
        assertNull(board.resolve("motogp:missing"))
    }

    @Test fun sessionsRunInStartOrderAndTheRaceIsTheDefault() {
        assertEquals(listOf("FP1", "Qual", "Race"), monza.sessions.map { it.sessionType })
        assertEquals("Race", monza.defaultSession?.sessionType)
        val live = monza.copy(subEvents = monza.subEvents.map { if (it.sessionType == "Qual") it.copy(status = "live") else it })
        assertEquals("a live session wins", "Qual", live.defaultSession?.sessionType)
        val practice = monza.copy(subEvents = monza.subEvents.filter { it.sessionType != "Race" })
        assertEquals("no race: the last session listed, as Expo picks it", "FP1", practice.defaultSession?.sessionType)
        val bare = monza.copy(subEvents = emptyList())
        assertTrue("a weekend listed without sessions has none to open", bare.sessions.isEmpty())
        assertNull(bare.defaultSession)
    }

    @Test fun timingLabels() {
        fun driver(vararg stats: Pair<String, String>) =
            SportsRaceDriver(id = "1", name = "A", statistics = stats.map { SportsRaceStatistic(it.first, it.first, it.second) })
        assertEquals("+3.857", SportsRaceFormat.timing(driver("behindTime" to "+3.857", "totalTime" to "1:51:19.138")))
        assertEquals("Gap", SportsRaceFormat.timingKind(driver("behindTime" to "+3.857")))
        assertEquals("2 laps behind", SportsRaceFormat.timing(driver("behindLaps" to "-2")))
        assertEquals("1 lap behind", SportsRaceFormat.timing(driver("behindLaps" to "1")))
        assertEquals("zero laps behind is no gap", "1:51:15.281", SportsRaceFormat.timing(driver("behindLaps" to "0", "totalTime" to "1:51:15.281")))
        assertEquals("Time", SportsRaceFormat.timingKind(driver("totalTime" to "1:51:15.281")))
        assertEquals("01:45.455", SportsRaceFormat.timing(driver("fastestLap" to "01:45.455")))
        assertEquals("Best lap", SportsRaceFormat.timingKind(driver("fastestLap" to "01:45.455")))
        assertNull(SportsRaceFormat.timing(driver("lapsCompleted" to "53")))
        assertEquals("DNF", SportsRaceFormat.cardValue(SportsRaceDriver(id = "2", name = "B", result = "DNF")))
        assertNull("Classified goes unsaid", SportsRaceFormat.cardValue(SportsRaceDriver(id = "3", name = "C", result = "Classified")))
    }

    @Test fun cardTitlesAsExpo() {
        assertEquals("Grand Prix of Aragon", SportsRaceFormat.cardTitle("MICHELIN® GRAND PRIX OF ARAGON "))
        assertEquals("Grand Prix of the Americas", SportsRaceFormat.cardTitle("Red Bull Grand Prix of The Americas"))
        assertEquals("Pirelli Italian Grand Prix", SportsRaceFormat.cardTitle("Pirelli Italian Grand Prix"))
        assertEquals("Formula 1 Gran Premio de Monaco", SportsRaceFormat.cardTitle("FORMULA 1 GRAN PREMIO DE MONACO"))
        assertEquals("Qatar Airways Austrian GP", SportsRaceFormat.cardTitle("QATAR AIRWAYS AUSTRIAN GP"))
    }

    @Test fun sessionStates() {
        val race = monza.subEvents[1]
        assertEquals("Results", SportsRaceFormat.sessionState(race))
        assertEquals("Live", SportsRaceFormat.sessionState(race.copy(status = "live")))
        assertEquals("Scheduled", SportsRaceFormat.sessionState(race.copy(status = "scheduled")))
        assertEquals("Cancelled", SportsRaceFormat.sessionState(race.copy(statusDetail = "Canceled")))
        assertEquals("Postponed", SportsRaceFormat.sessionState(race.copy(status = "scheduled", statusDetail = "Postponed")))
        assertEquals("Race scheduled", SportsRaceFormat.emptyNotice(race.copy(status = "scheduled")).first)
        assertTrue(SportsRaceFormat.isQualifying(monza.subEvents[0]))
        assertEquals("Pirelli Italian Grand Prix · Race", SportsRaceFormat.watchTitle(monza, race))
    }

    @Test fun circuitOutlineFitsItsBox() {
        val outline = monza.circuit?.outline()
        assertNotNull(outline)
        outline!!
        assertEquals(4, outline.points.size)
        assertTrue(outline.points.all { (x, y) -> x in 0.0..outline.width && y in 0.0..outline.height })
        assertEquals("north is up: the northernmost point has y 0", 0.0, outline.points[2].second, 1e-9)
        assertNull("two points are no outline", SportsCircuit(coordinates = listOf(listOf(1.0, 1.0), listOf(2.0, 2.0))).outline())
        assertNull("latitude past the pole", SportsCircuit(coordinates = listOf(listOf(1.0, 91.0), listOf(2.0, 2.0), listOf(3.0, 1.0))).outline())
    }

    @Test fun decodesMotoGPStandingsAndCircuit() {
        val standings: SportsMotoGPEnrichment = ApiJson.decodeFromString(
            """{"state":"available","season":2026,"source":"MotoGP","sourceUrl":"https://www.motogp.com/en/world-standing",
               "standings":[{"riderId":"5cbd7eb6","name":"Jorge Martin","number":89,"team":"Aprilia Racing","constructor":"Aprilia",
                             "position":1,"points":256,"wins":1,"podiums":6},
                            {"riderId":"x","name":"Half Points","points":12.5}]}""",
        )
        assertEquals(2026, standings.season)
        assertEquals("Aprilia", standings.standings[0].manufacturer)
        assertEquals(12.5, standings.standings[1].points!!, 0.0)
        val guide: SportsMotoGPEnrichment = ApiJson.decodeFromString(
            """{"state":"stale","season":2026,"source":"MotoGP","sourceUrl":"","circuit":{"id":"1fe3","name":"MotorLand Aragón",
               "mapUrl":"https://photos.motogp.com/ara.png","lengthMeters":5077,"leftCorners":10,"rightCorners":7,"longestStraightMeters":968}}""",
        )
        assertTrue(guide.stale)
        assertEquals(968, guide.circuit?.longestStraightMeters)
        assertFalse(guide.unavailable)
    }
}

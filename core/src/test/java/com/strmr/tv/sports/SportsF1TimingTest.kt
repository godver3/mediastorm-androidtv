package com.strmr.tv.sports

import com.strmr.tv.api.ApiJson
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Reading F1 timing archive channels, the cases of Expo's `sports-f1.test.ts` on a trimmed
 * excerpt of the recorded Monza capture (`replay/fixtures/f1-timing.json`).
 */
class SportsF1TimingTest {
    private fun archive(channels: String, session: String = "Race", mode: String = "archive"): SportsF1Archive = ApiJson.decodeFromString(
        """{"state":"available","mode":"$mode","eventId":"f1:600057442:401839102","sessionName":"$session",
           "checkedAt":"2026-09-12T16:31:00Z","capturedElapsed":"01:30:00","channels":$channels}""",
    )

    private val race = archive(
        """{"DriverList":{
              "12":{"RacingNumber":"12","FullName":"Kimi ANTONELLI","Tla":"ANT","TeamName":"Mercedes"},
              "63":{"RacingNumber":"63","FullName":"George RUSSELL","Tla":"RUS","TeamName":"Mercedes"},
              "_kf":true,
              "99":{"FullName":"No Number"}},
            "TimingData":{"Lines":{
              "12":{"Position":"1","NumberOfLaps":53,"GapToLeader":"","IntervalToPositionAhead":{"Value":""},
                    "LastLapTime":{"Value":"1:24.010"},"BestLapTime":{"Value":"1:23.504"},"NumberOfPitStops":1,
                    "Sectors":[{"Value":"26.701"},{"Value":"27.120"},{"Value":"30.189"}]},
              "63":{"Position":"2","GapToLeader":"+3.857","IntervalToPositionAhead":{"Value":"+3.857"},"InPit":true,
                    "Sectors":{"0":{"Value":"26.900"},"2":{"Value":"30.400"}},"Retired":false}}},
            "TimingAppData":{"Lines":{
              "12":{"Stints":[{"Compound":"MEDIUM","TotalLaps":20,"StartLaps":0},{"Compound":"HARD","TotalLaps":36,"StartLaps":3}]},
              "63":{"Stints":{"0":{"Compound":"SOFT","TotalLaps":12,"StartLaps":3},"1":{"Compound":"HARD"}}}}},
            "RaceControlMessages":{"Messages":[
              {"Utc":"2026-09-06T13:03:00","Lap":1,"Category":"Flag","Flag":"GREEN","Message":"GREEN LIGHT - PIT EXIT OPEN"},
              {"Utc":"2026-09-06T13:40:12","Lap":21,"Category":"SafetyCar","Message":"SAFETY CAR DEPLOYED"},
              {"Utc":"2026-09-06T13:50:00","Lap":25,"Category":"Other","Message":""}]},
            "WeatherData":{"AirTemp":"27.4","TrackTemp":"44.1","Rainfall":"0"}}""",
    )

    @Test fun driversInPositionOrderWithTheirTiming() {
        val drivers = SportsF1Timing.drivers(race)
        assertEquals("entries without a racing number are left out", listOf("12", "63"), drivers.map { it.id })
        val leader = drivers[0]
        assertEquals("Kimi ANTONELLI", leader.name)
        assertEquals("ANT", leader.abbreviation)
        assertEquals(1, leader.position)
        assertEquals(53, leader.laps)
        assertEquals("1:24.010", leader.lastLap)
        assertEquals("1:23.504", leader.bestLap)
        assertEquals(1, leader.pitStops)
        assertEquals(listOf("26.701", "27.120", "30.189"), leader.sectors)
        assertNull(leader.state)
        val second = drivers[1]
        assertEquals("+3.857", second.interval)
        assertEquals("sparse sector indexes keep their places", listOf("26.900", "", "30.400"), second.sectors)
        assertEquals("In pit", second.state)
        assertNull("no laps reported is none, not zero", second.laps)
    }

    @Test fun stintsTakeOffAUsedSetsEarlierLaps() {
        val drivers = SportsF1Timing.drivers(race).associateBy { it.id }
        assertEquals(listOf(20, 33), drivers.getValue("12").stints.map { it.laps })
        assertEquals(listOf(false, true), drivers.getValue("12").stints.map { it.used })
        val russell = drivers.getValue("63").stints
        assertEquals(listOf("SOFT", "HARD"), russell.map { it.compound })
        assertEquals("an unknown stint length stays unknown", listOf(9, null), russell.map { it.laps })
    }

    @Test fun messagesNewestFirstWithoutBlanks() {
        val messages = SportsF1Timing.messages(race)
        assertEquals(listOf("SAFETY CAR DEPLOYED", "GREEN LIGHT - PIT EXIT OPEN"), messages.map { it.message })
        assertEquals(21, messages[0].lap)
        assertEquals("GREEN", messages[1].flag)
    }

    @Test fun weatherLine() {
        assertEquals("Air 27.4°C · Track 44.1°C · No rainfall reported", SportsF1Timing.weather(race))
    }

    @Test fun qualifyingPartAndEntriesLeft() {
        val qualifying = archive(
            """{"DriverList":{"10":{"RacingNumber":"10","FullName":"Pierre GASLY","Tla":"GAS"}},
               "TimingData":{"SessionPart":2,"NoEntries":[20,15,10],"Lines":{"10":{"Position":"1","KnockedOut":true,
                 "BestLapTimes":[{"Value":"1:22.612"},{"Value":"1:22.077"}]}}}}""",
            session = "Qualifying",
        )
        assertEquals(SportsF1Qualifying(2, 15), SportsF1Timing.qualifying(qualifying))
        val gasly = SportsF1Timing.drivers(qualifying).single()
        assertEquals(listOf("1:22.612", "1:22.077", ""), gasly.qualifyingTimes)
        assertTrue(gasly.knockedOut)
        assertNull("only qualifying archives have parts", SportsF1Timing.qualifying(race))
    }

    @Test fun emptyChannelsAreMissingValuesNotZeros() {
        val bare = archive("""{"DriverList":{"1":{"RacingNumber":"1","FullName":"Driver"}}}""", mode = "recorded")
        val driver = SportsF1Timing.drivers(bare).single()
        assertNull(driver.position)
        assertNull(driver.laps)
        assertTrue(driver.sectors.isEmpty())
        assertTrue(driver.stints.isEmpty())
        assertTrue(SportsF1Timing.messages(bare).isEmpty())
        assertEquals("", SportsF1Timing.weather(bare))
        assertTrue(bare.recorded)
        assertTrue(SportsF1Timing.drivers(SportsF1Archive()).isEmpty())
    }
}

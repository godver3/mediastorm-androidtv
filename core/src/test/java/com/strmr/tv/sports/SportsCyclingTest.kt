package com.strmr.tv.sports

import com.strmr.tv.api.ApiJson
import com.strmr.tv.api.LossyList
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Locale

/**
 * The cycling calendar as the hub and the race page read it (Expo `sports-cycling.ts`, the hub's
 * cycling cards and `SportsCyclingDetailTV`), from trimmed excerpts of the Expo cycling samples.
 */
class SportsCyclingTest {
    private val skipped = mutableListOf<String>()

    init {
        LossyList.report = { skipped += it }
        Locale.setDefault(Locale.US) // "Oct 23, 2026" (the device's locale on a TV)
    }

    private val feed: SportsCyclingFeed = ApiJson.decodeFromString(
        """{"state":"available","source":{"provider":"Cycling organizers","observedAt":"2026-10-23T08:21:27Z"},"coverage":null,
            "data":[
             {"raceKind":"stage-race","id":"aso:tour:2026","name":"Tour de France","category":"men","startDate":"2026-10-01",
              "endDate":"2026-10-23","restDays":["2026-10-10","2026-10-17"],"source":{"provider":"ASO race center","observedAt":"2026-09-09T03:43:49Z"},
              "sourceUrl":"https://racecenter.letour.fr/en/","stages":[
                {"id":"aso:tour:2026:9","name":"Stage 9","date":"2026-10-09","status":"unknown",
                 "results":{"state":"pending","source":{"provider":"ASO race center","observedAt":"2026-09-12T06:21:01Z"},"reason":"Select this stage to load published results."},
                 "generalClassification":{"state":"pending","source":{"provider":"ASO race center","observedAt":"2026-09-12T06:21:01Z"}}},
                {"id":"aso:tour:2026:10","name":"Stage 10","date":"2026-10-11","status":"unknown",
                 "results":{"state":"pending","source":{"provider":"ASO"}},"generalClassification":{"state":"pending","source":{"provider":"ASO"}}},
                {"id":"aso:tour:2026:21","name":"Stage 21","date":"2026-10-23","departure":"Thoiry","arrival":"Paris Champs-Élysées",
                 "distance":"89 km","terrain":"Flat","status":"unknown","generalClassificationLabel":"After stage 21",
                 "routeGuide":{"state":"available","source":{"provider":"ASO"},"sourceUrl":"https://www.letour.fr/en/stage-21",
                               "mapUrl":"https://img.aso.fr/map","profileUrl":"https://img.aso.fr/profile"},
                 "results":{"state":"available","source":{"provider":"ASO race center","observedAt":"2026-09-09T15:52:13Z","updatedAt":"2026-08-17T09:45:48Z"},"data":[
                   {"id":"r3","name":"Mads PEDERSEN","team":"LIDL-TREK","rank":3,"time":"1:58:49","gap":"+0:00"},
                   {"id":"r1","name":"Mathieu VAN DER POEL","team":"ALPECIN-PREMIER TECH","rank":1,"time":"1:58:49","gap":"+0:00","bib":21,"nationality":"NED"},
                   {"id":"r2","name":"Jasper PHILIPSEN","team":"ALPECIN-PREMIER TECH","rank":2,"time":"1:58:49"},
                   {"id":"rx","name":"Out","team":"X","result":"DNF"},
                   {"name":"No id"}]},
                 "generalClassification":{"state":"available","source":{"provider":"ASO"},"data":[
                   {"id":"g1","name":"Tadej POGACAR","rank":1,"time":"73:56:26","gap":"+0:00"},
                   {"id":"g2","name":"Remco EVENEPOEL","rank":2,"time":"74:02:52","gap":"+6:26"}]}}]},
             {"raceKind":"one-day","id":"aso:paris-roubaix:2026","name":"Paris-Roubaix","category":"men","startDate":"2026-04-12",
              "endDate":"2026-04-12","captured":true,"source":{"provider":"ASO"},"stages":[{"id":"aso:paris-roubaix:2026:1","name":"Race","date":"2026-04-12"}]},
             {"id":"cro:cro-race:2026","name":"CRO Race","category":"men","scheduleOnly":true,"startDate":"2026-10-30","endDate":"2026-11-04",
              "source":{"provider":"CRO Race"},"stages":[{"id":"cro:cro-race:2026:1","name":"Stage 1","date":"2026-10-30","status":"scheduled"}]},
             {"name":"no id"}
            ]}""",
    )
    private val tour get() = feed.races[0]

    @Test fun decodesTheFeedLeniently() {
        assertEquals(3, feed.races.size)
        assertEquals("a race without an id and a result without one are skipped", 2, skipped.size)
        assertTrue(feed.coverage.isEmpty())
        val stage = tour.stages.last()
        assertEquals("Thoiry → Paris Champs-Élysées", stage.route)
        assertEquals(4, stage.results.rows.size)
        assertEquals(21, stage.results.rows.first { it.id == "r1" }.bib)
        assertTrue(stage.completed)
        assertFalse("pending classifications have no rows", tour.stages.first().completed)
        assertEquals("aso:tour", tour.followId)
        assertEquals("stage-race", feed.races[2].format)
        assertEquals(Triple("tour", "2026", "21"), SportsCycling.stagePath(stage.id))
        assertNull(SportsCycling.stagePath("tour:21"))
    }

    @Test fun hubListsTheDaysStageAndRestDays() {
        val today = SportsCycling.listings(feed.races, "2026-10-23") { true }
        assertEquals(listOf("cycling:aso:tour:2026:21"), today.map { it.key })
        assertEquals("results are in: a final card", SportsLifecycle.FINAL, today.single().lifecycle)

        val rest = SportsCycling.listings(feed.races, "2026-10-10") { true }.single()
        assertTrue(rest.restDay)
        assertNull(rest.stage)
        assertEquals(SportsLifecycle.SCHEDULED, rest.lifecycle)
        assertEquals("cycling:aso:tour:2026", rest.key)
        assertEquals("Stage 10", rest.nextStage?.name)

        val noStage = SportsCycling.listings(feed.races, "2026-10-12") { true }.single()
        assertFalse("a day without a stage that isn't a rest day", noStage.restDay)
        assertTrue("outside a race's dates nothing shows", SportsCycling.listings(feed.races, "2026-11-10") { true }.isEmpty())
        assertTrue(SportsCycling.listings(feed.races, "2026-10-23") { it.id != tour.id }.isEmpty())
    }

    @Test fun cardTexts() {
        val final = SportsCycling.listings(feed.races, "2026-10-23") { true }.single()
        assertEquals("Stage 21", SportsCyclingFormat.cardSubtitle(final))
        assertEquals("89 km · Flat", SportsCyclingFormat.cardProgress(final))
        assertEquals(
            listOf(SportsCyclingCardRow(1, "Mathieu VAN DER POEL", "+0:00"), SportsCyclingCardRow(2, "Jasper PHILIPSEN", "1:58:49")),
            SportsCyclingFormat.cardRows(final, spoilers = false),
        )
        assertTrue("Hide Scores empties the results", SportsCyclingFormat.cardRows(final, spoilers = true).isEmpty())
        assertEquals("Hide Scores is on", SportsCyclingFormat.cardEmpty(final, spoilers = true))
        assertEquals("FINAL", SportsCyclingFormat.cardPill(final))

        val rest = SportsCycling.listings(feed.races, "2026-10-10") { true }.single()
        assertEquals("REST DAY", SportsCyclingFormat.cardPill(rest))
        assertEquals("Rest day", SportsCyclingFormat.cardSubtitle(rest))
        assertEquals("Racing resumes Oct 11, 2026", SportsCyclingFormat.cardProgress(rest))
        assertEquals("No racing scheduled today", SportsCyclingFormat.cardEmpty(rest, spoilers = false))

        val schedule = SportsCycling.listings(feed.races, "2026-10-30") { true }.single()
        assertEquals("SCHEDULE", SportsCyclingFormat.cardPill(schedule))
        assertEquals("Cycling · Published schedule", SportsCyclingFormat.cardLabel(schedule.race))
        assertEquals("Start time TBC · Find streams in event details", SportsCyclingFormat.cardEmpty(schedule, spoilers = false))
    }

    @Test fun datesStagesAndTheCalendar() {
        assertEquals("Oct 23, 2026", SportsCycling.date("2026-10-23"))
        assertEquals("Date to be confirmed", SportsCycling.date(null))
        assertEquals("Date to be confirmed", SportsCycling.date("soon"))
        assertEquals("Stage 10", SportsCycling.defaultStage(tour, "2026-10-12")?.name)
        assertEquals("before the first stage: the first", "Stage 9", SportsCycling.defaultStage(tour, "2026-09-01")?.name)
        assertEquals(SportsCyclingPeriod.CURRENT, SportsCycling.period(tour, "2026-10-23"))
        assertEquals(SportsCyclingPeriod.PAST, SportsCycling.period(tour, "2026-10-24"))
        assertEquals(SportsCyclingPeriod.UPCOMING, SportsCycling.period(feed.races[2], "2026-10-23"))
        val ordered = feed.races.sortedWith(SportsCycling.calendarOrder("2026-10-23")).map { it.name }
        assertEquals(listOf("Tour de France", "CRO Race", "Paris-Roubaix"), ordered)
        assertEquals("Stage 21 · Oct 23, 2026", SportsCyclingFormat.stageOption(tour.stages.last()))
    }

    @Test fun findStreamsAndFollowing() {
        val stage = tour.stages.last()
        assertTrue(SportsCycling.canSearch(tour, stage, "2026-10-23"))
        assertFalse("a past stage", SportsCycling.canSearch(tour, stage, "2026-10-24"))
        assertFalse("a recorded capture", SportsCycling.canSearch(feed.races[1], feed.races[1].stages[0], "2026-04-01"))
        assertTrue(SportsCycling.isFollowed(tour, listOf("aso:tour"), emptyList()))
        assertTrue(SportsCycling.isFollowed(tour, emptyList(), listOf("cycling")))
        assertEquals("after the one follow, second pin", 2, SportsCycling.followRank(tour, listOf("nba"), listOf("basketball", "cycling")))
        assertEquals(Int.MAX_VALUE, SportsCycling.followRank(tour, emptyList(), emptyList()))
    }

    @Test fun pageTexts() {
        val stage = tour.stages.last()
        assertEquals("Results", SportsCyclingFormat.stageState(tour, stage, stage.results))
        assertEquals("Stage Overview", SportsCyclingFormat.overviewTab(tour))
        assertEquals("Race Overview", SportsCyclingFormat.overviewTab(feed.races[1]))
        assertEquals("Race Classification", SportsCyclingFormat.resultsTitle(feed.races[1], overall = false, teams = false))
        assertEquals("General Classification", SportsCyclingFormat.resultsTitle(tour, overall = true, teams = false))
        assertEquals("Team Results", SportsCyclingFormat.resultsTitle(tour, overall = false, teams = true))
        val pending = tour.stages.first()
        assertEquals("Classification not available" to "The selected classification is not available in this feed.",
                     SportsCyclingFormat.emptyNotice(tour, pending, pending.results))
        val winner = stage.results.rows.first { it.rank == 1 }
        assertEquals("ALPECIN-PREMIER TECH · NED · Bib 21", SportsCyclingFormat.riderLine(winner))
        assertEquals("the winner's time", "1:58:49", SportsCyclingFormat.leaderValue(winner, gapFirst = false))
        assertEquals("+6:26", SportsCyclingFormat.leaderValue(stage.generalClassification.rows[1], gapFirst = true))
    }
}

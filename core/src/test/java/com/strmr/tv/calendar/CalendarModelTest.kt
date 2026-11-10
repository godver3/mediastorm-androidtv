package com.strmr.tv.calendar

import com.strmr.tv.api.CastMember
import com.strmr.tv.home.CalendarItem
import java.time.LocalDate
import java.time.YearMonth
import java.util.Locale
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test

class CalendarModelTest {
    @Before fun locale() = Locale.setDefault(Locale.US)

    private fun episode(title: String, date: String, s: Int? = 1, e: Int? = 1, time: String? = null, tmdb: String? = "1") =
        CalendarItem(title = title, mediaType = "series", seasonNumber = s, episodeNumber = e, airDate = date, airTime = time,
                     externalIds = tmdb?.let { mapOf("tmdb" to it) })

    private fun movie(title: String, date: String, release: String? = "digital", ids: Map<String, String>? = mapOf("imdb" to "tt9")) =
        CalendarItem(title = title, mediaType = "movie", airDate = date, releaseType = release, externalIds = ids)

    @Test fun itemKeyPrefersTmdbThenTvdbThenImdbThenTitle() {
        assertEquals("tmdb:1:series:2026-10-20:1:2:", CalendarModel.itemKey(episode("A", "2026-10-20", 1, 2)))
        val tvdb = episode("A", "2026-10-20", tmdb = null).copy(externalIds = mapOf("tvdb" to "7", "imdb" to "tt1"))
        assertEquals("tvdb:7:series:2026-10-20:1:1:", CalendarModel.itemKey(tvdb))
        assertEquals("tt9:movie:2026-10-21:0:0:digital", CalendarModel.itemKey(movie("M", "2026-10-21")))
        assertEquals("  my show ".trim() + ":series:2026-10-20:0:0:",
                     CalendarModel.itemKey(episode("  My Show ", "2026-10-20", null, null, tmdb = null)))
    }

    @Test fun groupsByDayInTimeOrderUntimedLast() {
        val items = listOf(
            episode("Late", "2026-10-21", time = "21:00", tmdb = "1"),
            episode("Untimed", "2026-10-21", tmdb = "2"),
            episode("Early", "2026-10-21", time = "08:30", tmdb = "3"),
            episode("Before", "2026-10-19", tmdb = "4"),
            episode("NoDate", "", tmdb = "5"),
        )
        val days = CalendarModel.groupDays(items, CalendarFilter.ALL, "2026-10-20")
        assertEquals(listOf("2026-10-19", "2026-10-21"), days.map { it.key })
        assertEquals(listOf("Early", "Late", "Untimed"), days[1].items.map { it.title })
    }

    @Test fun filtersByDateAndKind() {
        val items = listOf(episode("Past", "2026-10-19"), episode("Today", "2026-10-20", tmdb = "2"), movie("Film", "2026-10-25"))
        fun titles(f: CalendarFilter) = CalendarModel.groupDays(items, f, "2026-10-20").flatMap { it.items }.map { it.title }
        assertEquals(listOf("Today", "Film"), titles(CalendarFilter.UPCOMING))
        assertEquals(listOf("Past"), titles(CalendarFilter.RECENT))
        assertEquals(listOf("Past", "Today"), titles(CalendarFilter.SERIES))
        assertEquals(listOf("Film"), titles(CalendarFilter.MOVIES))
    }

    @Test fun selectionKeepsChoiceElseDayFirstElseTodayOrLater() {
        val a = episode("A", "2026-10-18", tmdb = "1")
        val b = episode("B", "2026-10-22", tmdb = "2")
        val c = episode("C", "2026-10-22", tmdb = "3")
        val days = CalendarModel.groupDays(listOf(a, b, c), CalendarFilter.ALL, "2026-10-20")
        val key = CalendarModel::itemKey
        assertEquals(CalendarSelection("2026-10-22", key(c)), CalendarModel.resolveSelection(days, "2026-10-22", key(c), "2026-10-20"))
        assertEquals(CalendarSelection("2026-10-22", key(b)), CalendarModel.resolveSelection(days, "2026-10-22", "gone", "2026-10-20"))
        assertEquals(CalendarSelection("2026-10-22", key(b)), CalendarModel.resolveSelection(days, null, null, "2026-10-20"))
        // Everything aired: the last day.
        assertEquals(CalendarSelection("2026-10-22", key(b)), CalendarModel.resolveSelection(days, "1999-01-01", null, "2027-01-01"))
        assertEquals(CalendarSelection(null, null), CalendarModel.resolveSelection(emptyList(), null, null, "2026-10-20"))
    }

    @Test fun posterMetadataAndSummary() {
        assertEquals("S01E02", CalendarModel.posterMetadata(episode("A", "2026-10-20", 1, 2)))
        assertEquals("Episode", CalendarModel.posterMetadata(episode("A", "2026-10-20", 0, 2)))
        assertEquals("TV", CalendarModel.posterMetadata(movie("M", "2026-10-20", "tv")))
        assertEquals("Theatrical", CalendarModel.posterMetadata(movie("M", "2026-10-20", " theatrical ")))
        assertEquals("", CalendarModel.posterMetadata(movie("M", "2026-10-20", null)))

        assertEquals("Ep.", CalendarModel.summary(episode("A", "d").copy(episodeOverview = " Ep. ", overview = "Show")))
        assertEquals("Show", CalendarModel.summary(episode("A", "d").copy(overview = "Show")))
        assertEquals("Airs on HBO.", CalendarModel.summary(episode("A", "d").copy(network = "HBO")))
        assertEquals("Digital release.", CalendarModel.summary(movie("M", "d")))
        assertEquals("Movie release.", CalendarModel.summary(movie("M", "d", null)))
        assertEquals("Episode details will appear when they are available.", CalendarModel.summary(episode("A", "d")))
    }

    @Test fun detailThenEnrichmentKeepsPopulatedFields() {
        val item = episode("Show", "2026-10-20", 2, 5, time = "21:00").copy(
            episodeTitle = " Pilot ", backdropUrls = listOf(" ", "b2"), posterUrl = "p", genres = listOf("Drama"), year = 2020,
        )
        val detail = CalendarModel.detail(item)
        assertEquals("Pilot", detail.episodeTitle)
        assertEquals("S2 E5", detail.episodeLabel)
        assertEquals("b2", detail.backdropUrl)
        assertEquals("9:00 PM", detail.airTime?.replace(' ', ' '))
        assertEquals("S2 E5  ·  Released 2020  ·  Airs at 9:00 PM", CalendarModel.metadataLine(detail).replace(' ', ' '))

        val merged = CalendarModel.merge(detail, CalendarEnrichment(
            synopsis = "  ", backdropUrl = "big", certification = "TV-MA", genres = emptyList(),
            cast = listOf(CastMember(1, "Ann")), year = 2021,
        ))
        assertEquals(detail.synopsis, merged.synopsis)
        assertEquals("big", merged.backdropUrl)
        assertEquals(listOf("Drama"), merged.genres)
        assertEquals("TV-MA", merged.certification)
        assertEquals(2021, merged.year)
        assertEquals("Ann", merged.cast.single().name)
        assertNull(CalendarModel.detail(movie("M", "2026-10-20")).episodeLabel)
    }

    @Test fun datesAndTimes() {
        val today = LocalDate.of(2026, 10, 20)
        assertEquals("Today", CalendarModel.dayHeading("2026-10-20", today))
        assertEquals("Tomorrow", CalendarModel.dayHeading("2026-10-21", today))
        assertEquals("Yesterday", CalendarModel.dayHeading("2026-10-19", today))
        assertEquals("Friday", CalendarModel.dayHeading("2026-10-23", today))
        assertEquals("Tuesday, October 20", CalendarModel.fullDayHeading("2026-10-20"))
        assertEquals("All day", CalendarModel.formatTime(null))
        assertEquals("soon", CalendarModel.formatTime("soon"))
        assertEquals("8:05 AM", CalendarModel.formatTime("08:05").replace(' ', ' '))
    }

    @Test fun timelineMonthsAndPosition() {
        val days = listOf(CalendarDay("2026-10-01", emptyList()), CalendarDay("2026-12-31", emptyList()))
        val months = CalendarTimeline.months(days)
        assertEquals(listOf("SEP", "OCT", "NOV", "DEC", "JAN"), months.map { it.label })
        assertEquals(YearMonth.of(2027, 1), months.last().key)
        assertEquals(1f, CalendarTimeline.position(months, "2026-10-01"), 0.0001f)
        assertEquals(4f, CalendarTimeline.position(months, "2026-12-31"), 0.0001f)
        assertEquals(1.5f, CalendarTimeline.position(months, "2026-10-16"), 0.0001f)
        assertEquals("OCT 20", CalendarTimeline.activeLabel("2026-10-20"))
        assertEquals("1 release", CalendarTimeline.releaseCount(1))
        assertEquals("Tuesday, October 20, 2 releases", CalendarTimeline.description(CalendarDay("2026-10-20", listOf(
            episode("A", "2026-10-20"), episode("B", "2026-10-20", tmdb = "2"),
        ))))
    }

    @Test fun categoriesMatchExpoRail() {
        assertEquals(listOf("All", "TV", "Movies", "Trending", "Watchlist", "Top"), CalendarCategory.entries.map { it.label })
        assertEquals("top-trending", CalendarCategory.TOP.source.raw)
        assertNull(CalendarCategory.TV.source.raw)
    }
}

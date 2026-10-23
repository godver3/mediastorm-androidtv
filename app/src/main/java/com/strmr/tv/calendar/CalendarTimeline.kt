package com.strmr.tv.calendar

import java.time.LocalDate
import java.time.YearMonth
import java.time.format.DateTimeFormatter
import java.util.Locale

/**
 * The date timeline over the feed (the Expo `CalendarDateTimeline`): a month a node, evenly
 * spaced, from the month before the first release to the month after the last. The track
 * slides so the focused day sits under a fixed marker.
 */
object CalendarTimeline {
    data class Month(val key: YearMonth, val label: String)

    fun months(days: List<CalendarDay>): List<Month> {
        val dates = days.mapNotNull { CalendarModel.parse(it.key) }.sorted()
        if (dates.isEmpty()) return emptyList()
        val end = YearMonth.from(dates.last()).plusMonths(1)
        val label = DateTimeFormatter.ofPattern("MMM", Locale.getDefault())
        return generateSequence(YearMonth.from(dates.first()).minusMonths(1)) { it.plusMonths(1) }
            .takeWhile { it <= end }
            .map { Month(it, it.atDay(1).format(label).uppercase(Locale.getDefault())) }
            .toList()
    }

    /**
     * Where `dateKey` falls along the track, in month spacings: its month's index plus how far
     * through the month it is (the 1st at the node, the last day at the next one).
     */
    fun position(months: List<Month>, dateKey: String): Float {
        val date = CalendarModel.parse(dateKey) ?: return 0f
        val index = months.indexOfFirst { it.key == YearMonth.from(date) }.coerceAtLeast(0)
        val length = date.lengthOfMonth()
        return index + (date.dayOfMonth - 1).toFloat() / maxOf(1, length - 1)
    }

    /** The marker's label: "OCT 20". */
    fun activeLabel(dateKey: String): String = CalendarModel.parse(dateKey)
        ?.format(DateTimeFormatter.ofPattern("MMM d", Locale.getDefault()))?.uppercase(Locale.getDefault()) ?: dateKey

    /** The day as a screen reader reads it: "Monday, October 20, 3 releases". */
    fun description(day: CalendarDay): String {
        val date: LocalDate? = CalendarModel.parse(day.key)
        val name = date?.format(DateTimeFormatter.ofPattern("EEEE, MMMM d", Locale.getDefault())) ?: day.key
        return "$name, ${releaseCount(day.items.size)}"
    }

    fun releaseCount(count: Int) = "$count ${if (count == 1) "release" else "releases"}"
}

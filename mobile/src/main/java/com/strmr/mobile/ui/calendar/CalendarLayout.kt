package com.strmr.mobile.ui.calendar

import com.strmr.tv.calendar.CalendarDay

/**
 * Where each day sits in the agenda's flat list (a header, then its releases), so the day
 * strip can jump to a day and follow the agenda's scroll.
 */
object CalendarLayout {
    /** The index of [key]'s header, or null when the day isn't shown. */
    fun headerIndex(days: List<CalendarDay>, key: String): Int? {
        var index = 0
        for (day in days) {
            if (day.key == key) return index
            index += 1 + day.items.size
        }
        return null
    }

    /** The day whose header or release sits at [index]. */
    fun dayAt(days: List<CalendarDay>, index: Int): String? {
        var start = 0
        for (day in days) {
            val end = start + 1 + day.items.size
            if (index < end) return day.key
            start = end
        }
        return days.lastOrNull()?.key
    }

    /** Expo's `formatSource`: the source tag on a release. */
    fun sourceLabel(source: String?): String? = when (source) {
        "watchlist" -> "Watchlist"
        "history" -> "History"
        "top-trending" -> "Top"
        "mdblist" -> "MDBList"
        "trending" -> "Trending"
        else -> null
    }
}

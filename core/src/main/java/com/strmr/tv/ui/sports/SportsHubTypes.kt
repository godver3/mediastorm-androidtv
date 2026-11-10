package com.strmr.tv.ui.sports

import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.strmr.tv.app.App
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

/** The day the hub shows. Other days need a server that answers dated scoreboards. */
enum class SportsDay(val offset: Long, val label: String) {
    YESTERDAY(-1, "Yesterday"), TODAY(0, "Today"), TOMORROW(1, "Tomorrow");

    /** The local day's bounds. */
    fun bounds(): Pair<Instant, Instant> {
        val zone = ZoneId.systemDefault()
        val day = LocalDate.now(zone).plusDays(offset)
        return day.atStartOfDay(zone).toInstant() to day.plusDays(1).atStartOfDay(zone).toInstant()
    }

    /** The ESPN days (US Eastern) that cover the local day: one, or two away from US time. */
    fun scoreboardDates(): List<String> {
        val eastern = ZoneId.of("America/New_York")
        val (start, end) = bounds()
        return listOf(start, end.minusSeconds(1)).map { it.atZone(eastern).toLocalDate().toString() }.distinct()
    }
}

enum class SportsFilter(val label: String) { ALL("All"), LIVE("Live"), UPCOMING("Upcoming"), RESULTS("Results") }

/** A row of the hub. */
data class SportsSection(val key: String, val title: String, val items: List<SportsHubItem>)

/** Hide scores: per device, as in the Expo app (`sports.hub.spoilerMode.v1`). */
object SportsSpoilers {
    private val prefs by lazy { App.context.getSharedPreferences("strmr.sports", Context.MODE_PRIVATE) }

    var hidden by mutableStateOf(prefs.getBoolean("spoilerMode", false))
        private set

    fun toggle() {
        hidden = !hidden
        prefs.edit().putBoolean("spoilerMode", hidden).apply()
    }
}

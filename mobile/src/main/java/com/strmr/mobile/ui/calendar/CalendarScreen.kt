package com.strmr.mobile.ui.calendar

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.EventBusy
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.strmr.mobile.ui.lists.GridSpinner
import com.strmr.mobile.ui.lists.GridStatus
import com.strmr.mobile.ui.shell.LocalNavigator
import com.strmr.mobile.ui.shell.PageScaffold
import com.strmr.tv.app.AppSession
import com.strmr.tv.calendar.CalendarDay
import com.strmr.tv.calendar.CalendarModel
import com.strmr.tv.calendar.CalendarTimeline
import com.strmr.tv.ui.calendar.CalendarState
import kotlinx.coroutines.launch

/**
 * The Calendar tab on a phone (the iPhone's port of the Expo mobile calendar) on the shared
 * [CalendarState]: category chips and a strip of days over an agenda, a section per day. It
 * opens on today (or the next day with releases); tapping a day jumps to it, and the strip
 * follows the scroll. Tap a release for details, long press for the title menu.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CalendarScreen(asPage: Boolean) {
    val profile by AppSession.profile.collectAsState()
    val scope = rememberCoroutineScope()
    val state = remember(profile?.id) { CalendarState(scope) }
    LaunchedEffect(state) { state.load() }
    val days = state.days
    val listState = rememberLazyListState()
    // The day the agenda shows at its top, for the strip.
    val shownDay by remember(days) {
        derivedStateOf { CalendarLayout.dayAt(days, listState.firstVisibleItemIndex) }
    }
    // A new set of days (category or reload) opens on the selected one: today or the next with releases.
    LaunchedEffect(days) {
        val key = state.selection.dateKey ?: return@LaunchedEffect
        CalendarLayout.headerIndex(days, key)?.let { listState.scrollToItem(it) }
    }
    val total = days.sumOf { it.items.size }
    val subtitle = if (total > 0) "$total releases across ${days.size} days" else null

    PageScaffold("Calendar", showBack = asPage, large = false) { padding ->
        Column(Modifier.padding(padding).fillMaxSize()) {
            subtitle?.let {
                Text(
                    it, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(start = 16.dp, bottom = 4.dp),
                )
            }
            CategoryChips(state.category, state::select)
            if (days.isNotEmpty()) {
                DayStrip(days, shownDay) { key ->
                    CalendarLayout.headerIndex(days, key)?.let { scope.launch { listState.animateScrollToItem(it) } }
                }
            }
            HorizontalDivider(Modifier.padding(top = 8.dp))
            PullToRefreshBox(
                isRefreshing = state.loading && state.items.isNotEmpty(),
                onRefresh = state::load,
                modifier = Modifier.weight(1f).fillMaxWidth(),
            ) {
                when {
                    state.loading && state.items.isEmpty() -> Column { GridSpinner() }
                    state.error != null && state.items.isEmpty() -> Column {
                        GridStatus("Calendar unavailable", state.error, Icons.Filled.EventBusy, "Try Again", state::load)
                    }
                    days.isEmpty() -> Column {
                        GridStatus("Nothing here yet", "Try another category or enable more calendar sources for this profile.", Icons.Filled.CalendarMonth)
                    }
                    else -> Agenda(days, listState)
                }
            }
        }
    }
}

/** A section per day, its header pinned while its releases scroll. */
@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun Agenda(days: List<CalendarDay>, listState: LazyListState) {
    val navigator = LocalNavigator.current
    LazyColumn(Modifier.fillMaxSize(), state = listState, contentPadding = PaddingValues(bottom = 24.dp)) {
        for (day in days) {
            stickyHeader(key = "day-${day.key}", contentType = "header") { DayHeader(day) }
            items(day.items, key = { "${day.key}|${CalendarModel.itemKey(it)}" }, contentType = { "release" }) { item ->
                CalendarRow(item) { navigator.openDetails(CalendarModel.target(item)) }
            }
        }
    }
}

/** "Today" over "November 10", with the day's release count. */
@Composable
private fun DayHeader(day: CalendarDay) {
    Row(
        Modifier.fillMaxWidth().background(MaterialTheme.colorScheme.surface).padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.Bottom,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Column {
            Text(CalendarModel.dayHeading(day.key), style = MaterialTheme.typography.titleMedium)
            Text(
                CalendarModel.fullDayHeading(day.key).substringAfter(", "), style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Text(
            CalendarTimeline.releaseCount(day.items.size), style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

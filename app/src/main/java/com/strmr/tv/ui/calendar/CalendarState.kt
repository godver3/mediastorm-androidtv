package com.strmr.tv.ui.calendar

import androidx.compose.runtime.Stable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.strmr.tv.api.ApiClient
import com.strmr.tv.app.AppLog
import com.strmr.tv.calendar.CalendarCategory
import com.strmr.tv.calendar.CalendarEnrichment
import com.strmr.tv.calendar.CalendarItemDetail
import com.strmr.tv.calendar.CalendarModel
import com.strmr.tv.calendar.calendar
import com.strmr.tv.calendar.calendarEnrichment
import com.strmr.tv.home.CalendarItem
import kotlin.coroutines.cancellation.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

internal const val LOG = "Calendar"

/**
 * The Calendar screen's state (the Expo `WideCalendarScreen`): the rail's category, the
 * releases its source returned, and the focused day and release. Changing the source reloads;
 * changing only the filter regroups what's loaded.
 */
@Stable
class CalendarState(private val scope: CoroutineScope) {
    var category by mutableStateOf(CalendarCategory.ALL)
        private set
    var items by mutableStateOf<List<CalendarItem>>(emptyList())
        private set
    var loading by mutableStateOf(true)
        private set
    var error by mutableStateOf<String?>(null)
        private set
    private var dateKey by mutableStateOf<String?>(null)
    private var itemKey by mutableStateOf<String?>(null)
    private var request: Job? = null

    val days by derivedStateOf { CalendarModel.groupDays(items, category.filter) }
    val selection by derivedStateOf { CalendarModel.resolveSelection(days, dateKey, itemKey) }
    val activeItem by derivedStateOf { CalendarModel.findItem(days, selection.itemKey) }

    fun load() {
        request?.cancel()
        val source = category.source
        items = emptyList()
        loading = true
        error = null
        request = scope.launch {
            val started = System.currentTimeMillis()
            try {
                val loaded = ApiClient.calendar(source)
                items = loaded
                AppLog.i(LOG, "loaded ${loaded.size} releases (source=${source.raw ?: "all"}) in ${System.currentTimeMillis() - started} ms")
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                error = e.message?.takeIf { it.isNotBlank() } ?: "Unable to load calendar."
                AppLog.e(LOG, "load failed (source=${source.raw ?: "all"})", e)
            }
            loading = false
        }
    }

    /** A rail entry: forgets the selection; reloads only when the source changes. */
    fun select(next: CalendarCategory) {
        if (next == category) return
        dateKey = null
        itemKey = null
        val reload = next.source != category.source
        category = next
        AppLog.i(LOG, "category ${next.name}")
        if (reload) load()
    }

    /** A poster took focus: its day and release become the selection. */
    fun preview(item: CalendarItem) {
        dateKey = item.airDate
        itemKey = CalendarModel.itemKey(item)
    }
}

/**
 * The detail panel's content for the focused release (Expo `useCalendarItemDetails`): the
 * release's own fields at once, then its title's details, fetched 180 ms after focus settles,
 * cached per profile and release; a failure waits 30 s before that release is tried again.
 */
@Stable
class CalendarDetailLoader(screen: CoroutineScope) {
    // A failed request mustn't cancel the screen's scope.
    private val scope = CoroutineScope(screen.coroutineContext + SupervisorJob(screen.coroutineContext[Job]))
    var detail by mutableStateOf<CalendarItemDetail?>(null)
        private set
    var loading by mutableStateOf(false)
        private set
    private val cache = mutableMapOf<String, CalendarEnrichment>()
    private val pending = mutableMapOf<String, Deferred<CalendarEnrichment>>()
    private val failures = mutableMapOf<String, Long>()
    private var job: Job? = null

    fun show(item: CalendarItem?) {
        job?.cancel()
        if (item == null) {
            detail = null
            loading = false
            return
        }
        val key = "${ApiClient.profileId}::${CalendarModel.itemKey(item)}"
        val base = CalendarModel.detail(item)
        cache[key]?.let {
            detail = CalendarModel.merge(base, it)
            loading = false
            return
        }
        detail = base
        loading = false
        job = scope.launch {
            while (true) {
                val failedAt = failures[key]
                val wait = failedAt?.let { BACKOFF_MS - (System.currentTimeMillis() - it) }?.takeIf { it > 0 }
                delay((wait ?: 0) + DEBOUNCE_MS)
                loading = true
                val request = pending.getOrPut(key) { scope.async { ApiClient.calendarEnrichment(item) } }
                try {
                    val value = request.await()
                    cache[key] = value
                    failures.remove(key)
                    detail = CalendarModel.merge(base, value)
                    loading = false
                    return@launch
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    failures[key] = System.currentTimeMillis()
                    AppLog.w(LOG, "details failed for ${item.titleId}: ${e.message}")
                    loading = false
                } finally {
                    if (pending[key] === request && request.isCompleted) pending.remove(key)
                }
            }
        }
    }

    private companion object {
        const val DEBOUNCE_MS = 180L
        const val BACKOFF_MS = 30_000L
    }
}

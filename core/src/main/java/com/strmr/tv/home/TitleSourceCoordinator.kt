package com.strmr.tv.home

import com.strmr.tv.api.SourceAvailability
import com.strmr.tv.api.TitleSourceCoordinate
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

/** All visible card instances share one bounded view, including duplicate shelves. */
@OptIn(FlowPreview::class)
class TitleSourceCoordinator(scope: CoroutineScope, private val gateway: TitleSourceGateway, refreshes: StateFlow<Long> = SourceRecheckEvents.generation) {
    private val demands = MutableStateFlow<Map<String, TitleSourceCoordinate>>(emptyMap())
    private val _hints = MutableStateFlow<Map<String, SourceAvailability>>(emptyMap())
    val hints: StateFlow<Map<String, SourceAvailability>> = _hints.asStateFlow()
    private val _lastKnown = MutableStateFlow<Map<String, SourceAvailability>>(emptyMap())
    val lastKnown: StateFlow<Map<String, SourceAvailability>> = _lastKnown.asStateFlow()
    private val observer = TitleSourceObserver(gateway)
    private val job: Job = scope.launch {
        combine(demands,refreshes) { owners, generation -> owners.values.distinct().take(24) to generation }
            .distinctUntilChanged().debounce(150).collectLatest { (titles,_) ->
            observer.states(titles).collect { values ->
                _hints.value = values
                if (values.isNotEmpty()) _lastKnown.update { old ->
                    val next = LinkedHashMap(old)
                    values.forEach { (key,value) -> next.remove(key); next[key] = value }
                    next.entries.toList().takeLast(500).associate { it.toPair() }
                }
            }
        }
    }
    fun isCurrent() = gateway.isCurrent()
    fun setVisible(owner: String, title: TitleSourceCoordinate?) {
        demands.update { old ->
            if (title == null) old - owner
            else if (old.containsKey(owner) || old.size < 256) old + (owner to title)
            else old
        }
    }
    fun close() { _hints.value = emptyMap(); _lastKnown.value = emptyMap(); job.cancel() }
}

/** Visibility is demand only while the active window can actually show the card. */
fun titleSourceActive(inWindow: Boolean, resumed: Boolean, focused: Boolean, playerOpen: Boolean, resolving: Boolean, ready: Boolean): Boolean =
    inWindow && resumed && focused && !playerOpen && !resolving && ready

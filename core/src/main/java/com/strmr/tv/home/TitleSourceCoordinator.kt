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
class TitleSourceCoordinator(scope: CoroutineScope, private val gateway: TitleSourceGateway) {
    private val demands = MutableStateFlow<Map<String, TitleSourceCoordinate>>(emptyMap())
    private val _hints = MutableStateFlow<Map<String, SourceAvailability>>(emptyMap())
    val hints: StateFlow<Map<String, SourceAvailability>> = _hints.asStateFlow()
    private val observer = TitleSourceObserver(gateway)
    private val job: Job = scope.launch {
        demands.map { it.values.distinct().take(24) }.distinctUntilChanged().debounce(150).collectLatest { titles ->
            observer.states(titles).collect { _hints.value = it }
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
    fun close() { _hints.value = emptyMap(); job.cancel() }
}

/** Visibility is demand only while the active window can actually show the card. */
fun titleSourceActive(inWindow: Boolean, resumed: Boolean, focused: Boolean, playerOpen: Boolean, resolving: Boolean, ready: Boolean): Boolean =
    inWindow && resumed && focused && !playerOpen && !resolving && ready

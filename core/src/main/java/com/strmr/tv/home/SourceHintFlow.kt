package com.strmr.tv.home

import com.strmr.tv.api.SourceAvailability
import java.time.Instant
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow

/** Observation is owned by the visible card; collection cancellation stops its local timer. */
fun sourceHintFlow(source: SourceAvailability, now: () -> Instant = Instant::now): Flow<SourceHint> = flow {
    while (true) {
        val instant = now()
        emit(SourceHint.from(source, instant)!!)
        delay(SourceHint.nextUpdateMillis(source, instant))
    }
}

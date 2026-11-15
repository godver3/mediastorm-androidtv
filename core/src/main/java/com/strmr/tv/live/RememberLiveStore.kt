package com.strmr.tv.live

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel

/** An owned Live view cancels all old jobs when the profile, account or route changes. */
@Composable
fun rememberProfileLiveStore(context: Long): LiveStore {
    val parent = rememberCoroutineScope()
    val owned = remember(context) {
        val scope = CoroutineScope(parent.coroutineContext + SupervisorJob(parent.coroutineContext[Job]))
        scope to LiveStore(scope,context)
    }
    DisposableEffect(owned) { onDispose { owned.first.cancel() } }
    return owned.second
}

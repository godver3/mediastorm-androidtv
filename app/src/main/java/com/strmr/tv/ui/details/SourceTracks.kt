package com.strmr.tv.ui.details

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import com.strmr.tv.api.SourceResult
import com.strmr.tv.player.ManualSourceTracks

@Composable
fun rememberSourceTracks(): SourceTracks {
    val scope = rememberCoroutineScope()
    return remember { SourceTracks(scope) }
}

/** The open result's panel ([SourceTracks]); Play hands the result and its tracks to [onPlay]. */
@Composable
fun SourceTracks.Panel(onPlay: (SourceResult, ManualSourceTracks?) -> Unit) {
    val result = openResult ?: return
    SourceTracksDialog(
        release = release(result),
        state = probeState(result),
        defaults = trackDefaults,
        choice = choice(result),
        onChoice = { choose(result, it) },
        onRetry = { retry(result) },
        onPlay = {
            close()
            onPlay(result, manualFor(result))
        },
        onDismiss = { close() },
    )
}

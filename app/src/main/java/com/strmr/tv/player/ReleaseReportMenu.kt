package com.strmr.tv.player

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

/** The TV remote uses the same source-bound workflow as the phone's report sheet. */
@Composable
fun ReleaseReportMenu(controller: PlayerController, onDone: () -> Unit, modifier: Modifier = Modifier) {
    val flow = remember(controller) { controller.openReleaseReports() }
    val state by flow.state.collectAsState()
    LaunchedEffect(flow) { flow.load() }
    DisposableEffect(flow) { onDispose { controller.closeReleaseReports() } }
    key(state.busy, state.problem, state.ready != null, state.options != null) {
        ChoiceMenu("Report / change source", releaseReportChoices(flow, state, onDone), "Loading selected release...", modifier, width = 680.dp)
    }
}

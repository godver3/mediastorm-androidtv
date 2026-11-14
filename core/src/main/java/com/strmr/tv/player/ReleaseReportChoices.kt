package com.strmr.tv.player

/** The same confirmation, track and resume choices on TV and touch devices. */
fun releaseReportChoices(flow: ReleaseReports, state: ReleaseReportState, onDone: () -> Unit): List<MenuChoice> = buildList {
    val options = state.options
    val message = state.message
    if (state.busy) {
        add(MenuChoice("Cancel preparation", (message?.plus(" ") ?: "") + "Keep the current source. A saved report remains saved.", selected = false, onClick = onDone))
        return@buildList
    }
    if (message != null && options != null) add(MenuChoice("Status: $message", "Back to problems", selected = false) { flow.choose(null) })
    if (state.reportId != null) add(MenuChoice("Undo report", "Remove this exclusion from the server.", selected = false) { flow.undo() })
    if (options == null) {
        add(MenuChoice("Close", message, selected = false, onClick = onDone))
        return@buildList
    }
    if (state.ready != null) {
        if (state.problem?.wrongContent != true) {
            ReleaseResume.entries.forEach { mode ->
                add(MenuChoice(mode.label, "Different edits may use different timing.", selected = state.resume == mode, section = "Start position") { flow.resume(mode) })
            }
        }
        add(MenuChoice("Start prepared alternative", if (state.problem?.wrongContent == true) "Wrong content: restart from the beginning." else state.resume.label,
            selected = false, section = state.ready.displayName ?: "Alternative ready") { flow.take() })
        add(MenuChoice("Cancel", "Keep playing the current source.", selected = false, onClick = onDone))
        return@buildList
    }
    val problem = state.problem
    if (problem == null) {
        ReleaseProblem.entries.filter { it != ReleaseProblem.WRONG_EPISODE || options.hasEpisode }.forEach { reason ->
            if (reason != ReleaseProblem.LANGUAGE || options.audioLanguage.isNotBlank()) {
                add(MenuChoice(reason.label, selected = false, section = options.releaseName) { flow.choose(reason) })
            }
        }
        add(MenuChoice("Prepare an alternative without reporting", "No permanent exclusion is saved.", selected = false) { flow.submit(false, true) })
        add(MenuChoice("Cancel", selected = false, onClick = onDone))
    } else {
        if (problem == ReleaseProblem.AUDIO) {
            options.audioTracks.forEach { track ->
                add(MenuChoice("Track ${track.index}: ${languageLabel(track.language)}", track.title ?: track.codec,
                    selected = state.track == track.index, section = "Select the affected track") { flow.track(track.index) })
            }
        }
        if (problem != ReleaseProblem.AUDIO || state.track != null) {
            add(MenuChoice("Report only", problem.scope(options), selected = false) { flow.submit(true, false) })
            add(MenuChoice("Report and prepare an alternative", problem.scope(options), selected = false) { flow.submit(true, true) })
        }
        add(MenuChoice("Back to problems", selected = false) { flow.choose(null) })
        add(MenuChoice("Cancel", selected = false, onClick = onDone))
    }
}

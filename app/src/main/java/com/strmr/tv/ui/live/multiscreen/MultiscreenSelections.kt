package com.strmr.tv.ui.live.multiscreen

/**
 * The channel selection in flight (a picker choice or Resume's extra screens), which waits on a
 * capacity check: one at a time, and a newer one, the picker closing or exit supersedes it, so a
 * late answer changes nothing. Main thread only.
 */
internal class MultiscreenSelections {
    private var request = 0

    /** A selection is waiting on its check. */
    var busy = false
        private set

    /** Starts a selection; its generation tells whether it's still current. */
    fun begin(): Int {
        busy = true
        return ++request
    }

    fun isCurrent(generation: Int) = generation == request

    /** The selection `generation` is done (a superseded one leaves the newer alone). */
    fun end(generation: Int) {
        if (isCurrent(generation)) busy = false
    }

    /** Supersedes whatever is in flight. */
    fun cancel() {
        ++request
        busy = false
    }
}

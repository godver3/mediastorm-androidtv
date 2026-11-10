package com.strmr.mobile.player.ui

/**
 * When the phone player's controls hide by themselves: a few seconds after the last touch,
 * only while playing, and never while a sheet is open or the seek bar is being dragged (as the
 * iPhone and Expo players). Pure, so the timing is unit tested; the chrome polls [shouldHide].
 */
class ChromeTimer(private val hideAfterMs: Long = HIDE_AFTER_MS) {
    var visible: Boolean = true
        private set
    private var lastTouchMs: Long = 0

    /** Any touch on the controls (or a tap that shows them) restarts the countdown. */
    fun touched(nowMs: Long) {
        lastTouchMs = nowMs
    }

    fun show(nowMs: Long) {
        visible = true
        lastTouchMs = nowMs
    }

    fun hide() {
        visible = false
    }

    /** A single tap on the video: shows hidden controls, hides shown ones. */
    fun toggle(nowMs: Long) = if (visible) hide() else show(nowMs)

    /** Whether the controls should hide now. */
    fun shouldHide(nowMs: Long, playing: Boolean, busy: Boolean): Boolean =
        visible && playing && !busy && nowMs - lastTouchMs >= hideAfterMs

    companion object {
        const val HIDE_AFTER_MS = 4_000L
    }
}

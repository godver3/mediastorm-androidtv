package com.strmr.tv.player

import android.view.Display
import android.view.Window
import com.strmr.tv.app.AppLog
import kotlin.math.abs
import kotlin.math.roundToInt

/**
 * Android TV's counterpart of tvOS Match Content (frame rate): once the video's frame rate is
 * known, asks for a display mode at the current resolution whose refresh rate is the frame
 * rate or a whole multiple of it (23.976 → 23.976 Hz, else 47.95, else 119.88), and gives the
 * mode back when the player closes. The TV may take a few seconds to resync after a switch.
 */
class FrameRateMatcher(private val window: Window, private val display: Display?) {
    private var applied = false

    fun match(frameRate: Float) {
        val display = display ?: return
        if (frameRate <= 0 || applied) return
        applied = true
        val current = display.mode
        val sameSize = display.supportedModes.filter {
            it.physicalWidth == current.physicalWidth && it.physicalHeight == current.physicalHeight
        }
        val best = sameSize
            .mapNotNull { mode -> multiple(mode.refreshRate, frameRate)?.let { mode to it } }
            .minWithOrNull(compareBy({ it.second }, { -it.first.refreshRate }))
            ?.first ?: return
        if (best.modeId == current.modeId) return
        AppLog.i("FrameRate", "content ${"%.3f".format(frameRate)} fps → mode ${best.modeId} ${"%.3f".format(best.refreshRate)} Hz")
        window.attributes = window.attributes.apply { preferredDisplayModeId = best.modeId }
    }

    /** Back to the system's choice. */
    fun reset() {
        if (!applied) return
        applied = false
        window.attributes = window.attributes.apply { preferredDisplayModeId = 0 }
    }

    /** `refresh` as a whole multiple of `fps` (1, 2, 3…), or null when it isn't one. */
    private fun multiple(refresh: Float, fps: Float): Int? {
        val ratio = refresh / fps
        val whole = ratio.roundToInt()
        return whole.takeIf { it >= 1 && abs(ratio - whole) < 0.01f }
    }
}

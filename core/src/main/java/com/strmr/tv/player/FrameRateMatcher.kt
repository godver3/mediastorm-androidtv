package com.strmr.tv.player

import android.os.Handler
import android.os.Looper
import android.view.Display
import android.view.Window
import androidx.media3.common.Format
import androidx.media3.exoplayer.video.VideoFrameMetadataListener
import com.strmr.tv.app.AppLog

/**
 * Android TV's counterpart of tvOS Match Content (frame rate), as the Expo app does it: the
 * video's frame rate comes from the container ([onVideoFormat]), else the server's probe of
 * the file ([PlayerSession.sourceFrameRate]; Matroska and MPEG-TS state none), else from the
 * rendered frames' timestamps ([FrameCadence]). It then asks for the display mode that fits it
 * ([FrameRates.bestMode]) once [enable] says Match Frame Rate is on, and gives the mode back
 * when the player closes ([reset]). [onSwitch] runs after a switch: the TV takes a few seconds
 * to resync.
 */
class FrameRateMatcher(
    private val window: Window,
    private val display: Display?,
    private val onSwitch: () -> Unit = {},
) : VideoFrameMetadataListener {
    private val main = Handler(Looper.getMainLooper())
    private val cadence = FrameCadence()
    private var enabled: Boolean? = null
    private var candidate: Pair<Float, String>? = null
    private var applied = false
    /** Set once the container and probe both came up empty: the frames are measured. */
    @Volatile private var measuring = false
    @Volatile private var done = false

    /** Whether Match Frame Rate is on; nothing switches until it is known. */
    fun enable(on: Boolean) {
        enabled = on
        if (!on) done = true
        AppLog.i("FrameRate", "match frame rate ${if (on) "on" else "off"}")
        apply()
    }

    /** The selected video track: its frame rate, else [probed], else measure the frames. */
    fun onVideoFormat(format: Format, probed: Float?) {
        if (candidate != null || done) return
        when {
            FrameRates.usable(format.frameRate) -> propose(format.frameRate, "container")
            probed != null && FrameRates.usable(probed) -> propose(probed, "server probe")
            !measuring -> {
                measuring = true
                AppLog.i("FrameRate", "no frame rate in container or probe; measuring frames")
            }
        }
    }

    /** Playback thread: every frame about to render, while measuring. */
    override fun onVideoFrameAboutToBeRendered(presentationTimeUs: Long, releaseTimeNs: Long, format: Format, mediaFormat: android.media.MediaFormat?) {
        if (!measuring || done) return
        val fps = cadence.feed(presentationTimeUs)
        when {
            fps != null -> { measuring = false; main.post { propose(fps, "frame cadence") } }
            cadence.exhausted -> {
                measuring = false
                main.post { AppLog.i("FrameRate", "frame cadence unsteady; leaving the display mode") }
            }
        }
    }

    private fun propose(fps: Float, source: String) {
        if (candidate != null) return
        candidate = FrameRates.snap(fps) to source
        apply()
    }

    private fun apply() {
        val (fps, source) = candidate ?: return
        if (enabled != true || applied) return
        applied = true
        done = true
        val display = display ?: return
        val current = display.mode.toMode()
        val best = FrameRates.bestMode(fps, current, display.supportedModes.map { it.toMode() })
        if (best == null) {
            AppLog.i("FrameRate", "content ${"%.3f".format(fps)} fps ($source): no fitting mode at ${current.width}x${current.height}")
            return
        }
        if (best.id == current.id) {
            AppLog.i("FrameRate", "content ${"%.3f".format(fps)} fps ($source): already at ${"%.3f".format(current.refreshRate)} Hz")
            return
        }
        AppLog.i("FrameRate", "content ${"%.3f".format(fps)} fps ($source) → mode ${best.id} ${"%.3f".format(best.refreshRate)} Hz")
        window.attributes = window.attributes.apply { preferredDisplayModeId = best.id }
        onSwitch()
    }

    /** Back to the system's choice. */
    fun reset() {
        done = true
        if (!applied) return
        window.attributes = window.attributes.apply { preferredDisplayModeId = 0 }
    }

    private fun Display.Mode.toMode() = FrameRates.Mode(modeId, physicalWidth, physicalHeight, refreshRate)
}

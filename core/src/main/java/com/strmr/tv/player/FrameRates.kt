package com.strmr.tv.player

import kotlin.math.abs
import kotlin.math.roundToInt

/** Frame-rate arithmetic for [FrameRateMatcher]: parsing, snapping to broadcast rates, picking a display mode. */
object FrameRates {
    /** The rates video is made at; a measured or probed rate this close to one is that one. */
    private val common = floatArrayOf(23.976f, 24f, 25f, 29.97f, 30f, 47.952f, 48f, 50f, 59.94f, 60f, 100f, 119.88f, 120f)

    /** A display mode, as far as matching cares. */
    data class Mode(val id: Int, val width: Int, val height: Int, val refreshRate: Float)

    /** ffprobe's `avg_frame_rate` ("24000/1001", "25/1", "23.976"), or null when unusable ("0/0"). */
    fun parse(value: String?): Float? {
        val raw = value?.trim()?.removeSuffix("fps")?.trim()?.ifEmpty { null } ?: return null
        val parts = raw.split('/')
        val fps = when (parts.size) {
            1 -> parts[0].toFloatOrNull()
            2 -> parts[1].toFloatOrNull()?.takeIf { it > 0 }?.let { d -> parts[0].toFloatOrNull()?.div(d) }
            else -> null
        }
        return fps?.takeIf { usable(it) }
    }

    fun usable(fps: Float): Boolean = fps.isFinite() && fps > 1f && fps <= 240f

    /** The common rate within 0.3% of [fps], else [fps] itself. */
    fun snap(fps: Float): Float = nearestCommon(fps) ?: fps

    /** The common rate within 0.3% of [fps], or null when it is none of them. */
    fun nearestCommon(fps: Float): Float? = common.minBy { abs(it - fps) }.takeIf { abs(it - fps) / fps < 0.003f }

    /**
     * The mode at the current resolution that shows [fps] best: a refresh rate that is the
     * frame rate or a whole multiple of it exactly (23.976 → 23.976, else 47.95, else 119.88 Hz),
     * else one within 0.2% (24 Hz for 23.976), lowest multiple first. Null when none fits, and
     * the TV stays as it is.
     */
    fun bestMode(fps: Float, current: Mode, modes: List<Mode>): Mode? {
        data class Fit(val mode: Mode, val multiple: Int, val error: Float)
        return modes
            .filter { it.width == current.width && it.height == current.height }
            .mapNotNull { mode ->
                val multiple = (mode.refreshRate / fps).roundToInt()
                if (multiple < 1) return@mapNotNull null
                val error = abs(mode.refreshRate / (multiple * fps) - 1f)
                Fit(mode, multiple, error).takeIf { error < 0.002f }
            }
            .minWithOrNull(compareBy<Fit>({ if (it.error < 0.0005f) 0 else 1 }, { it.multiple }, { it.error }))
            ?.mode
    }
}

/**
 * Measures a video's frame rate from the presentation times of the frames it renders, for
 * containers that don't state one (Matroska, MPEG-TS). Averages over [SPAN_US] of video, so
 * millisecond timestamps still tell 23.976 from 24; a dropped frame counts as the frames it
 * stood for. A seek or other jump starts the measurement over. Not thread-safe: fed from the
 * playback thread only.
 */
class FrameCadence {
    private val times = ArrayList<Long>(256)
    private var attempts = 0

    /** True once it has given up (variable frame rate, or too many restarts). */
    var exhausted = false
        private set

    /** The next frame's presentation time; returns the snapped rate once measured. */
    fun feed(ptsUs: Long): Float? {
        if (exhausted) return null
        val last = times.lastOrNull()
        if (last != null && (ptsUs <= last || ptsUs - last > MAX_GAP_US)) {
            times.clear()
        }
        times.add(ptsUs)
        if (times.size < MIN_FRAMES || times.last() - times.first() < SPAN_US) return null
        val fps = measure()
        times.clear()
        fps?.let { FrameRates.nearestCommon(it) }?.let { return it }
        if (++attempts >= MAX_ATTEMPTS) exhausted = true
        return null
    }

    private fun measure(): Float? {
        val deltas = times.zipWithNext { a, b -> b - a }
        val median = deltas.sorted()[deltas.size / 2].takeIf { it > 0 } ?: return null
        var intervals = 0
        for (d in deltas) {
            val k = (d.toFloat() / median).roundToInt()
            if (k !in 1..4) return null
            intervals += k
        }
        return intervals * 1_000_000f / (times.last() - times.first())
    }

    companion object {
        const val SPAN_US = 5_000_000L
        const val MIN_FRAMES = 60
        const val MAX_GAP_US = 250_000L
        const val MAX_ATTEMPTS = 4
    }
}

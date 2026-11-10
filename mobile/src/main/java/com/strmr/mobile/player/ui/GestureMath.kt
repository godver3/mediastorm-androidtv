package com.strmr.mobile.player.ui

/**
 * Double-tap seeking, as the Expo and YouTube-style phone players: each double tap on the left
 * or right half seeks 10 s, and taps in quick succession on the same side add up in the label
 * ("+30 s") instead of starting over. Pure, for unit tests.
 */
class SeekRipple(private val stepMs: Long = STEP_MS, private val windowMs: Long = WINDOW_MS) {
    /** The side of the run in progress (-1 left, 1 right, 0 none) and its total. */
    var side: Int = 0
        private set
    var totalMs: Long = 0
        private set
    private var lastAtMs: Long = 0

    /** A double tap on `side`; returns the seek it makes (signed ms). */
    fun tap(side: Int, nowMs: Long): Long {
        val delta = side * stepMs
        totalMs = if (side == this.side && nowMs - lastAtMs <= windowMs) totalMs + delta else delta
        this.side = side
        lastAtMs = nowMs
        return delta
    }

    /** Whether the label is still shown at `nowMs`. */
    fun showing(nowMs: Long): Boolean = side != 0 && nowMs - lastAtMs <= windowMs

    /** "+10 s" / "−20 s". */
    val label: String get() = (if (totalMs < 0) "−" else "+") + "${kotlin.math.abs(totalMs) / 1000} s"

    fun reset() {
        side = 0
        totalMs = 0
    }

    companion object {
        const val STEP_MS = 10_000L
        const val WINDOW_MS = 800L
    }
}

/** Which half of the player a touch is on: -1 left, 1 right. */
fun sideOf(x: Float, width: Float): Int = if (x < width / 2) -1 else 1

/**
 * A vertical drag's change to a 0..1 level (brightness or volume): dragging up the full height
 * of the player raises it by [fullSwipe] (so a full-height swipe isn't needed to go from 0 to 1).
 * `dragY` is in pixels, downward positive, as the gesture reports it.
 */
fun swipeLevel(start: Float, dragY: Float, height: Float, fullSwipe: Float = 1.2f): Float {
    if (height <= 0f) return start.coerceIn(0f, 1f)
    return (start - dragY / height * fullSwipe).coerceIn(0f, 1f)
}

/** A 0..1 level as a stream volume step (rounded), and back. */
fun volumeStep(level: Float, max: Int): Int = Math.round(level.coerceIn(0f, 1f) * max)
fun volumeLevel(step: Int, max: Int): Float = if (max <= 0) 0f else (step.toFloat() / max).coerceIn(0f, 1f)

/** The seek bar's position for a fraction of its length, in the bar's ms. */
fun barPosition(fraction: Float, durationMs: Long): Long =
    if (durationMs <= 0) 0 else (fraction.coerceIn(0f, 1f) * durationMs).toLong()

/** A position's fraction of the bar. */
fun barFraction(positionMs: Long, durationMs: Long): Float =
    if (durationMs <= 0) 0f else (positionMs.toFloat() / durationMs).coerceIn(0f, 1f)

/**
 * The fitted video rectangle in a box: the picture at its aspect, letterboxed or pillarboxed,
 * centred; `aspect` 0 (unknown) fills the box. Returns width and height.
 */
fun fittedSize(boxWidth: Float, boxHeight: Float, aspect: Float): Pair<Float, Float> {
    if (aspect <= 0f || boxWidth <= 0f || boxHeight <= 0f) return boxWidth to boxHeight
    val width = minOf(boxWidth, boxHeight * aspect)
    return width to width / aspect
}

/** Picture in picture accepts aspect ratios between 1:2.39 and 2.39:1; anything else is clamped (just inside, so rounding stays legal). */
fun pipAspect(aspect: Float): Pair<Int, Int> {
    val a = if (aspect > 0f) aspect.coerceIn(0.42f, 2.39f) else 16f / 9f
    return Math.round(a * 1000) to 1000
}

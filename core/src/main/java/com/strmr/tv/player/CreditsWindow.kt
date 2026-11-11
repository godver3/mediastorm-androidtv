package com.strmr.tv.player

/**
 * The decision half of on-device credits detection, the Expo players' state machine
 * (`CreditsDetector.kt` / KSPlayer's `CreditsDetector.swift`) without the OCR: from 90 % of the
 * runtime a frame is read every [coarseIntervalSec], every [fineIntervalSec] once credit-like
 * text shows, and credits start at the first of three samples in a row that each hold at least
 * two credit-like lines, four different ones between them, and not all the same (credits roll;
 * a title card doesn't). Fires once.
 */
class CreditsWindow(
    private val coarseIntervalSec: Double = 5.0,
    private val fineIntervalSec: Double = 1.0,
) {
    private class Sample(val timeSec: Double, val lines: Set<String>)

    private val samples = ArrayDeque<Sample>()
    private var lastSampleSec = Double.NEGATIVE_INFINITY
    var detectedAtSec: Double? = null
        private set

    /** Whether a frame should be read at [positionSec] (the caller then reports it to [onText]). */
    fun shouldSample(positionSec: Double, durationSec: Double): Boolean {
        if (detectedAtSec != null || !durationSec.isFinite() || durationSec <= 0 || !positionSec.isFinite()) return false
        if (positionSec / durationSec < START_FRACTION) return false
        // A seek back leaves the interval measured from before it; start again from there.
        if (positionSec < lastSampleSec) samples.clear()
        else if (positionSec - lastSampleSec < if (samples.isEmpty()) coarseIntervalSec else fineIntervalSec) return false
        lastSampleSec = positionSec
        return true
    }

    /** The lines read from the frame at [timeSec]; returns the credits start when this sample decides it. */
    fun onText(timeSec: Double, recognized: List<String>): Double? {
        if (detectedAtSec != null) return null
        val lines = recognized.map(::normalize).filter(::looksLikeCredit).toSet()
        if (lines.size < MIN_LINES) {
            samples.clear()
            return null
        }
        if (samples.lastOrNull()?.lines == lines) return null
        samples.addLast(Sample(timeSec, lines))
        while (samples.size > WINDOW) samples.removeFirst()
        if (samples.size < WINDOW) return null
        if (samples.flatMapTo(HashSet()) { it.lines }.size < MIN_UNIQUE) return null
        if (samples.map { it.lines }.toSet().size <= 1) return null
        return samples.first().timeSec.also { detectedAtSec = it }
    }

    fun reset() {
        samples.clear()
        lastSampleSec = Double.NEGATIVE_INFINITY
        detectedAtSec = null
    }

    companion object {
        const val START_FRACTION = 0.90
        private const val MIN_LINES = 2
        private const val WINDOW = 3
        private const val MIN_UNIQUE = 4

        /** Lowercase, punctuation to spaces, single-spaced. */
        fun normalize(text: String): String =
            text.lowercase().map { if (it.isLetterOrDigit() || it.isWhitespace()) it else ' ' }.joinToString("")
                .trim().split(Regex("\\s+")).filter { it.isNotBlank() }.joinToString(" ")

        /** A name or role: 2 to 60 characters, mostly letters. */
        fun looksLikeCredit(text: String): Boolean {
            val t = text.trim()
            if (t.length !in 2..60) return false
            val letters = t.count { it.isLetter() || it == ' ' || it == '.' || it == '-' || it == '\'' }
            return letters.toFloat() / t.length > 0.7f
        }
    }
}

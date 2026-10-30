package com.strmr.tv.player

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** Auto Switch's stall rules: stuck for a while, or short stalls again and again. */
class StallWatchTest {
    private val watch = StallWatch()

    /** Steady playback for `seconds` from `position` (s) at `time` (s), one sample a second. */
    private fun play(position: Long, time: Long, seconds: Int): StallWatch.Trigger? =
        (0..seconds).firstNotNullOfOrNull { s -> watch.sample((position + s) * 1000, true, (time + s) * 1000) }

    /** The playhead held at `position` for `seconds` after `time`, one sample a second. */
    private fun stall(position: Long, time: Long, seconds: Int, active: Boolean = true): StallWatch.Trigger? =
        (1..seconds).firstNotNullOfOrNull { s -> watch.sample(position * 1000, active, (time + s) * 1000) }

    @Test fun stuckMidPlaybackFiresAfterTenSeconds() {
        assertNull(play(100, 0, 30))
        assertNull(stall(130, 30, 9))
        assertEquals(StallWatch.Trigger.Stuck(10), stall(130, 39, 2))
    }

    @Test fun slowStartGetsTwentySeconds() {
        assertNull(stall(0, 0, 19))
        assertEquals(StallWatch.Trigger.Stuck(20), stall(0, 19, 2))
    }

    @Test fun pausedNeverFires() {
        assertNull(play(100, 0, 10))
        assertNull(stall(110, 10, 120, active = false))
    }

    @Test fun seekLandingIsAllowedTheSlowStart() {
        assertNull(play(100, 0, 10))
        assertNull(stall(1000, 10, 15))
        assertNull(play(1000, 25, 5))
    }

    @Test fun threeShortStallsInTwoMinutesFire() {
        assertNull(play(100, 0, 10))
        var position = 110L
        var time = 10L
        var fired: StallWatch.Trigger? = null
        repeat(3) {
            if (fired != null) return@repeat
            fired = stall(position, time, 3)
            time += 3
            if (fired == null) {
                assertNull(play(position, time, 20))
                position += 20
                time += 20
            }
        }
        assertEquals(StallWatch.Trigger.RepeatedStalls(3), fired)
    }

    @Test fun stallsSpreadOverMoreThanTwoMinutesDontFire() {
        assertNull(play(100, 0, 5))
        var position = 105L
        var time = 5L
        repeat(4) {
            assertNull(stall(position, time, 3))
            time += 3
            assertNull(play(position, time, 70))
            position += 70
            time += 70
        }
    }
}

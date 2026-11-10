package com.strmr.tv.adaptive

import org.junit.Assert.assertEquals
import org.junit.Test

/** Expo's `testBackendConnectionSpeedAggregate` maths. */
class SpeedMathTest {
    @Test fun splitSpreadsTheRemainder() {
        assertEquals(listOf(3L, 3L, 2L, 2L), SpeedMath.split(10, 4))
        assertEquals(listOf(4L shl 20), SpeedMath.split(4L shl 20, 1))
        assertEquals("at most 8 streams", 8, SpeedMath.split(100, 20).size)
        assertEquals("at least 1 stream", 1, SpeedMath.split(100, 0).size)
    }

    @Test fun mbps() {
        // 1 MB (10^6 bytes) in one second is 8 Mbps.
        assertEquals(8.0, SpeedMath.mbps(1_000_000, 1000.0), 1e-9)
        assertEquals("never divides by less than 1 ms", 8_000.0, SpeedMath.mbps(1_000_000, 0.0), 1e-9)
    }

    @Test fun aggregateTakesTheEarliestSetupOffTheWholeTime() {
        val flows = listOf(
            SpeedFlow(2_500_000, 900.0, 100.0, 800.0),
            SpeedFlow(2_500_000, 1000.0, 150.0, 850.0),
        )
        val sample = SpeedMath.aggregate(flows, 1100.0, 42)
        assertEquals(100.0, sample.setupMs, 0.0)
        assertEquals(1000.0, sample.transferMs, 0.0)
        assertEquals(5_000_000L, sample.receivedBytes)
        assertEquals(40.0, sample.speedMbps, 1e-9)
        assertEquals(2, sample.parallelStreams)
    }
}

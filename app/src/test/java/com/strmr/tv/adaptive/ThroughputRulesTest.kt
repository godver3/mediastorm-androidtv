package com.strmr.tv.adaptive

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** Expo's `services/adaptive-throughput.ts` rules: route key, two-hour freshness, rounding, stored shape. */
class ThroughputRulesTest {
    private val now = 1_800_000_000_000L
    private fun estimate(ageS: Long, mbps: Double = 80.0) =
        ThroughputEstimate(mbps, now / 1000 - ageS, ThroughputSource.IDLE)

    @Test fun routeKey() {
        assertEquals("http://10.0.2.2:7777", ThroughputRules.routeKey(" http://10.0.2.2:7777/ ", overIroh = false))
        assertEquals("http://host/proxy", ThroughputRules.routeKey("http://host/proxy//", overIroh = false))
        assertEquals("transport:iroh", ThroughputRules.routeKey("http://127.0.0.1:41234/", overIroh = true))
    }

    @Test fun freshness() {
        assertTrue(ThroughputRules.isFresh(estimate(0), now))
        assertTrue("exactly two hours", ThroughputRules.isFresh(estimate(2 * 3600), now))
        assertFalse("past two hours", ThroughputRules.isFresh(estimate(2 * 3600 + 1), now))
        assertFalse("from the future", ThroughputRules.isFresh(estimate(-5), now))
        assertFalse("zero", ThroughputRules.isFresh(estimate(10, 0.0), now))
        assertFalse("NaN", ThroughputRules.isFresh(estimate(10, Double.NaN), now))
        assertFalse(ThroughputRules.isFresh(null, now))
    }

    @Test fun roundingAndHeader() {
        assertEquals(87.5, ThroughputRules.rounded(87.46), 0.0)
        assertEquals(120.0, ThroughputRules.rounded(119.96), 0.0)
        assertEquals("120", ThroughputRules.headerValue(120.0))
        assertEquals("87.5", ThroughputRules.headerValue(87.5))
    }

    @Test fun storedShapeRoundTrips() {
        val routes = mapOf("http://a" to ThroughputEstimate(55.5, 1_700_000_000, ThroughputSource.MANUAL))
        val raw = ThroughputRules.encode(routes)
        assertTrue(raw, raw.contains("\"version\":2") && raw.contains("\"source\":\"manual\""))
        assertEquals(routes, ThroughputRules.decode(raw))
        // Expo's own JSON reads the same.
        val expo = """{"version":2,"routes":{"transport:iroh":{"measuredMbps":12.3,"measuredAt":1700000000,"source":"idle"}}}"""
        assertEquals(ThroughputSource.IDLE, ThroughputRules.decode(expo)["transport:iroh"]?.source)
        assertEquals(emptyMap<String, ThroughputEstimate>(), ThroughputRules.decode("""{"version":1,"routes":{}}"""))
        assertEquals(emptyMap<String, ThroughputEstimate>(), ThroughputRules.decode("not json"))
        assertEquals(emptyMap<String, ThroughputEstimate>(), ThroughputRules.decode(null))
    }
}

package com.strmr.mobile.connection

import java.util.Base64
import okhttp3.HttpUrl.Companion.toHttpUrl
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class RouteRulesTest {
    private val settings = NetworkSettings("HomeNet", "http://192.168.1.10:7777", "https://media.example.com")
    private val home = "http://192.168.1.10:7777/".toHttpUrl()
    private val away = "https://media.example.com/".toHttpUrl()

    @Test fun directOnCellularIsAway() {
        assertEquals(RouteRules.DirectChoice(false, away), RouteRules.direct(settings, onCellular = true, ssid = "HomeNet", recentHome = true))
    }

    @Test fun directByWifiName() {
        assertEquals(RouteRules.DirectChoice(true, home), RouteRules.direct(settings, false, "homenet", null))
        assertEquals(RouteRules.DirectChoice(false, away), RouteRules.direct(settings, false, "Cafe", null))
    }

    @Test fun unreadableNameUsesRecentAnswerElseKeeps() {
        assertEquals(RouteRules.DirectChoice(true, home), RouteRules.direct(settings, false, null, true))
        assertEquals(RouteRules.DirectChoice(null, null), RouteRules.direct(settings, false, null, null))
    }

    @Test fun recentDetectionExpires() {
        assertEquals(true, RouteRules.recentDetection(true, at = 1000, now = 1000 + 299_000))
        assertNull(RouteRules.recentDetection(true, at = 1000, now = 1000 + 301_000))
        assertNull(RouteRules.recentDetection(null, 0, 0))
    }

    @Test fun probeRules() {
        assertFalse(RouteRules.shouldProbe(onCellular = true, ssid = null, homeSsid = "HomeNet"))
        assertTrue(RouteRules.shouldProbe(false, null, "HomeNet"))
        assertTrue(RouteRules.shouldProbe(false, "HOMENET", "HomeNet"))
        assertFalse(RouteRules.shouldProbe(false, "Cafe", "HomeNet"))
    }

    @Test fun serverIdFromInvite() {
        val id = "ab".repeat(32)
        val payload = Base64.getUrlEncoder().withoutPadding().encodeToString("""{"id":"${id.uppercase()}","addrs":[]}""".toByteArray())
        assertEquals(id, RouteRules.serverId("mshost-iroh-$payload"))
        assertNull(RouteRules.serverId("mshost-ABC123"))
        val bad = Base64.getUrlEncoder().encodeToString("""{"id":"xyz"}""".toByteArray())
        assertNull(RouteRules.serverId("mshost-iroh-$bad"))
        assertTrue(RouteRules.identityMatches(id.uppercase(), id))
        assertFalse(RouteRules.identityMatches(null, id))
    }

    @Test fun backoff() {
        assertEquals(listOf(2000L, 4000L, 8000L, 15000L, 30000L, 30000L), (0..5).map(RouteRules::backoffMs))
    }

    @Test fun loopbackAddressesAreIgnored() {
        assertNull(NetworkSettings.address("http://127.0.0.1:7777"))
        assertNull(NetworkSettings.address("localhost"))
        assertEquals(home, NetworkSettings.address("192.168.1.10:7777/api"))
    }

    @Test fun journalSanitizes() {
        val text = ReconnectJournal.sanitize("failed https://x.example.com/a token=abc Bearer xyz mshost-iroh-QQQ")
        assertEquals("failed [url] token=[redacted] Bearer [redacted] [invite]", text)
    }
}

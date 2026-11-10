package com.strmr.tv.auth

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ServerAddressTest {
    private fun n(input: String) = ServerAddress.normalize(input)?.toString()

    @Test
    fun lanHostsGetHttpAndThePort() {
        assertEquals("http://192.168.1.10:7777/", n("192.168.1.10"))
        assertEquals("http://nas:7777/", n("nas"))
        assertEquals("http://media.local:7777/", n("media.local"))
    }

    @Test
    fun tailscaleHostsCountAsLocal() {
        assertEquals("http://100.75.176.115:7777/", n("100.75.176.115"))
        assertEquals("http://media.tail1234.ts.net:7777/", n("media.tail1234.ts.net"))
        // Outside 100.64.0.0/10 is a public address.
        assertEquals("https://100.128.0.1/", n("100.128.0.1"))
    }

    @Test
    fun publicHostsGetHttpsAndKeepTheirPort() {
        assertEquals("https://media.example.com/", n("media.example.com"))
        assertEquals("http://media.example.com:8080/", n("media.example.com:8080"))
    }

    @Test
    fun dropsTrailingApiAndRejectsEmpty() {
        assertEquals("http://10.0.0.2:7777/", n("http://10.0.0.2:7777/api/"))
        assertNull(n("  "))
    }
}

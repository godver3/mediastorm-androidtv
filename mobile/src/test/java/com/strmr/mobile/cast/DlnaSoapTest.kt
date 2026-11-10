package com.strmr.mobile.cast

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

class DlnaSoapTest {
    private val av = "urn:schemas-upnp-org:service:AVTransport:1"

    @Test
    fun buildsEnvelopeWithEscapedArguments() {
        val body = DlnaSoap.envelope(av, "SetAVTransportURI", listOf("InstanceID" to "0", "CurrentURI" to "http://h/a?x=1&y=<2>"))
        assertTrue(body.startsWith("<?xml version=\"1.0\" encoding=\"utf-8\"?><s:Envelope"))
        assertTrue("<s:Body><u:SetAVTransportURI xmlns:u=\"$av\">" in body)
        assertTrue("<InstanceID>0</InstanceID><CurrentURI>http://h/a?x=1&amp;y=&lt;2&gt;</CurrentURI>" in body)
        assertTrue(body.endsWith("</u:SetAVTransportURI></s:Body></s:Envelope>"))
        // It is well-formed XML that reads back to the arguments.
        val action = XmlNode.parse(body)!!.descendants("SetAVTransportURI").single()
        assertEquals("http://h/a?x=1&y=<2>", action.childText("CurrentURI"))
    }

    @Test
    fun readsActionResponse() {
        val reply = """<?xml version="1.0"?><s:Envelope xmlns:s="http://schemas.xmlsoap.org/soap/envelope/"><s:Body>
            <u:GetPositionInfoResponse xmlns:u="$av"><Track>1</Track><TrackDuration>01:02:03</TrackDuration>
            <RelTime>0:10:05.500</RelTime></u:GetPositionInfoResponse></s:Body></s:Envelope>"""
        val node = DlnaSoap.response("GetPositionInfo", 200, reply)
        assertEquals(3723.0, DlnaSoap.seconds(node.childText("TrackDuration"))!!, 0.0)
        assertEquals(605.0, DlnaSoap.seconds(node.childText("RelTime"))!!, 0.0)
    }

    @Test
    fun emptyReplyStillAnswers() {
        assertEquals("PlayResponse", DlnaSoap.response("Play", 200, "").name)
    }

    @Test
    fun faultBecomesError() {
        val fault = """<s:Envelope xmlns:s="http://schemas.xmlsoap.org/soap/envelope/"><s:Body><s:Fault>
            <faultcode>s:Client</faultcode><faultstring>UPnPError</faultstring><detail>
            <UPnPError xmlns="urn:schemas-upnp-org:control-1-0"><errorCode>701</errorCode>
            <errorDescription>Transition not available</errorDescription></UPnPError></detail></s:Fault></s:Body></s:Envelope>"""
        try {
            DlnaSoap.response("Play", 500, fault)
            fail("expected a fault")
        } catch (e: CastException) {
            assertEquals("The TV rejected Play (701: Transition not available).", e.message)
        }
        try {
            DlnaSoap.response("Stop", 404, "")
            fail("expected a fault")
        } catch (e: CastException) {
            assertEquals("The TV rejected Stop (HTTP 404).", e.message)
        }
    }

    @Test
    fun parsesClocks() {
        assertEquals(0.0, DlnaSoap.seconds("00:00:00")!!, 0.0)
        assertEquals(36_000.0 + 59 * 60 + 59, DlnaSoap.seconds("10:59:59")!!, 0.0)
        assertEquals(125.0, DlnaSoap.seconds(" 0:02:05.123 ")!!, 0.0)
        assertNull(DlnaSoap.seconds("NOT_IMPLEMENTED"))
        assertNull(DlnaSoap.seconds(""))
        assertNull(DlnaSoap.seconds(null))
        assertNull(DlnaSoap.seconds("12:30"))
    }

    @Test
    fun formatsClocks() {
        assertEquals("00:00:00", DlnaSoap.clock(-4.0))
        assertEquals("00:01:05", DlnaSoap.clock(65.9))
        assertEquals("02:00:01", DlnaSoap.clock(7201.0))
    }

    @Test
    fun mapsTransportStates() {
        assertEquals(CastPlaybackState.Phase.PLAYING, DlnaTransport.phase("PLAYING"))
        assertEquals(CastPlaybackState.Phase.PAUSED, DlnaTransport.phase("PAUSED_PLAYBACK"))
        assertEquals(CastPlaybackState.Phase.BUFFERING, DlnaTransport.phase(" transitioning "))
        assertEquals(CastPlaybackState.Phase.IDLE, DlnaTransport.phase("STOPPED"))
        assertEquals(CastPlaybackState.Phase.IDLE, DlnaTransport.phase("NO_MEDIA_PRESENT"))
        assertEquals(CastPlaybackState.Phase.IDLE, DlnaTransport.phase(null))
    }
}

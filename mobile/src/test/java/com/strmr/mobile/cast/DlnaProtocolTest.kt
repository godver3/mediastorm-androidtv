package com.strmr.mobile.cast

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class DlnaProtocolTest {
    private val flags = "DLNA.ORG_FLAGS=01700000000000000000000000000000"

    @Test
    fun buildsProtocolInfo() {
        assertEquals("http-get:*:video/x-matroska:DLNA.ORG_OP=01;DLNA.ORG_CI=0;$flags",
                     DlnaProtocol.protocolInfo("video/x-matroska", converted = false))
        assertEquals("http-get:*:video/mpeg:DLNA.ORG_PN=AVC_TS_HD_24_AC3_ISO;DLNA.ORG_OP=00;DLNA.ORG_CI=1;$flags",
                     DlnaProtocol.protocolInfo("video/mpeg", profile = "AVC_TS_HD_24_AC3_ISO", operation = "00", converted = true))
        assertEquals("video/mpeg", DlnaProtocol.mime("http-get:*:video/mpeg:*"))
    }

    @Test
    fun picksMimesByExtension() {
        assertEquals("video/x-matroska", DlnaProtocol.mimesForPath("/media/Movie (2020)/Movie.MKV?x=1").first())
        assertEquals(listOf("video/vnd.dlna.mpeg-tts", "video/mpeg"), DlnaProtocol.mimesForPath("rec.ts"))
        assertEquals(listOf("video/mp4"), DlnaProtocol.mimesForPath("usenet/abc/stream"))
    }

    @Test
    fun acceptsBySinkList() {
        val mkv = DlnaProtocol.protocolInfo("video/x-matroska", converted = false)
        val hls = DlnaProtocol.protocolInfo(DlnaProtocol.HLS_MIME, converted = true)
        val ts = DlnaProtocol.protocolInfo("video/mpeg", profile = "AVC_TS_HD_24_AC3_ISO", operation = "00", converted = true)
        // A renderer that won't say takes anything.
        assertTrue(DlnaProtocol.accepts(null, mkv))
        val lg = listOf("http-get:*:video/x-matroska:*", "http-get:*:video/mp4:DLNA.ORG_PN=AVC_MP4_HP_HD_AAC")
        assertTrue(DlnaProtocol.accepts(lg, mkv))
        assertFalse(DlnaProtocol.accepts(lg, hls))
        // Wildcards.
        assertTrue(DlnaProtocol.accepts(listOf("http-get:*:*:*"), hls))
        assertTrue(DlnaProtocol.accepts(listOf("http-get:*:video/*:*"), mkv))
        assertFalse(DlnaProtocol.accepts(listOf("http-get:*:audio/*:*"), mkv))
        // A sink naming a profile takes only that profile.
        val sony = listOf("http-get:*:video/mpeg:DLNA.ORG_PN=AVC_TS_HD_24_AC3_ISO;SONY.COM_PN=x")
        assertTrue(DlnaProtocol.accepts(sony, ts))
        assertFalse(DlnaProtocol.accepts(listOf("http-get:*:video/mpeg:DLNA.ORG_PN=MPEG_PS_PAL"), ts))
        // Other transports don't count.
        assertFalse(DlnaProtocol.accepts(listOf("rtsp-rtp-udp:*:video/x-matroska:*"), mkv))
        assertFalse(DlnaProtocol.accepts(lg, "nonsense"))
    }

    @Test
    fun splitsSinkValue() {
        assertEquals(listOf("http-get:*:video/mp4:*", "http-get:*:audio/mpeg:*"),
                     DlnaProtocol.sinkList(" http-get:*:video/mp4:* ,http-get:*:audio/mpeg:*,"))
        assertNull(DlnaProtocol.sinkList(""))
        assertNull(DlnaProtocol.sinkList(null))
    }

    @Test
    fun buildsDidlLite() {
        val info = DlnaProtocol.protocolInfo("video/mp4", converted = false)
        val didl = DlnaProtocol.didlLite("Tom & Jerry <1>", "http://s/api/video/stream?path=a&token=t", info)
        assertTrue(didl.startsWith("<?xml version=\"1.0\" encoding=\"UTF-8\"?><DIDL-Lite"))
        val root = XmlNode.parse(didl)!!
        assertEquals("DIDL-Lite", root.name)
        val item = root.child("item")!!
        assertEquals("Tom & Jerry <1>", item.childText("title"))
        assertEquals("object.item.videoItem", item.childText("class"))
        assertEquals("http://s/api/video/stream?path=a&token=t", item.childText("res"))
        assertTrue("protocolInfo=\"http-get:*:video/mp4:" in didl)
        assertTrue("musicTrack" in DlnaProtocol.didlLite("Song", "http://s/a.mp3", "http-get:*:audio/mpeg:*"))
    }

    @Test
    fun didlSurvivesSoapEscaping() {
        // The DIDL goes inside SetAVTransportURI escaped once more; the renderer reads it back intact.
        val didl = DlnaProtocol.didlLite("A & B", "http://s/x?a=1&b=2", "http-get:*:video/mp4:*")
        val body = DlnaSoap.envelope("urn:schemas-upnp-org:service:AVTransport:1", "SetAVTransportURI",
                                     listOf("InstanceID" to "0", "CurrentURIMetaData" to didl))
        val sent = XmlNode.parse(body)!!.descendants("SetAVTransportURI").single().childText("CurrentURIMetaData")
        assertEquals(didl, sent)
    }
}

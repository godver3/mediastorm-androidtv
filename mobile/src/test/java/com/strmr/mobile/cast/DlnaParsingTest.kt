package com.strmr.mobile.cast

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class DlnaParsingTest {
    @Test
    fun searchMessageAsksForRenderers() {
        val message = DlnaParsing.searchMessage("239.255.255.250")
        assertTrue(message.startsWith("M-SEARCH * HTTP/1.1\r\nHOST: 239.255.255.250:1900\r\n"))
        assertTrue("MAN: \"ssdp:discover\"\r\n" in message)
        assertTrue("MX: 3\r\n" in message)
        assertTrue("ST: urn:schemas-upnp-org:device:MediaRenderer:1\r\n\r\n" in message)
    }

    @Test
    fun parsesSearchReply() {
        val reply = "HTTP/1.1 200 OK\r\nCACHE-CONTROL: max-age=1800\r\nLocation: http://192.168.1.42:1400/xml/device_description.xml\r\n" +
            "ST: urn:schemas-upnp-org:device:MediaRenderer:1\r\nUSN: uuid:abc::urn:schemas-upnp-org:device:MediaRenderer:1\r\n\r\n"
        val parsed = DlnaParsing.parseReply(reply)
        assertEquals(SsdpReply("http://192.168.1.42:1400/xml/device_description.xml",
                               "uuid:abc::urn:schemas-upnp-org:device:MediaRenderer:1"), parsed)
    }

    @Test
    fun parsesNotifyAndBareNewlines() {
        val notify = "NOTIFY * HTTP/1.1\nNT: urn:schemas-upnp-org:device:MediaRenderer:1\nLOCATION: http://10.0.0.5/desc.xml\n"
        assertEquals("http://10.0.0.5/desc.xml", DlnaParsing.parseReply(notify)?.location)
        // No USN: the location stands in.
        assertEquals("http://10.0.0.5/desc.xml", DlnaParsing.parseReply(notify)?.usn)
    }

    @Test
    fun ignoresOtherDevicesAndBadLocations() {
        assertNull(DlnaParsing.parseReply("HTTP/1.1 200 OK\r\nST: urn:schemas-upnp-org:device:MediaServer:1\r\nLOCATION: http://a/b\r\n"))
        assertNull(DlnaParsing.parseReply("HTTP/1.1 200 OK\r\nST: urn:schemas-upnp-org:device:MediaRenderer:1\r\nLOCATION: https://a/b\r\n"))
        assertNull(DlnaParsing.parseReply("HTTP/1.1 200 OK\r\nST: urn:schemas-upnp-org:device:MediaRenderer:1\r\n"))
    }

    private val description = """
        <?xml version="1.0"?>
        <root xmlns="urn:schemas-upnp-org:device-1-0">
          <specVersion><major>1</major><minor>0</minor></specVersion>
          <device>
            <deviceType>urn:schemas-upnp-org:device:MediaRenderer:1</deviceType>
            <friendlyName> [TV] Living Room </friendlyName>
            <modelName>OLED55C1</modelName>
            <UDN>uuid:1234-5678</UDN>
            <serviceList>
              <service>
                <serviceType>urn:schemas-upnp-org:service:RenderingControl:1</serviceType>
                <controlURL>/upnp/control/RenderingControl1</controlURL>
              </service>
              <service>
                <serviceType>urn:schemas-upnp-org:service:ConnectionManager:1</serviceType>
                <controlURL>ConnectionManager/control</controlURL>
              </service>
              <service>
                <serviceType>urn:schemas-upnp-org:service:AVTransport:1</serviceType>
                <controlURL>/upnp/control/AVTransport1</controlURL>
              </service>
            </serviceList>
          </device>
        </root>
    """.trimIndent()

    @Test
    fun parsesDeviceDescription() {
        val root = XmlNode.parse(description)
        assertNotNull(root)
        val renderer = DlnaParsing.renderer(root!!, "http://192.168.1.42:1400/dmr/desc.xml")!!
        assertEquals("uuid:1234-5678", renderer.udn)
        assertEquals("[TV] Living Room", renderer.name)
        assertEquals("OLED55C1", renderer.model)
        assertEquals("192.168.1.42", renderer.host)
        assertEquals(DlnaService("urn:schemas-upnp-org:service:AVTransport:1", "http://192.168.1.42:1400/upnp/control/AVTransport1"),
                     renderer.avTransport)
        assertEquals("http://192.168.1.42:1400/upnp/control/RenderingControl1", renderer.renderingControl?.controlUrl)
        // Relative to the description's directory.
        assertEquals("http://192.168.1.42:1400/dmr/ConnectionManager/control", renderer.connectionManager?.controlUrl)
        assertEquals("dlna:uuid:1234-5678", renderer.device.id)
    }

    @Test
    fun findsEmbeddedRendererAndHonoursUrlBase() {
        val xml = """
            <root xmlns="urn:schemas-upnp-org:device-1-0"><URLBase>http://10.0.0.9:8080/</URLBase>
              <device><deviceType>urn:schemas-upnp-org:device:Basic:1</deviceType><friendlyName>Box</friendlyName>
                <deviceList><device><deviceType>urn:schemas-upnp-org:device:MediaRenderer:1</deviceType>
                  <UDN>uuid:inner</UDN>
                  <serviceList><service><serviceType>urn:schemas-upnp-org:service:AVTransport:1</serviceType>
                    <controlURL>av/ctl</controlURL></service></serviceList>
                </device></deviceList>
              </device></root>
        """.trimIndent()
        val renderer = DlnaParsing.renderer(XmlNode.parse(xml)!!, "http://10.0.0.9:49152/desc.xml")!!
        assertEquals("uuid:inner", renderer.udn)
        // No friendly name on the renderer itself: its host.
        assertEquals("10.0.0.9", renderer.name)
        assertEquals("http://10.0.0.9:8080/av/ctl", renderer.avTransport.controlUrl)
        assertNull(renderer.renderingControl)
    }

    @Test
    fun rejectsDescriptionsWithoutRendererOrTransport() {
        val server = "<root><device><deviceType>urn:schemas-upnp-org:device:MediaServer:1</deviceType></device></root>"
        assertNull(DlnaParsing.renderer(XmlNode.parse(server)!!, "http://a/desc.xml"))
        val noTransport = "<root><device><deviceType>urn:schemas-upnp-org:device:MediaRenderer:1</deviceType></device></root>"
        assertNull(DlnaParsing.renderer(XmlNode.parse(noTransport)!!, "http://a/desc.xml"))
        assertNull(XmlNode.parse("not xml"))
    }

    @Test
    fun readsTypedAddresses() {
        assertEquals("192.168.1.42", DlnaParsing.ipv4(" 192.168.1.42 "))
        assertNull(DlnaParsing.ipv4("192.168.1"))
        assertNull(DlnaParsing.ipv4("192.168.1.256"))
        assertNull(DlnaParsing.ipv4("tv.local"))
    }
}

package com.strmr.mobile.cast

import java.net.URI

/** A UPnP MediaRenderer found on the LAN, with the control URLs its description names. */
data class DlnaRenderer(
    /** The device's UDN (`uuid:…`). */
    val udn: String,
    val name: String,
    val model: String?,
    val host: String,
    val avTransport: DlnaService,
    val renderingControl: DlnaService?,
    val connectionManager: DlnaService?,
) {
    val device: CastDevice get() = CastDevice("dlna:$udn", name, model, CastDevice.Kind.DLNA, host)
}

/** An SSDP search reply: where the renderer's description is. */
data class SsdpReply(val location: String, val usn: String)

/** The pure halves of DLNA discovery: the search datagram, its replies and device descriptions. */
object DlnaParsing {
    const val SEARCH_TARGET = "urn:schemas-upnp-org:device:MediaRenderer:1"

    /** The `M-SEARCH` sent to the multicast group, or unicast to a known renderer. */
    fun searchMessage(host: String): String =
        "M-SEARCH * HTTP/1.1\r\nHOST: $host:1900\r\nMAN: \"ssdp:discover\"\r\nMX: 3\r\nST: $SEARCH_TARGET\r\n\r\n"

    /**
     * An `HTTP/1.1 200 OK` search reply (or a `NOTIFY`) for a MediaRenderer, with its http
     * description URL; null for anything else (other device types answer `ssdp:all` probes).
     */
    fun parseReply(datagram: String): SsdpReply? {
        val headers = mutableMapOf<String, String>()
        for (line in datagram.split("\r\n", "\n").drop(1)) {
            val colon = line.indexOf(':')
            if (colon <= 0) continue
            headers[line.substring(0, colon).trim().lowercase()] = line.substring(colon + 1).trim()
        }
        val target = (headers["st"] ?: headers["nt"] ?: "").lowercase()
        val location = headers["location"]?.takeIf { it.startsWith("http://", ignoreCase = true) } ?: return null
        if (!target.contains("mediarenderer")) return null
        return SsdpReply(location, headers["usn"] ?: location)
    }

    /** The first MediaRenderer device in a description (the root device, or one embedded in it). */
    fun renderer(root: XmlNode, location: String): DlnaRenderer? {
        val base = root.childText("URLBase")?.takeIf { it.startsWith("http") } ?: location
        val device = root.descendants("device").firstOrNull {
            it.childText("deviceType")?.lowercase()?.contains("mediarenderer") == true
        } ?: return null
        val services = device.child("serviceList")?.children.orEmpty()
        fun service(name: String): DlnaService? {
            val node = services.firstOrNull { it.childText("serviceType")?.contains(":service:$name:") == true } ?: return null
            val type = node.childText("serviceType") ?: return null
            val path = node.childText("controlURL") ?: return null
            val url = resolve(base, path) ?: return null
            return DlnaService(type, url)
        }
        val avTransport = service("AVTransport") ?: return null
        val host = runCatching { URI(location).host }.getOrNull() ?: return null
        val name = device.childText("friendlyName")?.ifEmpty { null } ?: host
        return DlnaRenderer(
            udn = device.childText("UDN")?.ifEmpty { null } ?: location,
            name = name,
            model = device.childText("modelName")?.ifEmpty { null },
            host = host,
            avTransport = avTransport,
            renderingControl = service("RenderingControl"),
            connectionManager = service("ConnectionManager"),
        )
    }

    /** [path] against [base]; a control URL may be absolute, rooted or relative. */
    fun resolve(base: String, path: String): String? = runCatching {
        if (path.startsWith("http://") || path.startsWith("https://")) path else URI(base).resolve(path).toString()
    }.getOrNull()

    /** A dotted IPv4 address typed by hand (`192.168.1.42`), trimmed; null when it isn't one. */
    fun ipv4(text: String): String? {
        val trimmed = text.trim()
        val parts = trimmed.split('.')
        if (parts.size != 4 || parts.any { p -> p.isEmpty() || p.length > 3 || p.any { !it.isDigit() } || p.toInt() > 255 }) return null
        return trimmed
    }
}

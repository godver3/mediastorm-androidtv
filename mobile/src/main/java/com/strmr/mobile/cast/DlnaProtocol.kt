package com.strmr.mobile.cast

/**
 * DLNA media negotiation: the `protocolInfo` strings offered to a renderer, whether its Sink
 * list takes one, and the DIDL-Lite item that names the media in SetAVTransportURI.
 */
object DlnaProtocol {
    private const val FLAGS = "01700000000000000000000000000000"
    const val HLS_MIME = "application/vnd.apple.mpegurl"

    /** `http-get:*:<mime>:DLNA.ORG_PN=…;DLNA.ORG_OP=…;DLNA.ORG_CI=…;DLNA.ORG_FLAGS=…`. */
    fun protocolInfo(mime: String, profile: String? = null, operation: String = "01", converted: Boolean): String {
        val features = listOfNotNull(
            profile?.let { "DLNA.ORG_PN=$it" }, "DLNA.ORG_OP=$operation", "DLNA.ORG_CI=${if (converted) 1 else 0}", "DLNA.ORG_FLAGS=$FLAGS",
        )
        return "http-get:*:$mime:${features.joinToString(";")}"
    }

    /** The MIME type in a protocolInfo (its third field). */
    fun mime(protocolInfo: String): String = protocolInfo.split(':').getOrNull(2) ?: "video/mp4"

    /** The MIME types to offer for the original file, by its extension, most specific first. */
    fun mimesForPath(path: String): List<String> {
        val name = path.substringBefore('?').substringAfterLast('/')
        return when (name.substringAfterLast('.', "").lowercase()) {
            "mkv" -> listOf("video/x-matroska", "video/x-mkv", "video/mp4")
            "webm" -> listOf("video/webm")
            "mov" -> listOf("video/quicktime", "video/mp4")
            "ts", "m2ts", "mts" -> listOf("video/vnd.dlna.mpeg-tts", "video/mpeg")
            "avi" -> listOf("video/x-msvideo", "video/avi")
            else -> listOf("video/mp4")
        }
    }

    /**
     * Whether a Sink list takes [protocolInfo]: protocol, network and MIME type must match, and a
     * sink naming a DLNA profile must name ours. A renderer that won't list its sinks (null)
     * takes anything; we offer the original container first.
     */
    fun accepts(sinks: List<String>?, protocolInfo: String): Boolean {
        if (sinks == null) return true
        val offered = protocolInfo.split(':', limit = 4)
        if (offered.size != 4) return false
        val offeredMime = offered[2].lowercase()
        val offeredProfile = profile(offered[3])
        return sinks.any { sink ->
            val parts = sink.trim().split(':', limit = 4)
            if (parts.size != 4 || (parts[0] != "*" && parts[0] != "http-get") || parts[1] != "*") return@any false
            val mime = parts[2].lowercase()
            val mimeMatches = mime == "*" || mime == "*/*" || mime == offeredMime ||
                (mime.endsWith("/*") && offeredMime.startsWith(mime.dropLast(1)))
            if (!mimeMatches) return@any false
            profile(parts[3])?.let { it == offeredProfile } ?: true
        }
    }

    private fun profile(features: String): String? =
        features.split(';').firstOrNull { it.startsWith("DLNA.ORG_PN=") }?.removePrefix("DLNA.ORG_PN=")

    /** The ConnectionManager `Sink` value as a list; null when it is empty. */
    fun sinkList(sink: String?): List<String>? =
        sink?.split(',')?.map { it.trim() }?.filter { it.isNotEmpty() }?.ifEmpty { null }

    /** The DIDL-Lite item for SetAVTransportURI's `CurrentURIMetaData`. */
    fun didlLite(title: String, url: String, protocolInfo: String): String {
        val itemClass = if (mime(protocolInfo).startsWith("audio/")) "object.item.audioItem.musicTrack" else "object.item.videoItem"
        return "<?xml version=\"1.0\" encoding=\"UTF-8\"?><DIDL-Lite xmlns=\"urn:schemas-upnp-org:metadata-1-0/DIDL-Lite/\" " +
            "xmlns:dc=\"http://purl.org/dc/elements/1.1/\" xmlns:upnp=\"urn:schemas-upnp-org:metadata-1-0/upnp/\">" +
            "<item id=\"0\" parentID=\"-1\" restricted=\"1\"><dc:title>${DlnaSoap.escape(title)}</dc:title>" +
            "<upnp:class>$itemClass</upnp:class><res protocolInfo=\"${DlnaSoap.escape(protocolInfo)}\">" +
            "${DlnaSoap.escape(url)}</res></item></DIDL-Lite>"
    }
}

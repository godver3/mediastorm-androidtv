package com.strmr.tv.auth

import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull

/**
 * Turns what someone types on the remote into a backend base URL, following the Expo app's
 * rules (`normaliseBackendUrl`): without a scheme, LAN hosts get `http://` and port 7777,
 * anything else `https://` with its own port; a trailing `/api` is dropped because request
 * paths already start with `/api`.
 */
object ServerAddress {
    const val DEFAULT_PORT = 7777

    fun normalize(input: String): HttpUrl? {
        var text = input.filterNot { it.isWhitespace() }
        if (text.isEmpty()) return null
        if (!Regex("^[A-Za-z][A-Za-z0-9+.-]*://").containsMatchIn(text)) {
            val hostPort = text.substringBefore('/')
            val rest = if (text.contains('/')) "/" + text.substringAfter('/') else ""
            val hasPort = hostPort.contains(':')
            val local = isLocal(hostPort.substringBefore(':'))
            val authority = if (hasPort || !local) hostPort else "$hostPort:$DEFAULT_PORT"
            text = "${if (hasPort || local) "http" else "https"}://$authority$rest"
        }
        val url = text.toHttpUrlOrNull() ?: return null
        if (url.host.isEmpty()) return null
        var path = url.encodedPath.trimEnd('/')
        if (path.endsWith("/api", ignoreCase = true)) path = path.dropLast(4)
        return url.newBuilder()
            .encodedPath(if (path.isEmpty()) "/" else path)
            .query(null).fragment(null).username("").password("")
            .build()
    }

    /** Private IPv4 ranges, localhost, `.local` and bare (dot-less) hostnames. */
    fun isLocal(host: String): Boolean {
        val h = host.lowercase()
        if (h == "localhost" || h.endsWith(".local") || !h.contains('.')) return true
        val octets = h.split('.').mapNotNull { it.toIntOrNull() }
        if (octets.size != 4) return false
        return when {
            octets[0] == 10 || octets[0] == 127 -> true
            octets[0] == 192 && octets[1] == 168 -> true
            octets[0] == 172 && octets[1] in 16..31 -> true
            else -> false
        }
    }

    /** Short form for display: drops the scheme and a trailing slash. */
    fun display(url: HttpUrl): String =
        url.toString().removePrefix("http://").removePrefix("https://").trimEnd('/')
}

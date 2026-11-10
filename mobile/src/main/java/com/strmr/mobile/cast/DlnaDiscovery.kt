package com.strmr.mobile.cast

import android.content.Context
import android.net.wifi.WifiManager
import com.strmr.tv.app.App
import com.strmr.tv.app.AppLog
import java.net.DatagramPacket
import java.net.InetAddress
import java.net.MulticastSocket
import java.net.SocketTimeoutException
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withContext
import okhttp3.Request

/**
 * SSDP discovery, as the Expo and iPhone apps do it: an `M-SEARCH` for MediaRenderer:1 to the
 * multicast group with `MX: 3`, plus the same search sent unicast to every renderer seen before
 * (and to an address typed in by hand), then each reply's description fetched for its name and
 * AVTransport / RenderingControl URLs. Wi-Fi drops multicast replies unless a MulticastLock is
 * held, so one is held for the search window.
 */
object DlnaDiscovery {
    private const val TAG = "DLNA"
    private const val GROUP = "239.255.255.250"
    private const val PREFS = "cast"
    private const val KNOWN_HOSTS = "dlna.knownHosts"
    /** Replies spread over the `MX: 3` window; hanging up earlier drops late ones. */
    private const val WINDOW_MS = 3_000L

    private val prefs get() = App.context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    /** Renderers seen before, probed by unicast on every search. */
    var knownHosts: Set<String>
        get() = prefs.getStringSet(KNOWN_HOSTS, emptySet()).orEmpty().toSet()
        private set(value) = prefs.edit().putStringSet(KNOWN_HOSTS, value).apply()

    /** Searches the network and returns every renderer whose description could be read. */
    suspend fun discover(): List<DlnaRenderer> {
        val replies = search(multicast = true, unicast = knownHosts.toList())
        val renderers = describe(replies)
        if (renderers.isNotEmpty()) knownHosts = knownHosts + renderers.map { it.host }
        return renderers
    }

    /** A renderer at an address typed in by hand. */
    suspend fun probe(host: String): DlnaRenderer {
        val address = DlnaParsing.ipv4(host) ?: throw CastException("Enter an IPv4 address, such as 192.168.1.42.")
        val renderer = describe(search(multicast = false, unicast = listOf(address))).firstOrNull()
            ?: throw CastException("No DLNA renderer answered at $address.")
        knownHosts = knownHosts + renderer.host
        return renderer
    }

    private suspend fun search(multicast: Boolean, unicast: List<String>): Set<SsdpReply> = withContext(Dispatchers.IO) {
        val wifi = App.context.applicationContext.getSystemService(Context.WIFI_SERVICE) as? WifiManager
        val lock = wifi?.createMulticastLock("strmr-ssdp")?.apply { setReferenceCounted(false) }
        runCatching { lock?.acquire() }
        try {
            runSearch(multicast, unicast)
        } finally {
            runCatching { lock?.release() }
        }
    }

    /** Blocking: one UDP socket, the sends (again a second in: UDP is best effort), then replies until the window closes. */
    private fun runSearch(multicast: Boolean, unicast: List<String>): Set<SsdpReply> {
        val replies = mutableSetOf<SsdpReply>()
        runCatching {
            MulticastSocket(null).use { socket ->
                socket.reuseAddress = true
                socket.bind(null)
                socket.timeToLive = 2
                socket.soTimeout = 200
                fun send(host: String) {
                    val bytes = DlnaParsing.searchMessage(host).toByteArray(Charsets.US_ASCII)
                    runCatching { socket.send(DatagramPacket(bytes, bytes.size, InetAddress.getByName(host), 1900)) }
                        .onFailure { AppLog.i(TAG, "SSDP send to $host failed: ${it.message}") }
                }
                fun sendAll() {
                    if (multicast) send(GROUP)
                    unicast.forEach(::send)
                }
                sendAll()
                var resent = false
                val start = System.currentTimeMillis()
                val buffer = ByteArray(8192)
                while (System.currentTimeMillis() - start < WINDOW_MS && replies.size < 64) {
                    if (!resent && System.currentTimeMillis() - start > 1_000) {
                        resent = true
                        sendAll()
                    }
                    val packet = DatagramPacket(buffer, buffer.size)
                    try {
                        socket.receive(packet)
                    } catch (_: SocketTimeoutException) {
                        continue
                    }
                    DlnaParsing.parseReply(String(packet.data, 0, packet.length, Charsets.UTF_8))?.let { replies += it }
                }
            }
        }.onFailure { AppLog.w(TAG, "SSDP search failed: ${it.message}") }
        return replies
    }

    /** One renderer per device: one answering on two interfaces (or twice) is listed once. */
    private suspend fun describe(replies: Set<SsdpReply>): List<DlnaRenderer> = coroutineScope {
        replies.map { it.location }.distinct()
            .map { location -> async(Dispatchers.IO) { description(location) } }
            .awaitAll().filterNotNull()
            .distinctBy { it.udn }
            .sortedBy { it.name.lowercase() }
    }

    private fun description(location: String): DlnaRenderer? = runCatching {
        val client = DlnaSoap.http.newBuilder().callTimeout(5, TimeUnit.SECONDS).build()
        val request = Request.Builder().url(location).header("User-Agent", DlnaSoap.USER_AGENT).build()
        val body = client.newCall(request).execute().use { it.body?.bytes() } ?: return null
        val root = XmlNode.parse(body) ?: return null
        DlnaParsing.renderer(root, location)
    }.onFailure { AppLog.i(TAG, "description unreadable at $location: ${it.message}") }.getOrNull()
}

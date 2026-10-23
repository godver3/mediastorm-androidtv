package com.strmr.tv.auth

import android.content.Context
import android.net.nsd.NsdManager
import android.net.nsd.NsdServiceInfo
import android.net.wifi.WifiManager
import android.os.Build
import com.strmr.tv.app.App
import com.strmr.tv.app.AppLog
import java.net.Inet4Address
import java.util.concurrent.Executors
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import okhttp3.HttpUrl

/** A MediaStorm server advertised over mDNS (`_mediastorm._tcp`), resolved to an address. */
data class DiscoveredServer(
    val id: String,
    /** TXT `name`, else the service instance name. */
    val name: String,
    /** TXT `version`. */
    val version: String?,
    /** `http://<IPv4>:<port><apiPath minus /api>`. */
    val baseUrl: HttpUrl,
)

/**
 * Browses the LAN for MediaStorm servers while the server screen is up (NsdManager, the
 * counterpart of tvOS Bonjour). Each service is resolved as it appears, so a pick connects
 * straight away. Holds a multicast lock while browsing: some boxes drop mDNS replies without.
 */
class ServerBrowser {
    private val nsd = App.context.getSystemService(Context.NSD_SERVICE) as NsdManager
    private val _servers = MutableStateFlow<List<DiscoveredServer>>(emptyList())
    val servers: StateFlow<List<DiscoveredServer>> = _servers

    private var listener: NsdManager.DiscoveryListener? = null
    private var multicastLock: WifiManager.MulticastLock? = null
    private val executor = Executors.newSingleThreadExecutor()
    private val callbacks = mutableMapOf<String, NsdManager.ServiceInfoCallback>()

    fun start() {
        if (listener != null) return
        multicastLock = (App.context.applicationContext.getSystemService(Context.WIFI_SERVICE) as? WifiManager)
            ?.createMulticastLock("strmr-nsd")?.apply { setReferenceCounted(false); runCatching { acquire() } }
        val l = object : NsdManager.DiscoveryListener {
            override fun onDiscoveryStarted(serviceType: String) = Unit
            override fun onDiscoveryStopped(serviceType: String) = Unit
            override fun onStartDiscoveryFailed(serviceType: String, errorCode: Int) {
                // Manual entry still works; stay quiet on screen.
                AppLog.w("ServerBrowser", "discovery failed: $errorCode")
            }
            override fun onStopDiscoveryFailed(serviceType: String, errorCode: Int) = Unit
            override fun onServiceFound(info: NsdServiceInfo) = resolve(info)
            override fun onServiceLost(info: NsdServiceInfo) {
                _servers.update { list -> list.filterNot { it.id == info.serviceName } }
                if (Build.VERSION.SDK_INT >= 34) callbacks.remove(info.serviceName)?.let { runCatching { nsd.unregisterServiceInfoCallback(it) } }
            }
        }
        listener = l
        runCatching { nsd.discoverServices(SERVICE_TYPE, NsdManager.PROTOCOL_DNS_SD, l) }
            .onFailure { AppLog.w("ServerBrowser", "discoverServices: ${it.message}") }
    }

    fun stop() {
        listener?.let { runCatching { nsd.stopServiceDiscovery(it) } }
        listener = null
        if (Build.VERSION.SDK_INT >= 34) callbacks.values.forEach { runCatching { nsd.unregisterServiceInfoCallback(it) } }
        callbacks.clear()
        runCatching { multicastLock?.release() }
        multicastLock = null
    }

    private fun resolve(info: NsdServiceInfo) {
        if (Build.VERSION.SDK_INT >= 34) {
            val callback = object : NsdManager.ServiceInfoCallback {
                override fun onServiceInfoCallbackRegistrationFailed(errorCode: Int) = Unit
                override fun onServiceUpdated(resolved: NsdServiceInfo) = publish(resolved)
                override fun onServiceLost() = Unit
                override fun onServiceInfoCallbackUnregistered() = Unit
            }
            callbacks[info.serviceName] = callback
            runCatching { nsd.registerServiceInfoCallback(info, executor, callback) }
        } else {
            // Older releases resolve one service at a time; queue them on one thread.
            executor.execute {
                val done = java.util.concurrent.CountDownLatch(1)
                @Suppress("DEPRECATION")
                runCatching {
                    nsd.resolveService(info, object : NsdManager.ResolveListener {
                        override fun onResolveFailed(serviceInfo: NsdServiceInfo, errorCode: Int) = done.countDown()
                        override fun onServiceResolved(serviceInfo: NsdServiceInfo) {
                            publish(serviceInfo)
                            done.countDown()
                        }
                    })
                }.onFailure { done.countDown() }
                done.await(5, java.util.concurrent.TimeUnit.SECONDS)
            }
        }
    }

    private fun publish(info: NsdServiceInfo) {
        val txt = info.attributes.mapValues { (_, v) -> v?.toString(Charsets.UTF_8)?.trim().orEmpty() }
        fun value(key: String) = txt[key]?.ifEmpty { null }
        @Suppress("DEPRECATION")
        val hosts = if (Build.VERSION.SDK_INT >= 34) info.hostAddresses else listOfNotNull(info.host)
        // Prefer IPv4: link-local IPv6 needs a zone that URLs handle badly.
        val host = (hosts.firstOrNull { it is Inet4Address } ?: hosts.firstOrNull())?.hostAddress?.substringBefore('%') ?: return
        var apiPath = value("apiPath") ?: "/api"
        if (!apiPath.startsWith("/")) apiPath = "/$apiPath"
        val authority = if (host.contains(':')) "[$host]" else host
        val url = ServerAddress.normalize("http://$authority:${info.port}$apiPath") ?: return
        val server = DiscoveredServer(info.serviceName, value("name") ?: info.serviceName, value("version"), url)
        _servers.update { list -> (list.filterNot { it.id == server.id } + server).sortedBy { it.name.lowercase() } }
    }

    private companion object {
        const val SERVICE_TYPE = "_mediastorm._tcp"
    }
}

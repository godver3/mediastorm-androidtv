package com.strmr.tv.api

import com.strmr.tv.app.AppLog
import com.strmr.tv.app.Config
import com.strmr.tv.player.DisplayCapabilities
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put

@Serializable
private data class Filtering(val adaptivePlaybackEnabled: Boolean? = null)

@Serializable
private data class FilteringSettings(val filtering: Filtering? = null)

/**
 * Reports the display's HDR / Dolby Vision support into this device's client settings
 * (`adaptivePlayback.displayHdr` / `displayDv`), as the Expo app's `reportAdaptiveMeasurements`
 * does, so adaptive playback leaves out releases the panel can't show. Runs only while the
 * profile's (else the server's) `filtering.adaptivePlaybackEnabled` is on, and writes only
 * when a value changed.
 *
 * The server replaces client settings whole, so the stored object is read and written back as
 * raw JSON: every field this app doesn't model (admin overrides) survives the round trip. The
 * device must be registered first.
 */
suspend fun ApiClient.reportDisplayCapabilities() {
    if (!adaptivePlaybackEnabled()) return
    val caps = DisplayCapabilities.current()
    val path = "/api/clients/${segment(Config.clientId)}/settings"
    val query = mapOf("userId" to profileId)
    try {
        val stored = json.parseToJsonElement(sendRaw("GET", path, query, promptOnLock = false, timeoutMs = 10_000)) as? JsonObject
            ?: JsonObject(emptyMap())
        val adaptive = stored["adaptivePlayback"] as? JsonObject
        fun flag(key: String) = runCatching { adaptive?.get(key)?.jsonPrimitive?.booleanOrNull }.getOrNull()
        if (flag("displayHdr") == caps.hdr && flag("displayDv") == caps.dolbyVision) return
        // Throughput is never stored here (it rides on searches), so the object is replaced.
        val updated = JsonObject(stored + ("adaptivePlayback" to buildJsonObject {
            put("displayHdr", caps.hdr)
            put("displayDv", caps.dolbyVision)
        }))
        sendRaw("PUT", path, query, body = updated, promptOnLock = false, timeoutMs = 10_000)
        AppLog.i("Capabilities", "display reported hdr=${caps.hdr} dv=${caps.dolbyVision} types=${caps.hdrTypes}")
    } catch (e: Exception) {
        AppLog.w("Capabilities", "display report failed: ${e.message}")
    }
}

/** `filtering.adaptivePlaybackEnabled`: the profile's value, else the server's. */
suspend fun ApiClient.adaptivePlaybackEnabled(): Boolean = coroutineScope {
    val user = async {
        runCatching { send<FilteringSettings>("GET", "$profilePath/settings", promptOnLock = false, timeoutMs = 10_000) }.getOrNull()
    }
    val server = async { runCatching { send<FilteringSettings>("GET", "/api/settings", timeoutMs = 10_000) }.getOrNull() }
    user.await()?.filtering?.adaptivePlaybackEnabled ?: server.await()?.filtering?.adaptivePlaybackEnabled ?: false
}

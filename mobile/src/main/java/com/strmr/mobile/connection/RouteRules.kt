package com.strmr.mobile.connection

import java.util.Base64
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonPrimitive
import okhttp3.HttpUrl

/**
 * The connection's decisions, pure for tests (the iPhone app's `MobileConnectionSelector` and
 * `MobileConnection` rules).
 */
object RouteRules {
    /** A typed server's choice: which address, and whether the phone is home (null: can't tell). */
    data class DirectChoice(val isHome: Boolean?, val target: HttpUrl?)

    /**
     * A typed server with a home network configured: on cellular the away address (the home
     * LAN address can't answer there, even mid-handover); on the home Wi-Fi the home address,
     * on another Wi-Fi the away one; on Wi-Fi with an unreadable name the last name-based
     * answer from the past five minutes ([recentHome]), else no change (null target).
     */
    fun direct(settings: NetworkSettings, onCellular: Boolean, ssid: String?, recentHome: Boolean?): DirectChoice = when {
        onCellular -> DirectChoice(false, settings.remoteUrl)
        ssid != null -> {
            val home = ssid.equals(settings.homeWifiSsid, ignoreCase = true)
            DirectChoice(home, if (home) settings.homeUrl else settings.remoteUrl)
        }
        recentHome != null -> DirectChoice(recentHome, if (recentHome) settings.homeUrl else settings.remoteUrl)
        else -> DirectChoice(null, null)
    }

    /**
     * Paired over iroh: whether to probe the home address. Never on cellular; a readable Wi-Fi
     * name that isn't home's skips it; an unreadable one still probes (the probe decides).
     */
    fun shouldProbe(onCellular: Boolean, ssid: String?, homeSsid: String): Boolean =
        !onCellular && (ssid == null || ssid.equals(homeSsid, ignoreCase = true))

    /** A saved name-based answer, if it is from the past five minutes. */
    fun recentDetection(home: Boolean?, at: Long, now: Long): Boolean? = home?.takeIf { now - at in 0 until 300_000 }

    /** The iroh endpoint id inside an invite (`mshost-iroh-` + base64url JSON with a 64-hex `id`). */
    fun serverId(invite: String): String? {
        if (!invite.startsWith("mshost-iroh-")) return null
        val encoded = invite.removePrefix("mshost-iroh-").trimEnd('=')
        val bytes = runCatching { Base64.getUrlDecoder().decode(encoded) }.getOrNull() ?: return null
        val json = runCatching { Json.parseToJsonElement(String(bytes)) as? JsonObject }.getOrNull() ?: return null
        val id = runCatching { json["id"]?.jsonPrimitive?.contentOrNull }.getOrNull() ?: return null
        return id.lowercase().takeIf { it.length == 64 && it.all { c -> c in '0'..'9' || c in 'a'..'f' } }
    }

    /** Whether the identity answer names the paired server. */
    fun identityMatches(serverId: String?, expected: String): Boolean = serverId?.lowercase() == expected.lowercase()

    /** Seconds before each reconnect attempt: quick at first, then every 30 s. */
    val backoffSeconds = listOf(2L, 4L, 8L, 15L, 30L)

    fun backoffMs(attempt: Int): Long = backoffSeconds[attempt.coerceIn(0, backoffSeconds.lastIndex)] * 1000

    /** Reasons that may move a route while a title plays (recovery); others wait for the player to close. */
    fun mayMoveWhilePlaying(reason: String): Boolean = reason in setOf("startup", "retry", "network failure")
}

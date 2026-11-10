package com.strmr.tv.adaptive

import kotlin.math.roundToLong
import kotlinx.serialization.Serializable
import kotlinx.serialization.SerialName
import kotlinx.serialization.json.Json

/**
 * Where an estimate came from, as Expo's `AdaptiveThroughputSource`: Settings' speed test
 * (`manual`) or the automatic idle test (`idle`).
 */
@Serializable
enum class ThroughputSource {
    @SerialName("manual") MANUAL,
    @SerialName("idle") IDLE,
}

/** One route's client → server throughput, Expo's `AdaptiveThroughputContext`. `measuredAt` is Unix seconds. */
@Serializable
data class ThroughputEstimate(
    val measuredMbps: Double,
    val measuredAt: Long,
    val source: ThroughputSource,
)

/**
 * The rules of Expo's `services/adaptive-throughput.ts`, kept pure for tests: the route key,
 * the two-hour freshness window, rounding to 0.1 Mbps and the stored JSON
 * (`{"version":2,"routes":{…}}`, the same shape under the same key).
 */
object ThroughputRules {
    const val STORAGE_KEY = "strmr.adaptive.throughput.v2"
    /** Follows Wi-Fi / route changes without a benchmark on every wake; the server rejects data after 24 h itself. */
    const val TTL_MS = 2L * 60 * 60 * 1000
    /** Every request over the iroh bridge shares one route, whatever the proxy's port. */
    const val IROH_ROUTE = "transport:iroh"

    private val json = Json { ignoreUnknownKeys = true; coerceInputValues = true }

    @Serializable
    private data class Stored(val version: Int = 0, val routes: Map<String, ThroughputEstimate> = emptyMap())

    /** The server's base URL without trailing slashes, or [IROH_ROUTE] over remote access. */
    fun routeKey(baseUrl: String, overIroh: Boolean): String =
        if (overIroh) IROH_ROUTE else baseUrl.trim().trimEnd('/')

    /** Positive, finite, not from the future, and no older than [TTL_MS]. */
    fun isFresh(estimate: ThroughputEstimate?, nowMs: Long): Boolean {
        if (estimate == null) return false
        if (!estimate.measuredMbps.isFinite() || estimate.measuredMbps <= 0 || estimate.measuredAt <= 0) return false
        val ageMs = nowMs - estimate.measuredAt * 1000
        return ageMs in 0..TTL_MS
    }

    /** Expo's `Math.round(mbps * 10) / 10`. */
    fun rounded(mbps: Double): Double = (mbps * 10).roundToLong() / 10.0

    /** The header value: "87.5", or "120" for a whole number, as JavaScript's `String(n)` prints it. */
    fun headerValue(mbps: Double): String =
        if (mbps == Math.floor(mbps) && !mbps.isInfinite()) mbps.toLong().toString() else mbps.toString()

    fun decode(raw: String?): Map<String, ThroughputEstimate> {
        if (raw.isNullOrBlank()) return emptyMap()
        val stored = runCatching { json.decodeFromString<Stored>(raw) }.getOrNull() ?: return emptyMap()
        return if (stored.version == 2) stored.routes else emptyMap()
    }

    fun encode(routes: Map<String, ThroughputEstimate>): String = json.encodeToString(Stored.serializer(), Stored(2, routes))
}

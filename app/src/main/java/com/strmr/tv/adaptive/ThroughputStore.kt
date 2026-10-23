package com.strmr.tv.adaptive

import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.setValue
import com.strmr.tv.api.ApiClient
import com.strmr.tv.app.App
import com.strmr.tv.app.AppLog

/**
 * This device's throughput estimate per server route, persisted (Expo keeps it in
 * AsyncStorage under the same key). It never goes into client settings: it rides on the
 * search and prequeue requests as `X-Adaptive-Throughput-*` headers ([headers]).
 */
object ThroughputStore {
    private val prefs by lazy { App.context.getSharedPreferences("adaptive", Context.MODE_PRIVATE) }
    /** Bumped on every save, so Settings re-reads the estimate. */
    var revision by mutableIntStateOf(0)
        private set

    /** The current route: the iroh bridge, or the server's address. */
    val routeKey: String get() = ThroughputRules.routeKey(ApiClient.baseUrl.toString(), ApiClient.remoteCredential != null)

    /** The route's estimate, fresh or not (Settings shows its age). */
    fun stored(route: String = routeKey): ThroughputEstimate? =
        ThroughputRules.decode(prefs.getString(ThroughputRules.STORAGE_KEY, null))[route]

    /** The route's estimate while it is fresh, Expo's `getAdaptiveThroughputContext`. */
    fun fresh(route: String = routeKey, log: Boolean = true): ThroughputEstimate? {
        val estimate = stored(route)
        val valid = ThroughputRules.isFresh(estimate, System.currentTimeMillis())
        if (log) {
            val age = estimate?.let { System.currentTimeMillis() / 1000 - it.measuredAt }
            AppLog.i("Adaptive", "route estimate lookup route=$route found=${estimate != null} valid=$valid " +
                "ageS=$age mbps=${estimate?.measuredMbps} source=${estimate?.source}")
        }
        return if (valid) estimate else null
    }

    fun save(mbps: Double, source: ThroughputSource, route: String = routeKey): ThroughputEstimate {
        require(mbps.isFinite() && mbps > 0) { "Adaptive throughput must be a positive finite number" }
        val estimate = ThroughputEstimate(ThroughputRules.rounded(mbps), System.currentTimeMillis() / 1000, source)
        synchronized(this) {
            val routes = ThroughputRules.decode(prefs.getString(ThroughputRules.STORAGE_KEY, null)) + (route to estimate)
            prefs.edit().putString(ThroughputRules.STORAGE_KEY, ThroughputRules.encode(routes)).apply()
        }
        revision += 1
        AppLog.i("Adaptive", "saved route estimate route=$route mbps=${estimate.measuredMbps} " +
            "measuredAt=${estimate.measuredAt} source=${estimate.source}")
        return estimate
    }

    /**
     * The headers Expo sends on `/api/indexers/search` and `/api/playback/prequeue`: only while
     * the route has a fresh estimate (the server applies its caps only then).
     */
    fun headers(): Map<String, String> {
        val estimate = fresh() ?: return emptyMap()
        return mapOf(
            "X-Adaptive-Throughput-Mbps" to ThroughputRules.headerValue(estimate.measuredMbps),
            "X-Adaptive-Throughput-Measured-At" to estimate.measuredAt.toString(),
        )
    }
}

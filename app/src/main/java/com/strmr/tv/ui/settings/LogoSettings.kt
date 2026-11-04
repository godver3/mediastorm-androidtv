package com.strmr.tv.ui.settings

import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.strmr.tv.app.App

/**
 * Settings › Home › Home / Details Title Logo Size: scales the title logos on Home (Focused
 * Title Info and Top Carousel) and, separately, on details pages. Device only; 100% by default.
 */
object LogoSettings {
    enum class Scale(val percent: Int) {
        P25(25), P50(50), P75(75), P100(100), P125(125), P150(150);

        val label get() = "$percent%"
        val factor get() = percent / 100f
    }

    /** Where a title logo is drawn, each with its own size. */
    enum class Place { HOME, DETAILS }

    private val prefs by lazy { App.context.getSharedPreferences("strmr", Context.MODE_PRIVATE) }
    /** The Home size; before details had its own, it sized both. */
    private const val KEY = "logoScale"
    private const val DETAILS_KEY = "detailsLogoScale"

    private fun saved(key: String, default: Int) = Scale.entries.firstOrNull { it.percent == prefs.getInt(key, default) } ?: Scale.P100

    var scale by mutableStateOf(saved(KEY, 100))
        private set
    /** Unset: keeps the size the shared setting gave details pages. */
    var detailsScale by mutableStateOf(saved(DETAILS_KEY, scale.percent))
        private set

    fun scale(place: Place) = if (place == Place.DETAILS) detailsScale else scale

    fun set(value: Scale) {
        scale = value
        prefs.edit().putInt(KEY, value.percent).apply()
    }

    fun setDetails(value: Scale) {
        detailsScale = value
        prefs.edit().putInt(DETAILS_KEY, value.percent).apply()
    }
}

package com.strmr.tv.ui.settings

import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.strmr.tv.app.App

/**
 * Settings › Home › Title Logo Size: scales the title logos on Home (Focused Title Info and
 * Top Carousel) and on details pages. Device only; 100% by default.
 */
object LogoSettings {
    enum class Scale(val percent: Int) {
        P25(25), P50(50), P75(75), P100(100), P125(125);

        val label get() = "$percent%"
        val factor get() = percent / 100f
    }

    private val prefs by lazy { App.context.getSharedPreferences("strmr", Context.MODE_PRIVATE) }
    private const val KEY = "logoScale"

    var scale by mutableStateOf(Scale.entries.firstOrNull { it.percent == prefs.getInt(KEY, 100) } ?: Scale.P100)
        private set

    fun set(value: Scale) {
        scale = value
        prefs.edit().putInt(KEY, value.percent).apply()
    }
}

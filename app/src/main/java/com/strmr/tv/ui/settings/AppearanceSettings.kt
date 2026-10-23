package com.strmr.tv.ui.settings

import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.Color
import com.strmr.tv.app.App

/**
 * Settings › Appearance › Background: the colour behind the tabs other than Home and behind
 * pages, on this device. Graphite is the tvOS app's page grey.
 */
object AppearanceSettings {
    enum class Background(val label: String, val color: Color) {
        GRAPHITE("Graphite", Color(0xFF1C1C1C)),
        SLATE("Slate", Color(0xFF1E2228)),
        MIDNIGHT("Midnight", Color(0xFF0D0F12)),
        NAVY("Navy", Color(0xFF111A2B)),
        PLUM("Plum", Color(0xFF1D1422)),
        FOREST("Forest", Color(0xFF111C16)),
        BLACK("Black", Color(0xFF000000)),
    }

    private val prefs by lazy { App.context.getSharedPreferences("strmr", Context.MODE_PRIVATE) }
    private const val KEY = "pageBackground"

    var background by mutableStateOf(
        prefs.getString(KEY, null)?.let { name -> Background.entries.firstOrNull { it.name == name } } ?: Background.GRAPHITE,
    )
        private set

    fun choose(value: Background) {
        background = value
        prefs.edit().putString(KEY, value.name).apply()
    }
}

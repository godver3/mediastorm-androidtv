package com.strmr.tv.player

import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.strmr.tv.app.App
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/**
 * How text subtitles are drawn on this device: the server's style (the Expo app's
 * `playback.subtitle*` settings) with any setting this device overrides, as on tvOS.
 */
@Serializable
data class SubtitleStyle(
    /** Scale of the base text size ([BASE_POINTS] at 1.0); bitmap cues scale by it too. */
    val size: Double = 1.0,
    /** Text colour as `#RRGGBB`. */
    val textColor: String = "#FFFFFF",
    val textOpacity: Double = 1.0,
    val bold: Boolean = true,
    val edge: Edge = Edge.SHADOW,
    val outlineColor: String = "#000000",
    /** 0...1, as the backend's `subtitleOutlineWeight`. */
    val outlineWeight: Double = 0.35,
    /** Box behind the text; 0 = no box. */
    val backgroundOpacity: Double = 0.0,
    val backgroundColor: String = "#000000",
    /** Distance of the text from the bottom of the video, in 1080p points (tvOS units). */
    val bottomMargin: Double = 110.0,
) {
    enum class Edge(val label: String) { NONE("None"), SHADOW("Drop Shadow"), OUTLINE("Outline") }

    /** The server's style: the outline / box map directly; with neither the text keeps a drop shadow. */
    constructor(s: Server) : this(
        size = s.subtitleSize?.takeIf { it > 0 }?.coerceIn(0.5, 2.5) ?: 1.0,
        textColor = s.subtitleColor?.takeIf(::isHex)?.uppercase() ?: "#FFFFFF",
        textOpacity = s.subtitleOpacity?.takeIf { it > 0 }?.coerceAtMost(1.0) ?: 1.0,
        bold = s.subtitleBold ?: true,
        edge = if (s.subtitleOutlineEnabled == true) Edge.OUTLINE else Edge.SHADOW,
        outlineColor = s.subtitleOutlineColor?.takeIf(::isHex)?.uppercase() ?: "#000000",
        outlineWeight = s.subtitleOutlineWeight?.takeIf { it > 0 }?.coerceAtMost(1.0) ?: 0.35,
        backgroundOpacity = if (s.subtitleBackgroundEnabled == true) (s.subtitleBackgroundOpacity ?: 0.6).coerceIn(0.0, 1.0) else 0.0,
        backgroundColor = s.subtitleBackgroundColor?.takeIf(::isHex)?.uppercase() ?: "#000000",
    )

    /** The backend's `playback.subtitle*` fields (Expo app settings). */
    @Serializable
    data class Server(
        val subtitleSize: Double? = null,
        val subtitleColor: String? = null,
        val subtitleOpacity: Double? = null,
        val subtitleBold: Boolean? = null,
        val subtitleOutlineEnabled: Boolean? = null,
        val subtitleOutlineColor: String? = null,
        val subtitleOutlineWeight: Double? = null,
        val subtitleBackgroundEnabled: Boolean? = null,
        val subtitleBackgroundColor: String? = null,
        val subtitleBackgroundOpacity: Double? = null,
    ) {
        /** Whether the server sent any style at all (older servers send none). */
        val isEmpty: Boolean
            get() = subtitleSize == null && subtitleColor == null && subtitleBackgroundEnabled == null && subtitleOutlineEnabled == null

        /** `this` with `fallback` filling every field it leaves out (device override over profile). */
        fun mergedOver(fallback: Server?): Server = if (fallback == null) this else Server(
            subtitleSize ?: fallback.subtitleSize,
            subtitleColor ?: fallback.subtitleColor,
            subtitleOpacity ?: fallback.subtitleOpacity,
            subtitleBold ?: fallback.subtitleBold,
            subtitleOutlineEnabled ?: fallback.subtitleOutlineEnabled,
            subtitleOutlineColor ?: fallback.subtitleOutlineColor,
            subtitleOutlineWeight ?: fallback.subtitleOutlineWeight,
            subtitleBackgroundEnabled ?: fallback.subtitleBackgroundEnabled,
            subtitleBackgroundColor ?: fallback.subtitleBackgroundColor,
            subtitleBackgroundOpacity ?: fallback.subtitleBackgroundOpacity,
        )
    }

    /** The settings this device sets for itself; null follows the server's. */
    @Serializable
    data class Overrides(
        val size: Double? = null,
        val textColor: String? = null,
        val textOpacity: Double? = null,
        val bold: Boolean? = null,
        val edge: Edge? = null,
        val outlineColor: String? = null,
        val outlineWeight: Double? = null,
        val backgroundOpacity: Double? = null,
        val backgroundColor: String? = null,
        val bottomMargin: Double? = null,
    ) {
        val isEmpty: Boolean get() = this == Overrides()

        fun appliedTo(base: SubtitleStyle) = SubtitleStyle(
            size ?: base.size, textColor ?: base.textColor, textOpacity ?: base.textOpacity, bold ?: base.bold,
            edge ?: base.edge, outlineColor ?: base.outlineColor, outlineWeight ?: base.outlineWeight, backgroundOpacity ?: base.backgroundOpacity,
            backgroundColor ?: base.backgroundColor, bottomMargin ?: base.bottomMargin,
        )
    }

    companion object {
        /** Text size at 1.0, in points of a 1080-point-high frame (the tvOS and Expo tvOS baseline). */
        const val BASE_POINTS = 62f

        // Choices offered in Settings and the player's quick menu (the tvOS sets).
        val sizes = listOf(0.6, 0.8, 1.0, 1.25, 1.5, 2.0)
        val opacities = listOf(1.0, 0.75, 0.5)
        val backgroundOpacities = listOf(0.0, 0.25, 0.5, 0.75, 1.0)
        val outlineWeights = listOf(0.2, 0.35, 0.6)
        val margins = listOf(60.0, 110.0, 200.0, 320.0)
        val palette = listOf(
            "White" to "#FFFFFF", "Yellow" to "#FFFF00", "Cyan" to "#00FFFF", "Green" to "#00FF00",
            "Magenta" to "#FF00FF", "Red" to "#FF0000", "Blue" to "#0000FF", "Grey" to "#808080", "Black" to "#000000",
        )

        fun sizeName(size: Double) = when {
            size < 0.7 -> "Extra Small"
            size < 0.9 -> "Small"
            size < 1.1 -> "Medium"
            size < 1.35 -> "Large"
            size < 1.75 -> "Extra Large"
            else -> "Huge"
        }

        fun marginName(margin: Double) = when {
            margin < 80 -> "Bottom"
            margin < 150 -> "Default"
            margin < 250 -> "Raised"
            else -> "High"
        }

        fun weightName(weight: Double) = when {
            weight < 0.3 -> "Thin"
            weight < 0.5 -> "Medium"
            else -> "Thick"
        }

        fun colorName(hex: String) = palette.firstOrNull { it.second.equals(hex, ignoreCase = true) }?.first ?: hex.uppercase()

        fun percent(value: Double) = if (value <= 0) "Off" else "${Math.round(value * 100)}%"

        fun isHex(hex: String): Boolean {
            val s = hex.removePrefix("#")
            return s.length == 6 && s.toIntOrNull(16) != null
        }

        /** `#RRGGBB` with `opacity` as an ARGB int; white when malformed. */
        fun argb(hex: String, opacity: Double): Int {
            val rgb = hex.removePrefix("#").takeIf { it.length == 6 }?.toIntOrNull(16) ?: 0xFFFFFF
            return (Math.round(opacity.coerceIn(0.0, 1.0) * 255).toInt() shl 24) or rgb
        }
    }
}

/**
 * The device's subtitle style: the server's (this device's client settings, then the profile's
 * `playback.subtitle*`), with each setting this device overrides on top. Settings and the
 * player's Style menu write it; the player's subtitle view follows [style] live.
 */
object SubtitleStyleStore {
    private val prefs by lazy { App.context.getSharedPreferences("strmr", Context.MODE_PRIVATE) }
    private const val OVERRIDES_KEY = "subtitleStyleOverrides"
    private const val SERVER_KEY = "subtitleStyleServer"
    private val json = Json { ignoreUnknownKeys = true }

    /** The server's style as last read; the app's default before that or from servers that send none. */
    var server by mutableStateOf(read(SERVER_KEY) ?: SubtitleStyle())
        private set
    var overrides by mutableStateOf(read(OVERRIDES_KEY) ?: SubtitleStyle.Overrides())
        private set

    /** The style drawn. */
    val style: SubtitleStyle get() = overrides.appliedTo(server)

    /** Takes the server's style; settings this device doesn't override follow it. */
    fun adopt(value: SubtitleStyle.Server?) {
        if (value == null || value.isEmpty) return
        val next = SubtitleStyle(value)
        if (next == server) return
        server = next
        prefs.edit().putString(SERVER_KEY, json.encodeToString(SubtitleStyle.serializer(), next)).apply()
    }

    fun update(transform: (SubtitleStyle.Overrides) -> SubtitleStyle.Overrides) {
        overrides = transform(overrides)
        prefs.edit().putString(OVERRIDES_KEY, json.encodeToString(SubtitleStyle.Overrides.serializer(), overrides)).apply()
    }

    /** Every setting follows the server again. */
    fun reset() = update { SubtitleStyle.Overrides() }

    private inline fun <reified T> read(key: String): T? =
        prefs.getString(key, null)?.let { runCatching { json.decodeFromString<T>(it) }.getOrNull() }
}

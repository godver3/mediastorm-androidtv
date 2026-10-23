package com.strmr.tv.player.dv7

import android.media.MediaCodecInfo.CodecProfileLevel
import android.media.MediaCodecList
import androidx.annotation.OptIn
import androidx.media3.common.MimeTypes
import androidx.media3.common.util.UnstableApi
import androidx.media3.extractor.DefaultExtractorsFactory
import androidx.media3.extractor.ExtractorsFactory
import com.strmr.tv.app.AppLog
import com.strmr.tv.ui.settings.DolbyVisionSettings

/** What happens to a Dolby Vision Profile 7 video track before it reaches the decoder. */
internal enum class Dv7Mode {
    /** libdovi rewrites the RPU to single-layer Profile 8.1, for the box's Dolby decoder. */
    CONVERT_TO_DV81,

    /** The RPU and enhancement layer are dropped, leaving the HDR10 base layer. */
    STRIP_TO_HDR10,
}

/**
 * Dolby Vision Profile 7 (UHD Blu-ray, two layers) on Android TV, where almost no decoder takes
 * it: the extractors rewrite it on the way in, per track, from what the container says, so the
 * server's metadata isn't needed and other content passes through untouched. Which rewrite
 * depends only on this device's decoders (Settings › Playback › Dolby Vision Profile 7 turns
 * it off).
 */
@OptIn(UnstableApi::class)
internal object Dv7Playback {
    /** This device's choice, or null when its own decoder plays Profile 7. Probed once. */
    private val deviceMode: Dv7Mode? by lazy {
        val native = supportsProfile(CodecProfileLevel.DolbyVisionProfileDvheDtb)
        val dv81 = !native && supportsProfile(CodecProfileLevel.DolbyVisionProfileDvheSt)
        val bridge = !native && DoviBridge.isConversionReady()
        val mode = when {
            native -> null
            dv81 && bridge -> Dv7Mode.CONVERT_TO_DV81
            else -> Dv7Mode.STRIP_TO_HDR10
        }
        AppLog.i("DV7", "decoders: nativeDv7=$native dv81=$dv81 libdovi=$bridge -> ${mode ?: "native"}")
        mode
    }

    /** The extractors for a progressive stream: rewriting Profile 7 when on and needed. */
    fun extractorsFactory(): ExtractorsFactory {
        val mode = if (DolbyVisionSettings.profile7Enabled) deviceMode else null
        return mode?.let { Dv7ExtractorsFactory(DefaultExtractorsFactory(), it) } ?: DefaultExtractorsFactory()
    }

    private fun supportsProfile(profile: Int): Boolean = try {
        MediaCodecList(MediaCodecList.REGULAR_CODECS).codecInfos.any { info ->
            !info.isEncoder && info.supportedTypes.any { type ->
                type.equals(MimeTypes.VIDEO_DOLBY_VISION, ignoreCase = true) &&
                    info.getCapabilitiesForType(type).profileLevels.any { it.profile == profile }
            }
        }
    } catch (e: Exception) {
        AppLog.w("DV7", "couldn't inspect Dolby Vision decoders for profile $profile: ${e.message}")
        false
    }
}

/** Dolby Vision codec strings: the profile, and the rewrites for the two modes. */
internal object Dv7Codecs {
    /** What the HDR10 base layer advertises once the Dolby Vision signalling is gone. */
    const val HEVC_FALLBACK = "hvc1.2.4.L153.B0"
    private val profilePattern = Regex("(?i)(?:dvhe|dvh1|dvav|dva1)\\.0?(\\d{1,2})(?:\\.|$)")
    private val profile7Pattern = Regex("(?i)(dvhe|dvh1|dvav|dva1)\\.0?7\\.")

    fun profile(codecs: String?): Int? =
        codecs?.let { profilePattern.find(it) }?.groupValues?.get(1)?.toIntOrNull()

    fun isProfile7(codecs: String?): Boolean = profile(codecs) == 7

    /** `dvhe.07.06` → `dvhe.08.06`, keeping the level. */
    fun toDv81(codecs: String?): String {
        if (codecs.isNullOrBlank()) return "dvhe.08.06"
        val rewritten = profile7Pattern.replace(codecs) { "${it.groupValues[1]}.08." }
        return if (rewritten != codecs) rewritten else "dvhe.08.06"
    }
}

package com.strmr.tv.ui.live.multiscreen

import android.media.MediaCodecInfo
import android.media.MediaCodecList
import android.os.Build
import androidx.media3.common.MimeTypes
import com.strmr.tv.app.AppLog

/**
 * How many videos this box can decode at once, from the codecs' own
 * `getMaxSupportedInstances()`. ExoPlayer's decoder fallback would otherwise drop the tiles
 * past the hardware's limit to software decoding (Fire TV hardware decoders allow few
 * instances), which can't keep up. Counts the hardware decoders for a type when there are any,
 * else the software ones. Measured once per type and logged.
 */
object DecoderLimits {
    private val cache = mutableMapOf<String, Int>()

    /** Concurrent decoders for `mime` (an ExoPlayer video MIME type); [Int.MAX_VALUE] when nothing reports. */
    fun instances(mime: String): Int = synchronized(cache) {
        cache.getOrPut(mime) { measure(mime) }
    }

    /** The cap for tiles playing `mimes` (H.264 assumed until a tile reports its format). */
    fun screens(mimes: Collection<String>): Int =
        (mimes.ifEmpty { listOf(MimeTypes.VIDEO_H264) } + MimeTypes.VIDEO_H264).minOf { instances(it) }

    private fun measure(mime: String): Int {
        val decoders = runCatching { MediaCodecList(MediaCodecList.REGULAR_CODECS).codecInfos }.getOrDefault(emptyArray())
            // Secure decoders are for protected content only.
            .filter { !it.isEncoder && !it.name.endsWith(".secure") && it.supportedTypes.any { t -> t.equals(mime, ignoreCase = true) } }
        val counted = decoders.mapNotNull { info ->
            val max = runCatching { info.getCapabilitiesForType(mime).maxSupportedInstances }.getOrNull() ?: return@mapNotNull null
            Triple(info.name, hardware(info), max)
        }
        val hardware = counted.filter { it.second }
        val pick = (hardware.ifEmpty { counted }).maxOfOrNull { it.third } ?: Int.MAX_VALUE
        AppLog.i(
            "Multiscreen",
            "decoder instances for $mime: ${counted.joinToString { "${it.first}${if (it.second) " (hw)" else ""}=${it.third}" }
                .ifEmpty { "none reported" }} → $pick",
        )
        return pick
    }

    private fun hardware(info: MediaCodecInfo): Boolean =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) info.isHardwareAccelerated
        else !info.name.startsWith("OMX.google.") && !info.name.startsWith("c2.android.")
}

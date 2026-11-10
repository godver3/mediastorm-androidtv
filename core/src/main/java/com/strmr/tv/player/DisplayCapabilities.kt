package com.strmr.tv.player

import android.content.Context
import android.hardware.display.DisplayManager
import android.media.MediaCodecList
import android.media.MediaFormat
import android.os.Build
import android.view.Display
import com.strmr.tv.app.App

/**
 * What the connected display and this box can show: HDR from the display's HDR types, and
 * Dolby Vision only when the display reports it and a Dolby Vision decoder exists. Unlike
 * the tvOS engine, ExoPlayer can't present Profile 5 on an HDR10-only panel, so an HDR panel
 * does not count as Dolby Vision capable here.
 */
data class DisplayCapabilities(val hdr: Boolean, val dolbyVision: Boolean, val hdrTypes: List<Int>) {
    companion object {
        fun current(): DisplayCapabilities {
            val display = (App.context.getSystemService(Context.DISPLAY_SERVICE) as DisplayManager).getDisplay(Display.DEFAULT_DISPLAY)
            val types = display?.let(::hdrTypes) ?: emptyList()
            val hdr = types.any { it in setOf(Display.HdrCapabilities.HDR_TYPE_HDR10, Display.HdrCapabilities.HDR_TYPE_HDR10_PLUS) } ||
                types.contains(Display.HdrCapabilities.HDR_TYPE_DOLBY_VISION)
            val dv = types.contains(Display.HdrCapabilities.HDR_TYPE_DOLBY_VISION) && hasDolbyVisionDecoder()
            return DisplayCapabilities(hdr, dv, types)
        }

        private fun hdrTypes(display: Display): List<Int> {
            if (Build.VERSION.SDK_INT >= 34) return display.mode.supportedHdrTypes.toList()
            @Suppress("DEPRECATION")
            return display.hdrCapabilities?.supportedHdrTypes?.toList() ?: emptyList()
        }

        private fun hasDolbyVisionDecoder(): Boolean = runCatching {
            MediaCodecList(MediaCodecList.REGULAR_CODECS).codecInfos.any { info ->
                !info.isEncoder && info.supportedTypes.any { it.equals(MediaFormat.MIMETYPE_VIDEO_DOLBY_VISION, ignoreCase = true) }
            }
        }.getOrDefault(false)
    }
}

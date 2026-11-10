package com.strmr.tv.player.dv7

import androidx.annotation.OptIn
import androidx.media3.common.util.UnstableApi
import com.strmr.tv.app.AppLog
import com.strmr.tv.player.dv7.mkv.MatroskaExtractor

/**
 * The vendored Matroska extractor's hook for Profile 7 tracks (it calls in for no others):
 * convert the RPU, inline or in BlockAdditional, to Profile 8.1 and append it to the sample;
 * or drop it and the enhancement layer for the HDR10 base layer.
 */
@OptIn(UnstableApi::class)
internal class Dv7MatroskaTransformer(private val mode: Dv7Mode) : MatroskaExtractor.DolbyVisionSampleTransformer {
    private val converter = DoviConverter()
    private var lastLength = 0
    private var samples = 0

    override fun onDolbyVisionCodecString(codecs: String?, dolbyVisionConfigBytes: ByteArray?): String {
        AppLog.i("DV7", "Profile 7 track ($codecs, MKV): $mode")
        return when (mode) {
            Dv7Mode.CONVERT_TO_DV81 -> Dv7Codecs.toDv81(codecs)
            Dv7Mode.STRIP_TO_HDR10 -> Dv7Codecs.HEVC_FALLBACK
        }
    }

    /** A BlockAdditional RPU: converted for 8.1 (empty when that fails, so it's dropped). */
    override fun onDolbyVisionBlockAdditionalData(
        blockAdditionalData: ByteArray,
        blockAddIdType: Int,
        dolbyVisionConfigBytes: ByteArray?,
    ): ByteArray? {
        if (mode != Dv7Mode.CONVERT_TO_DV81) return null
        val len = converter.convertRpuNal(blockAdditionalData)
        return if (len > 0) converter.out.copyOf(len) else ByteArray(0)
    }

    override fun transformHevcSample(
        sampleLengthDelimitedData: ByteArray,
        sampleLength: Int,
        nalUnitLengthFieldLength: Int,
        blockAdditionalData: ByteArray?,
        dolbyVisionConfigBytes: ByteArray?,
    ): ByteArray? {
        lastLength = sampleLength
        if (mode == Dv7Mode.CONVERT_TO_DV81) {
            val converted = converter.processSample(
                sampleLengthDelimitedData, sampleLength, annexB = false, nalLengthFieldLength = nalUnitLengthFieldLength,
            )
            if (converted > 0) {
                return appendRpu(converter.out, converted, blockAdditionalData, nalUnitLengthFieldLength)
            }
            if (blockAdditionalData != null && blockAdditionalData.isNotEmpty()) {
                return appendRpu(sampleLengthDelimitedData, sampleLength, blockAdditionalData, nalUnitLengthFieldLength)
            }
        }
        val stripped = HevcDv7Stripper.stripLengthDelimited(sampleLengthDelimitedData, sampleLength, nalUnitLengthFieldLength)
            ?: return null
        lastLength = stripped.size
        samples += 1
        if (samples <= 3 || samples % 600 == 0) AppLog.i("DV7", "MKV samples stripped=$samples last=$sampleLength -> ${stripped.size}")
        return stripped
    }

    override fun lastTransformedSampleLength(): Int = lastLength

    /** The sample with [rpu] after it as one more length-prefixed NAL unit. */
    private fun appendRpu(sample: ByteArray, sampleLen: Int, rpu: ByteArray?, nalLengthFieldLength: Int): ByteArray {
        if (rpu == null || rpu.isEmpty()) {
            lastLength = sampleLen
            return sample
        }
        val out = ByteArray(sampleLen + nalLengthFieldLength + rpu.size)
        System.arraycopy(sample, 0, out, 0, sampleLen)
        var pos = sampleLen
        for (shift in nalLengthFieldLength - 1 downTo 0) out[pos++] = ((rpu.size ushr (shift * 8)) and 0xFF).toByte()
        System.arraycopy(rpu, 0, out, pos, rpu.size)
        lastLength = out.size
        return out
    }
}

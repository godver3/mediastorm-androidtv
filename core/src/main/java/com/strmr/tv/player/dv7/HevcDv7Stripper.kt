package com.strmr.tv.player.dv7

import java.io.ByteArrayOutputStream

/**
 * Removes Dolby Vision Profile 7 side-data NAL units from HEVC samples while
 * preserving the HDR10 base layer for hardware HEVC decode.
 */
internal object HevcDv7Stripper {
    private const val NAL_TYPE_DV_RPU = 62
    private const val NAL_TYPE_DV_EL = 63

    fun stripLengthDelimited(sample: ByteArray, sampleLen: Int, nalLengthFieldLength: Int): ByteArray? {
        if (sampleLen <= 0 || nalLengthFieldLength !in 1..4 || sampleLen < nalLengthFieldLength) {
            return null
        }

        val out = ByteArrayOutputStream(sampleLen)
        var pos = 0
        var changed = false

        while (pos + nalLengthFieldLength <= sampleLen) {
            var nalSize = 0
            for (i in 0 until nalLengthFieldLength) {
                nalSize = (nalSize shl 8) or (sample[pos + i].toInt() and 0xFF)
            }

            val nalStart = pos + nalLengthFieldLength
            if (nalSize <= 0 || nalStart + nalSize > sampleLen) {
                return null
            }

            if (shouldDropNal(sample, nalStart, nalSize)) {
                changed = true
            } else {
                writeLength(out, nalSize, nalLengthFieldLength)
                out.write(sample, nalStart, nalSize)
            }

            pos = nalStart + nalSize
        }

        if (pos != sampleLen) return null
        return if (changed) out.toByteArray() else null
    }

    fun stripAnnexB(sample: ByteArray, sampleLen: Int): ByteArray? {
        if (sampleLen <= 0) return null

        val out = ByteArrayOutputStream(sampleLen)
        var scan = 0
        var changed = false

        while (scan < sampleLen) {
            val startCode = findStartCode(sample, scan, sampleLen)
            if (startCode < 0) {
                out.write(sample, scan, sampleLen - scan)
                break
            }

            if (startCode > scan) {
                out.write(sample, scan, startCode - scan)
            }

            val startCodeLen = startCodeLength(sample, startCode, sampleLen)
            val nalStart = startCode + startCodeLen
            val nextStartCode = findStartCode(sample, nalStart + 2, sampleLen)
            val nalEnd = if (nextStartCode < 0) sampleLen else nextStartCode

            if (nalStart < nalEnd && shouldDropNal(sample, nalStart, nalEnd - nalStart)) {
                changed = true
            } else {
                out.write(sample, startCode, nalEnd - startCode)
            }

            scan = nalEnd
        }

        return if (changed) out.toByteArray() else null
    }

    private fun shouldDropNal(data: ByteArray, offset: Int, nalSize: Int): Boolean {
        if (nalSize <= 0 || offset >= data.size) return false
        val nalType = (data[offset].toInt() ushr 1) and 0x3F
        val layerId = if (nalSize >= 2 && offset + 1 < data.size) {
            ((data[offset].toInt() and 0x01) shl 5) or ((data[offset + 1].toInt() and 0xF8) ushr 3)
        } else {
            0
        }

        return nalType == NAL_TYPE_DV_RPU || nalType == NAL_TYPE_DV_EL || layerId > 0
    }

    private fun writeLength(out: ByteArrayOutputStream, value: Int, fieldLength: Int) {
        for (i in fieldLength - 1 downTo 0) {
            out.write((value ushr (i * 8)) and 0xFF)
        }
    }

    private fun findStartCode(data: ByteArray, from: Int, limit: Int): Int {
        var i = from
        while (i + 2 < limit) {
            if (data[i].toInt() == 0 && data[i + 1].toInt() == 0) {
                if (data[i + 2].toInt() == 1) return i
                if (i + 3 < limit && data[i + 2].toInt() == 0 && data[i + 3].toInt() == 1) {
                    return i
                }
            }
            i += 1
        }
        return -1
    }

    private fun startCodeLength(data: ByteArray, offset: Int, limit: Int): Int {
        return if (
            offset + 3 < limit &&
            data[offset].toInt() == 0 &&
            data[offset + 1].toInt() == 0 &&
            data[offset + 2].toInt() == 0 &&
            data[offset + 3].toInt() == 1
        ) {
            4
        } else {
            3
        }
    }
}

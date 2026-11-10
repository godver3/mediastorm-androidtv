package com.strmr.tv.player.dv7

import androidx.annotation.OptIn
import androidx.media3.common.C
import androidx.media3.common.DataReader
import androidx.media3.common.Format
import androidx.media3.common.MimeTypes
import androidx.media3.common.util.ParsableByteArray
import androidx.media3.common.util.UnstableApi
import androidx.media3.extractor.TrackOutput
import com.strmr.tv.app.AppLog
import java.io.EOFException

/**
 * An MP4 / TS video track output that, for a Profile 7 track, collects each sample whole,
 * rewrites it per [mode] and passes it on; the format is relabelled to match (DV 8.1 or plain
 * HEVC). Any other track goes straight through.
 */
@OptIn(UnstableApi::class)
internal class Dv7TrackOutput(
    private val delegate: TrackOutput,
    /** Start-code NAL units (TS) rather than length-prefixed (MP4). */
    private val annexB: Boolean,
    private val mode: Dv7Mode,
) : TrackOutput {
    private val converter = DoviConverter()
    private val scratch = ParsableByteArray()
    private var pending = ByteArray(0)
    private var pendingLen = 0
    private var readBuffer = ByteArray(0)
    private var rewriting = false
    private var nalLengthFieldLength = 4

    override fun format(format: Format) {
        rewriting = isProfile7(format)
        nalLengthFieldLength = nalLengthFieldLength(format)
        if (!rewriting) {
            delegate.format(format)
            return
        }
        AppLog.i("DV7", "Profile 7 track (${format.codecs}, ${if (annexB) "TS" else "MP4"}): $mode")
        delegate.format(
            when (mode) {
                Dv7Mode.CONVERT_TO_DV81 -> format.buildUpon()
                    .setSampleMimeType(MimeTypes.VIDEO_DOLBY_VISION)
                    .setCodecs(Dv7Codecs.toDv81(format.codecs))
                    .build()
                Dv7Mode.STRIP_TO_HDR10 -> format.buildUpon()
                    .setSampleMimeType(MimeTypes.VIDEO_H265)
                    .setCodecs(null)
                    .build()
            },
        )
    }

    override fun sampleData(input: DataReader, length: Int, allowEndOfInput: Boolean, sampleDataPart: Int): Int {
        if (!rewriting || sampleDataPart != TrackOutput.SAMPLE_DATA_PART_MAIN) {
            return delegate.sampleData(input, length, allowEndOfInput, sampleDataPart)
        }
        if (readBuffer.size < length) readBuffer = ByteArray(grow(readBuffer.size, length))
        val read = input.read(readBuffer, 0, length)
        if (read == C.RESULT_END_OF_INPUT) {
            if (allowEndOfInput) return C.RESULT_END_OF_INPUT
            throw EOFException()
        }
        if (read > 0) {
            ensurePending(read)
            System.arraycopy(readBuffer, 0, pending, pendingLen, read)
            pendingLen += read
        }
        return read
    }

    override fun sampleData(data: ParsableByteArray, length: Int, sampleDataPart: Int) {
        if (!rewriting || sampleDataPart != TrackOutput.SAMPLE_DATA_PART_MAIN || length <= 0) {
            delegate.sampleData(data, length, sampleDataPart)
            return
        }
        ensurePending(length)
        data.readBytes(pending, pendingLen, length)
        pendingLen += length
    }

    override fun sampleMetadata(timeUs: Long, flags: Int, size: Int, offset: Int, cryptoData: TrackOutput.CryptoData?) {
        if (!rewriting || pendingLen <= 0) {
            delegate.sampleMetadata(timeUs, flags, size, offset, cryptoData)
            return
        }
        // `offset` bytes written after this sample belong to the next one: carry them over.
        val carry = offset.coerceIn(0, pendingLen)
        val sampleLen = pendingLen - carry
        val (out, outLen) = rewrite(sampleLen)
        scratch.reset(out, outLen)
        delegate.sampleData(scratch, outLen)
        delegate.sampleMetadata(timeUs, flags, outLen, 0, cryptoData)
        if (carry > 0) System.arraycopy(pending, sampleLen, pending, 0, carry)
        pendingLen = carry
    }

    /** The rewritten sample: converted where libdovi managed it, else stripped, else as is. */
    private fun rewrite(sampleLen: Int): Pair<ByteArray, Int> {
        if (mode == Dv7Mode.CONVERT_TO_DV81) {
            val converted = converter.processSample(pending, sampleLen, annexB, nalLengthFieldLength)
            if (converted > 0) return converter.out to converted
        }
        val stripped = if (annexB) {
            HevcDv7Stripper.stripAnnexB(pending, sampleLen)
        } else {
            HevcDv7Stripper.stripLengthDelimited(pending, sampleLen, nalLengthFieldLength)
        }
        return stripped?.let { it to it.size } ?: (pending to sampleLen)
    }

    private fun ensurePending(extra: Int) {
        val required = pendingLen + extra
        if (pending.size < required) pending = pending.copyOf(grow(pending.size, required))
    }

    private companion object {
        fun grow(current: Int, required: Int): Int {
            var size = if (current == 0) 16 * 1024 else current
            while (size < required) size = size shl 1
            return size
        }

        /** Only a track the container labels Profile 7; a bare Dolby Vision MIME type isn't enough. */
        fun isProfile7(format: Format): Boolean = Dv7Codecs.isProfile7(format.codecs)

        /** From the hvcC record (lengthSizeMinusOne), 4 when there isn't one. */
        fun nalLengthFieldLength(format: Format): Int {
            val hvcc = format.initializationData.firstOrNull() ?: return 4
            if (hvcc.size <= 21 || hvcc[0].toInt() != 1) return 4
            return (hvcc[21].toInt() and 0x03) + 1
        }
    }
}

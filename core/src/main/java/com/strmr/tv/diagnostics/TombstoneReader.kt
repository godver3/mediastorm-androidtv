package com.strmr.tv.diagnostics

import java.io.InputStream

/**
 * The crashing thread's backtrace out of a native crash's tombstone, which
 * [android.app.ApplicationExitInfo.getTraceInputStream] hands over as a `Tombstone` protobuf
 * (API 31+, AOSP `system/core/debuggerd/proto/tombstone.proto`). Only the few fields a report
 * needs are read; everything else is skipped by wire type, so a newer schema still parses.
 */
object TombstoneReader {
    private const val MAX_FRAMES = 40

    fun summary(stream: InputStream): List<String> {
        val tombstone = Message(stream.readBytes())
        var tid = 0L
        var signal = ""
        var abort = ""
        val threads = mutableMapOf<Long, ByteArray>()
        tombstone.forEach { field, value ->
            when (field) {
                TID -> tid = value as Long
                SIGNAL_INFO -> signal = signalLine(value as ByteArray)
                ABORT_MESSAGE -> abort = String(value as ByteArray)
                THREADS -> {
                    var key = 0L
                    var thread: ByteArray? = null
                    Message(value as ByteArray).forEach { f, v -> if (f == 1) key = v as Long else if (f == 2) thread = v as ByteArray }
                    thread?.let { threads[key] = it }
                }
            }
        }
        val lines = mutableListOf<String>()
        if (signal.isNotEmpty()) lines += "signal $signal"
        if (abort.isNotEmpty()) lines += "abort message: $abort"
        val thread = threads[tid] ?: return lines
        var name = ""
        val frames = mutableListOf<String>()
        Message(thread).forEach { field, value ->
            if (field == THREAD_NAME) name = String(value as ByteArray)
            if (field == THREAD_BACKTRACE && frames.size < MAX_FRAMES) frames += frameLine(frames.size, value as ByteArray)
        }
        lines += "crashing thread $tid \"$name\":"
        lines += frames
        return lines
    }

    private fun signalLine(bytes: ByteArray): String {
        var name = ""
        var code = ""
        var fault: Long? = null
        Message(bytes).forEach { field, value ->
            when (field) {
                2 -> name = String(value as ByteArray)
                4 -> code = String(value as ByteArray)
                9 -> fault = value as Long
            }
        }
        return "$name $code" + (fault?.let { " fault address 0x${it.toULong().toString(16)}" } ?: "")
    }

    private fun frameLine(index: Int, bytes: ByteArray): String {
        var relPc = 0L
        var function = ""
        var offset = 0L
        var file = ""
        Message(bytes).forEach { field, value ->
            when (field) {
                1 -> relPc = value as Long
                4 -> function = String(value as ByteArray)
                5 -> offset = value as Long
                6 -> file = String(value as ByteArray)
            }
        }
        val symbol = if (function.isEmpty()) "" else " ($function+$offset)"
        return "#%02d pc %016x %s%s".format(index, relPc, file, symbol)
    }

    // Tombstone, Thread and the map entry's field numbers.
    private const val TID = 6
    private const val SIGNAL_INFO = 10
    private const val ABORT_MESSAGE = 14
    private const val THREADS = 16
    private const val THREAD_NAME = 2
    private const val THREAD_BACKTRACE = 4

    /** A protobuf message's fields: varints and fixed ints as Long, length-delimited as bytes. */
    private class Message(private val bytes: ByteArray) {
        private var position = 0

        fun forEach(visit: (Int, Any) -> Unit) {
            position = 0
            while (position < bytes.size) {
                val tag = varint()
                val field = (tag ushr 3).toInt()
                when ((tag and 7).toInt()) {
                    0 -> visit(field, varint())
                    1 -> visit(field, fixed(8))
                    2 -> {
                        val length = varint().toInt()
                        if (length < 0 || position + length > bytes.size) return
                        visit(field, bytes.copyOfRange(position, position + length))
                        position += length
                    }
                    5 -> visit(field, fixed(4))
                    else -> return
                }
            }
        }

        private fun varint(): Long {
            var result = 0L
            var shift = 0
            while (position < bytes.size && shift < 64) {
                val byte = bytes[position++].toInt()
                result = result or ((byte and 0x7f).toLong() shl shift)
                if (byte and 0x80 == 0) break
                shift += 7
            }
            return result
        }

        private fun fixed(size: Int): Long {
            var result = 0L
            for (i in 0 until size) {
                if (position >= bytes.size) break
                result = result or ((bytes[position++].toLong() and 0xff) shl (8 * i))
            }
            return result
        }
    }
}

package com.strmr.tv.player.dv7

import com.strmr.tv.app.AppLog

/**
 * The JNI bridge to libdovi (src/main/cpp/dovi_bridge.cpp). Without the libdovi prebuilts
 * (scripts/sync-vendor.sh) it builds as a stub that reports not ready, and Profile 7 falls
 * back to the HDR10 base layer.
 */
internal object DoviBridge {
    private val loaded: Boolean by lazy {
        try {
            System.loadLibrary("dovi_bridge")
            true
        } catch (t: Throwable) {
            AppLog.w("DV7", "dovi_bridge didn't load: ${t.message}")
            false
        }
    }

    /** Whether the library loaded; the converter does nothing otherwise. */
    val isLoaded: Boolean get() = loaded

    /** Whether the real libdovi is linked, so RPUs can be converted. */
    fun isConversionReady(): Boolean = loaded && runCatching { nativeIsConversionPathReady() }.getOrDefault(false)

    @JvmStatic external fun nativeIsConversionPathReady(): Boolean

    @JvmStatic external fun nativeConvertRpuNal(sample: ByteArray, offset: Int, len: Int, out: ByteArray, mode: Int): Int

    @JvmStatic external fun nativeProcessVideoSample(
        sample: ByteArray,
        sampleLen: Int,
        nalFormat: Int,
        nalLengthFieldLength: Int,
        out: ByteArray,
        convertDovi: Boolean,
        doviMode: Int,
        stripDovi: Boolean,
    ): Int
}

/**
 * One extractor's conversions, with its own output buffer (valid until the next call), so two
 * players extracting at once never share one. Results: bytes written, or 0 for unchanged /
 * failed; the native side returns minus the size needed when [out] is too small.
 */
internal class DoviConverter {
    var out = ByteArray(256 * 1024)
        private set

    /** Drops the enhancement layer and rewrites each RPU NAL unit to Profile 8.1. */
    fun processSample(sample: ByteArray, sampleLen: Int, annexB: Boolean, nalLengthFieldLength: Int): Int {
        if (!DoviBridge.isLoaded || sampleLen <= 0) return 0
        return withRetry {
            DoviBridge.nativeProcessVideoSample(
                sample, sampleLen, if (annexB) 0 else 1, nalLengthFieldLength, out,
                convertDovi = true, doviMode = DV81_MODE, stripDovi = false,
            )
        }
    }

    /** One RPU NAL unit (as from BlockAdditional) rewritten to Profile 8.1. */
    fun convertRpuNal(rpu: ByteArray): Int {
        if (!DoviBridge.isLoaded || rpu.isEmpty()) return 0
        return withRetry { DoviBridge.nativeConvertRpuNal(rpu, 0, rpu.size, out, DV81_MODE) }
    }

    private inline fun withRetry(call: () -> Int): Int {
        val first = runCatching(call).getOrElse { AppLog.w("DV7", "libdovi call failed: ${it.message}"); 0 }
        if (first >= 0) return first
        out = ByteArray(maxOf(-first, out.size * 2))
        return runCatching(call).getOrElse { AppLog.w("DV7", "libdovi retry failed: ${it.message}"); 0 }.coerceAtLeast(0)
    }

    private companion object {
        /** libdovi's conversion mode 1: to Profile 8.1, keeping the base layer's HDR10 compatibility. */
        const val DV81_MODE = 1
    }
}

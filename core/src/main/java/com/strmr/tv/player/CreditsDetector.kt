package com.strmr.tv.player

import android.app.ActivityManager
import android.content.Context
import android.graphics.Bitmap
import android.os.Handler
import android.os.Looper
import android.view.PixelCopy
import android.view.SurfaceView
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import com.strmr.tv.app.App
import com.strmr.tv.app.AppLog

/**
 * On-device credits detection (Settings › Playback › Credits Detection), as the Expo app's
 * ExoPlayer view does it: a small copy of the video surface ([PixelCopy]) read by ML Kit's
 * on-device text recognizer, decided by [CreditsWindow]. Only the frame on screen is read, so it
 * costs no network. Low-memory devices (≤ 2 GB, most Fire TV sticks) read smaller frames less often.
 */
class CreditsDetector(private val onDetected: (startSec: Double) -> Unit) {
    private val lowRam = run {
        val am = App.context.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
        val info = ActivityManager.MemoryInfo().also(am::getMemoryInfo)
        am.isLowRamDevice || info.totalMem / (1024 * 1024) <= LOW_RAM_MB
    }
    private val window = if (lowRam) CreditsWindow(8.0, 2.0) else CreditsWindow()
    private val maxWidth = if (lowRam) 320 else 640
    private val maxHeight = if (lowRam) 180 else 360
    private val recognizer by lazy { TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS) }
    private val main = Handler(Looper.getMainLooper())
    private var inFlight = false
    private var closed = false
    private var logged = false

    /** Called from the player's clock (main thread) while detection applies to this playback. */
    fun tick(positionSec: Double, durationSec: Double, surface: SurfaceView?) {
        if (closed || inFlight || window.detectedAtSec != null) return
        val view = surface?.takeIf { it.holder.surface.isValid && it.width > 0 && it.height > 0 } ?: return
        if (!window.shouldSample(positionSec, durationSec)) return
        val bitmap = sampleBitmap(view) ?: return
        if (!logged) {
            logged = true
            AppLog.i(TAG, "active: sample ${bitmap.width}x${bitmap.height}${if (lowRam) " (low-RAM)" else ""} from ${positionSec.toInt()}s of ${durationSec.toInt()}s")
        }
        inFlight = true
        try {
            PixelCopy.request(view, bitmap, { result ->
                if (result == PixelCopy.SUCCESS && !closed) recognize(bitmap, positionSec)
                else { bitmap.recycle(); inFlight = false }
            }, main)
        } catch (e: IllegalArgumentException) {
            bitmap.recycle()
            inFlight = false
        }
    }

    fun close() {
        closed = true
        if (logged) runCatching { recognizer.close() }
    }

    private fun recognize(bitmap: Bitmap, positionSec: Double) {
        recognizer.process(InputImage.fromBitmap(bitmap, 0))
            .addOnSuccessListener { text ->
                if (closed) return@addOnSuccessListener
                val lines = text.textBlocks.flatMap { block -> block.lines.map { it.text } }
                window.onText(positionSec, lines)?.let { start ->
                    AppLog.i(TAG, "credits at ${"%.1f".format(start)}s")
                    onDetected(start)
                }
            }
            .addOnFailureListener { AppLog.w(TAG, "text recognition failed: ${it.message}") }
            .addOnCompleteListener {
                bitmap.recycle()
                inFlight = false
            }
    }

    /** The surface's aspect, scaled down to the sample size. */
    private fun sampleBitmap(view: SurfaceView): Bitmap? {
        val scale = minOf(maxWidth.toFloat() / view.width, maxHeight.toFloat() / view.height, 1f)
        return try {
            Bitmap.createBitmap((view.width * scale).toInt().coerceAtLeast(1), (view.height * scale).toInt().coerceAtLeast(1),
                                Bitmap.Config.ARGB_8888)
        } catch (e: OutOfMemoryError) {
            null
        }
    }

    private companion object {
        const val TAG = "Credits"
        const val LOW_RAM_MB = 2048L
    }
}

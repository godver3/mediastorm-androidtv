package com.strmr.tv.ui

import android.graphics.Bitmap
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.graphics.ImageShader
import androidx.compose.ui.graphics.ShaderBrush
import androidx.compose.ui.graphics.TileMode
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.layer.CompositingStrategy
import androidx.compose.ui.graphics.layer.drawLayer
import com.strmr.tv.ui.settings.AppearanceSettings
import kotlin.math.abs
import kotlin.math.roundToInt
import kotlin.random.Random

/**
 * The page background behind the tabs other than Home: the chosen colour (Settings ›
 * Appearance, tvOS grey by default) under a fine grain (about ±2 levels, as measured off the
 * tvOS app), tiled from a small noise bitmap. Drawn into a cached offscreen layer: full-screen
 * fills redrawn every frame cost the Fire TV's GPU.
 */
fun Modifier.pageBackground(): Modifier = drawWithCache {
    // Read here, so a new choice rebuilds the layer.
    val color = AppearanceSettings.background.color
    val layer = obtainGraphicsLayer().apply {
        compositingStrategy = CompositingStrategy.Offscreen
        record {
            drawRect(color)
            drawRect(GrainBrush)
        }
    }
    onDrawBehind { drawLayer(layer) }
}

private val GrainBrush: ShaderBrush by lazy {
    val size = 128
    val random = Random(7)
    val pixels = IntArray(size * size) {
        // A Gaussian-ish spread (sum of uniforms), as faint white or black specks.
        val v = (random.nextFloat() + random.nextFloat() + random.nextFloat() - 1.5f) * 1.6f
        val alpha = (abs(v) * 1.2f).roundToInt()
        (alpha shl 24) or if (v > 0) 0xFFFFFF else 0
    }
    val bitmap = Bitmap.createBitmap(pixels, size, size, Bitmap.Config.ARGB_8888)
    ShaderBrush(ImageShader(bitmap.asImageBitmap(), TileMode.Repeated, TileMode.Repeated))
}

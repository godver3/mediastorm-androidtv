package com.strmr.tv.launcher

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Rect
import com.strmr.tv.R

/**
 * The home screen channels' logo. The launcher crops a channel logo to a square or a circle,
 * and the app icon (`brand_logo`, 382 × 419, opaque) would lose its edges, so the logo is a
 * square of the brand's own background with the icon fitted at 72 % of the side, centred:
 * its corners stay inside the circle crop.
 */
object ChannelLogo {
    /** Bumped when the logo changes, so channels published with an older one get it again. */
    const val VERSION = 2
    private const val SIDE = 400
    private const val FILL = 0.72f

    fun build(context: Context): Bitmap {
        val source = BitmapFactory.decodeResource(context.resources, R.drawable.brand_logo)
        val out = Bitmap.createBitmap(SIDE, SIDE, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(out)
        // The icon's corner is its background colour, so the padding blends in.
        canvas.drawColor(source.getPixel(0, 0))
        val scale = SIDE * FILL / maxOf(source.width, source.height)
        val w = (source.width * scale).toInt()
        val h = (source.height * scale).toInt()
        val left = (SIDE - w) / 2
        val top = (SIDE - h) / 2
        canvas.drawBitmap(source, null, Rect(left, top, left + w, top + h), Paint(Paint.FILTER_BITMAP_FLAG))
        source.recycle()
        return out
    }
}

package com.strmr.tv.ui.live.multiscreen

import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sqrt

/** A tile's rectangle, in dp from the screen's top left. */
data class TileFrame(val x: Float, val y: Float, val width: Float, val height: Float)

enum class TileDirection { LEFT, RIGHT, UP, DOWN }

/**
 * Where the multiscreen's tiles go, a port of the Expo TV `multiscreen-layout`. Android video
 * surfaces are separate SurfaceFlinger layers that can't be overlapped and clipped, so every
 * layout here keeps tiles disjoint (see the Expo `services/multiscreenLayout.ts`).
 */
object MultiscreenLayout {
    /**
     * Rectangles keyed by stable slot index, not display order: an equal 16:9 grid with
     * ceil(sqrt(n)) columns, centred; or `expanded` at 72% of the width beside a column of the
     * others (two tiles sit side by side). One tile fills the screen.
     */
    fun frames(ids: List<Int>, expanded: Int?, width: Float, height: Float, gap: Float): Map<Int, TileFrame> {
        val frames = linkedMapOf<Int, TileFrame>()
        if (ids.isEmpty()) return frames
        if (ids.size == 1) return mapOf(ids[0] to TileFrame(0f, 0f, width, height))
        if (expanded == null || expanded !in ids) {
            grid(frames, ids, TileFrame(0f, 0f, width, height), ceil(sqrt(ids.size.toFloat())).toInt(), gap)
            return frames
        }
        val largeWidth = min((width - gap) * 0.72f, height * 16f / 9f)
        val smallWidth = min((width - gap) * 0.28f, largeWidth * 0.28f / 0.72f)
        val left = max(0f, (width - largeWidth - smallWidth - gap) / 2f)
        val largeHeight = largeWidth * 9f / 16f
        if (ids.size == 2) {
            val smallHeight = smallWidth * 9f / 16f
            ids.forEachIndexed { i, id ->
                val large = id == expanded
                frames[id] = TileFrame(
                    x = left + if (i == 0) 0f else (if (expanded == ids[0]) largeWidth else smallWidth) + gap,
                    y = (height - if (large) largeHeight else smallHeight) / 2f,
                    width = if (large) largeWidth else smallWidth,
                    height = if (large) largeHeight else smallHeight,
                )
            }
        } else {
            frames[expanded] = TileFrame(left, (height - largeHeight) / 2f, largeWidth, largeHeight)
            val others = ids.filter { it != expanded }
            grid(
                frames, others, TileFrame(left + largeWidth + gap, 0f, smallWidth, height),
                max(1, ceil(sqrt(others.size / 3f)).toInt()), gap,
            )
        }
        return frames
    }

    private fun grid(frames: MutableMap<Int, TileFrame>, items: List<Int>, bounds: TileFrame, columns: Int, gap: Float) {
        val rows = ceil(items.size / columns.toFloat()).toInt()
        // Keeps the geometry valid in a small area.
        val spacing = min(gap, min(bounds.width / (columns * 4), bounds.height / (rows * 4)))
        val w = min(
            (bounds.width - spacing * (columns - 1)) / columns,
            (bounds.height - spacing * (rows - 1)) / rows * 16f / 9f,
        )
        val h = w * 9f / 16f
        val left = bounds.x + max(0f, (bounds.width - w * columns - spacing * (columns - 1)) / 2f)
        val top = bounds.y + max(0f, (bounds.height - h * rows - spacing * (rows - 1)) / 2f)
        items.forEachIndexed { i, id ->
            frames[id] = TileFrame(left + (i % columns) * (w + spacing), top + floor(i / columns.toFloat()) * (h + spacing), w, h)
        }
    }

    /**
     * The tile an arrow moves to from `selected`, or null: the nearest by facing-edge distance
     * (a large tile spans several side tiles, so centres would mislead) plus twice the
     * cross-axis offset of the centres; ties go to the lower slot.
     */
    fun neighbor(frames: Map<Int, TileFrame>, selected: Int, direction: TileDirection): Int? {
        val a = frames[selected] ?: return null
        val horizontal = direction == TileDirection.LEFT || direction == TileDirection.RIGHT
        return frames.entries.mapNotNull { (id, b) ->
            if (id == selected) return@mapNotNull null
            val distance = when (direction) {
                TileDirection.RIGHT -> b.x - a.x - a.width
                TileDirection.LEFT -> a.x - b.x - b.width
                TileDirection.DOWN -> b.y - a.y - a.height
                TileDirection.UP -> a.y - b.y - b.height
            }
            if (distance < -0.01f) return@mapNotNull null
            val cross = if (horizontal) abs(b.y + b.height / 2 - a.y - a.height / 2) else abs(b.x + b.width / 2 - a.x - a.width / 2)
            id to distance + cross * 2
        }.minWithOrNull(compareBy<Pair<Int, Float>>({ it.second }, { it.first }))?.first
    }
}

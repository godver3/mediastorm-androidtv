package com.strmr.tv.ui.live.multiscreen

import com.strmr.tv.ui.live.multiscreen.MultiscreenLayout.frames
import com.strmr.tv.ui.live.multiscreen.MultiscreenLayout.neighbor
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class MultiscreenLayoutTest {
    private val w = 960f
    private val h = 500f
    private val gap = 8f

    private fun overlaps(a: TileFrame, b: TileFrame) =
        a.x < b.x + b.width - 0.01f && b.x < a.x + a.width - 0.01f && a.y < b.y + b.height - 0.01f && b.y < a.y + a.height - 0.01f

    private fun assertDisjoint(f: Map<Int, TileFrame>) {
        val list = f.values.toList()
        for (i in list.indices) for (j in i + 1 until list.size) assertFalse("$i and $j overlap", overlaps(list[i], list[j]))
    }

    private fun assertInside(f: Map<Int, TileFrame>) = f.values.forEach {
        assertTrue(it.x >= -0.01f && it.y >= -0.01f && it.x + it.width <= w + 0.01f && it.y + it.height <= h + 0.01f)
    }

    @Test fun oneTileFillsTheScreen() {
        assertEquals(mapOf(3 to TileFrame(0f, 0f, w, h)), frames(listOf(3), null, w, h, gap))
    }

    @Test fun twoEqualTilesSitSideBySideCentred() {
        val f = frames(listOf(0, 1), null, w, h, gap)
        val a = f.getValue(0)
        val b = f.getValue(1)
        assertEquals(a.y, b.y, 0.01f)
        assertEquals(a.width, b.width, 0.01f)
        assertEquals(16f / 9f, a.width / a.height, 0.01f)
        assertEquals(gap, b.x - a.x - a.width, 0.01f)
        assertEquals(a.x, w - b.x - b.width, 0.01f)
        assertDisjoint(f)
    }

    @Test fun gridHasCeilSqrtColumnsAndKeepsSlotKeys() {
        val f = frames(listOf(0, 2, 3), null, w, h, gap)
        assertEquals(setOf(0, 2, 3), f.keys)
        // Two columns: 0 and 2 on the first row, 3 below 0.
        assertEquals(f.getValue(0).y, f.getValue(2).y, 0.01f)
        assertEquals(f.getValue(0).x, f.getValue(3).x, 0.01f)
        assertTrue(f.getValue(3).y > f.getValue(0).y)
        assertDisjoint(f)
        assertInside(f)
        assertDisjoint(frames(listOf(0, 1, 2, 3), null, w, h, gap))
    }

    @Test fun enlargedTileTakes72PercentBesideTheOthers() {
        val f = frames(listOf(0, 1, 2, 3), 2, w, h, gap)
        val large = f.getValue(2)
        assertEquals(minOf((w - gap) * 0.72f, h * 16f / 9f), large.width, 0.01f)
        listOf(0, 1, 3).forEach {
            assertTrue(f.getValue(it).x >= large.x + large.width + gap - 0.01f)
            assertTrue(f.getValue(it).width < large.width)
        }
        assertDisjoint(f)
        assertInside(f)
    }

    @Test fun twoTilesEnlargedStaySideBySideInSlotOrder() {
        val f = frames(listOf(0, 1), 1, w, h, gap)
        val small = f.getValue(0)
        val large = f.getValue(1)
        assertTrue(large.width > small.width)
        assertEquals(gap, large.x - small.x - small.width, 0.01f)
        assertDisjoint(f)
    }

    @Test fun expandedNotShownFallsBackToTheGrid() {
        assertEquals(frames(listOf(0, 1), null, w, h, gap), frames(listOf(0, 1), 5, w, h, gap))
    }

    @Test fun neighborFollowsTheGrid() {
        val f = frames(listOf(0, 1, 2, 3), null, w, h, gap)
        assertEquals(1, neighbor(f, 0, TileDirection.RIGHT))
        assertEquals(2, neighbor(f, 0, TileDirection.DOWN))
        assertEquals(3, neighbor(f, 2, TileDirection.RIGHT))
        assertEquals(1, neighbor(f, 3, TileDirection.UP))
        assertNull(neighbor(f, 0, TileDirection.LEFT))
        assertNull(neighbor(f, 2, TileDirection.DOWN))
    }

    @Test fun neighborUsesFacingEdgesForTheLargeTile() {
        val f = frames(listOf(0, 1, 2, 3), 0, w, h, gap)
        // Right from the large tile: the side tile nearest its centre line.
        assertEquals(2, neighbor(f, 0, TileDirection.RIGHT))
        // Left from any side tile returns to the large one.
        listOf(1, 2, 3).forEach { assertEquals(0, neighbor(f, it, TileDirection.LEFT)) }
        assertEquals(2, neighbor(f, 1, TileDirection.DOWN))
        assertEquals(1, neighbor(f, 2, TileDirection.UP))
        assertNull(neighbor(f, 0, TileDirection.UP))
    }

    @Test fun neighborOfAnUnknownSlotIsNull() {
        assertNull(neighbor(emptyMap(), 0, TileDirection.RIGHT))
    }
}

package com.strmr.tv.player.dv7

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class Dv7Test {
    @Test fun onlyProfile7IsRewritten() {
        assertTrue(Dv7Codecs.isProfile7("dvhe.07.06"))
        assertTrue(Dv7Codecs.isProfile7("hvc1.2.4.L153.B0,dvhe.07.06"))
        assertFalse(Dv7Codecs.isProfile7("dvhe.05.06"))
        assertFalse(Dv7Codecs.isProfile7("dvhe.08.06"))
        assertFalse(Dv7Codecs.isProfile7("hvc1.2.4.L153.B0"))
        assertFalse(Dv7Codecs.isProfile7(null))
    }

    @Test fun codecsRewriteTo81() {
        assertEquals("dvhe.08.06", Dv7Codecs.toDv81("dvhe.07.06"))
        assertEquals("dvh1.08.09", Dv7Codecs.toDv81("dvh1.07.09"))
        assertEquals("dvhe.08.06", Dv7Codecs.toDv81(null))
    }

    // Length-prefixed (4-byte) NAL units: a base-layer slice (type 1), an RPU (62), an
    // enhancement-layer slice (layer 1).
    private val slice = byteArrayOf(0x02, 0x01, 0x55)
    private val rpu = byteArrayOf((62 shl 1).toByte(), 0x01, 0x66)
    private val elSlice = byteArrayOf(0x02, 0x09, 0x77)

    private fun lengthDelimited(vararg nals: ByteArray) =
        nals.flatMap { listOf<Byte>(0, 0, 0, it.size.toByte()) + it.toList() }.toByteArray()

    private fun annexB(vararg nals: ByteArray) =
        nals.flatMap { listOf<Byte>(0, 0, 0, 1) + it.toList() }.toByteArray()

    @Test fun stripsRpuAndEnhancementLayer() {
        val sample = lengthDelimited(slice, rpu, elSlice)
        assertArrayEquals(lengthDelimited(slice), HevcDv7Stripper.stripLengthDelimited(sample, sample.size, 4))
        val ts = annexB(slice, rpu, elSlice)
        assertArrayEquals(annexB(slice), HevcDv7Stripper.stripAnnexB(ts, ts.size))
    }

    @Test fun leavesBaseLayerOnlySamplesAlone() {
        val sample = lengthDelimited(slice)
        assertNull(HevcDv7Stripper.stripLengthDelimited(sample, sample.size, 4))
    }
}

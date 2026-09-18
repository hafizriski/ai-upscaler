package com.arthexdev.exups.ml

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Unit test murni Kotlin logic — TIDAK pakai Android Bitmap.
 * Aman dijalankan di CI (JVM).
 */
class TilePositionTest {

    // Duplikat logic computePositions dari TileProcessor (untuk test)
    private fun computePositions(size: Int, tile: Int, stride: Int): List<Int> {
        if (size <= tile) return listOf(0)
        val result = mutableListOf<Int>()
        var pos = 0
        while (pos + tile <= size) {
            if (result.isEmpty() || result.last() != pos) result.add(pos)
            pos += stride
        }
        val last = size - tile
        if (result.isEmpty() || result.last() != last) result.add(last)
        return result
    }

    @Test
    fun `small image returns single position`() {
        val pos = computePositions(64, 128, 120)
        assertEquals(1, pos.size)
        assertEquals(0, pos.first())
    }

    @Test
    fun `large image returns multiple positions`() {
        val pos = computePositions(500, 128, 120)
        assertTrue(pos.size > 1)
        assertEquals(0, pos.first())
    }

    @Test
    fun `positions always start at 0`() {
        val pos = computePositions(1000, 128, 120)
        assertEquals(0, pos.first())
    }

    @Test
    fun `positions cover entire range`() {
        val size = 1000
        val tile = 128
        val pos = computePositions(size, tile, 120)
        val last = pos.last()
        assertTrue("Last position should cover end", last + tile >= size)
    }

    @Test
    fun `exact fit single position`() {
        val pos = computePositions(128, 128, 120)
        assertEquals(listOf(0), pos)
    }
}

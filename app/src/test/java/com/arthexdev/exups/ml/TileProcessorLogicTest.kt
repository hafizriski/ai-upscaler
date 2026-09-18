package com.arthexdev.exups.ml

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Test logika computePositions via reflection-free test.
 * Karena TileProcessor butuh AdaptiveInterpreter, kita test pure logic saja.
 */
class TileProcessorLogicTest {

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
    fun `small image single position`() {
        val pos = computePositions(64, 128, 120)
        assertEquals(listOf(0), pos)
    }

    @Test
    fun `large image multiple positions`() {
        val pos = computePositions(500, 128, 120)
        assertTrue(pos.size > 1)
        assertEquals(0, pos.first())
        assertEquals(500 - 128, pos.last())
    }

    @Test
    fun `positions cover full range`() {
        val pos = computePositions(1000, 128, 120)
        assertEquals(0, pos.first())
        assertTrue(pos.last() + 128 >= 1000)
    }
}

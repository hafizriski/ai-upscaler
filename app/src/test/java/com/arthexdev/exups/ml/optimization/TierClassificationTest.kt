package com.arthexdev.exups.ml.optimization

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Test logika klasifikasi tier — murni matematis.
 */
class TierClassificationTest {

    private fun classifyTier(cores: Int, ramMb: Int, hasVulkan: Boolean): String {
        return when {
            cores >= 8 && ramMb >= 6000 && hasVulkan -> "HIGH"
            cores >= 6 && ramMb >= 3500 -> "MID"
            else -> "LOW"
        }
    }

    private fun optimalThreads(cores: Int): Int = (cores - 1).coerceIn(2, 8)

    @Test
    fun `flagship phone -> HIGH tier`() {
        assertEquals("HIGH", classifyTier(8, 8000, true))
    }

    @Test
    fun `mid-range phone -> MID tier`() {
        assertEquals("MID", classifyTier(8, 4000, false))
    }

    @Test
    fun `low-end phone -> LOW tier`() {
        assertEquals("LOW", classifyTier(4, 2000, false))
    }

    @Test
    fun `optimal threads cores minus one`() {
        assertEquals(7, optimalThreads(8))
        assertEquals(5, optimalThreads(6))
        assertEquals(3, optimalThreads(4))
    }

    @Test
    fun `optimal threads clamped min 2`() {
        assertEquals(2, optimalThreads(2))
        assertEquals(2, optimalThreads(1))
    }

    @Test
    fun `optimal threads clamped max 8`() {
        assertEquals(8, optimalThreads(12))
        assertEquals(8, optimalThreads(16))
    }
}

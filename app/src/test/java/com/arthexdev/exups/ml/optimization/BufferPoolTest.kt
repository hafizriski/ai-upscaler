package com.arthexdev.exups.ml.optimization

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.nio.ByteBuffer

class BufferPoolTest {

    @Test
    fun `pool creates reusable buffers`() {
        val pool = BufferPool(128, 4)
        assertNotNull(pool.inputBuffer())
        assertNotNull(pool.paddedBitmap())
        assertNotNull(pool.outputTileBitmap())
    }

    @Test
    fun `writeFloatNHWC produces correct buffer size`() {
        val pool = BufferPool(4, 2)
        // Isi srcPixels dummy
        val px = pool.srcPixels()
        for (i in px.indices) px[i] = 0xFF804020.toInt()

        val buf: ByteBuffer = pool.writeFloatNHWC(16)
        assertNotNull(buf)
        // 16 pixel × 3 channel × 4 byte = 192 byte
        assertEquals(192, buf.capacity())
    }

    @Test
    fun `outputTileSize correct for scale 4`() {
        val pool = BufferPool(128, 4)
        assertEquals(512, pool.outputTileSize())
    }
}

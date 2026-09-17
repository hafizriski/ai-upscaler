package com.arthexdev.exups.ml.optimization

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import java.nio.ByteBuffer
import java.nio.ByteOrder

class BufferPool(tileSize: Int, scale: Int) {

    private val tile = tileSize
    private val outTile = tile * scale

    private val inputBuffer: ByteBuffer =
        ByteBuffer.allocateDirect(tile * tile * 3 * 4).order(ByteOrder.nativeOrder())

    private val paddedBitmap: Bitmap =
        Bitmap.createBitmap(tile, tile, Bitmap.Config.ARGB_8888)

    private val paddedCanvas = Canvas(paddedBitmap)

    private val outputTileBitmap: Bitmap =
        Bitmap.createBitmap(outTile, outTile, Bitmap.Config.ARGB_8888)

    private val srcPixels = IntArray(tile * tile)
    private val outPixels = IntArray(outTile * outTile)

    val fastPaint = Paint().apply {
        isFilterBitmap = false
        isAntiAlias = false
        isDither = false
    }

    fun inputBuffer(): ByteBuffer { inputBuffer.rewind(); return inputBuffer }
    fun paddedBitmap(): Bitmap = paddedBitmap
    fun paddedCanvas(): Canvas = paddedCanvas
    fun outputTileBitmap(): Bitmap = outputTileBitmap
    fun srcPixels(): IntArray = srcPixels
    fun outPixels(): IntArray = outPixels
    fun tileSize(): Int = tile
    fun outputTileSize(): Int = outTile

    fun writeFloatNHWC(pixelCount: Int): ByteBuffer {
        inputBuffer.rewind()
        for (i in 0 until pixelCount) {
            val p = srcPixels[i]
            inputBuffer.putFloat(((p shr 16) and 0xFF) / 255f)
            inputBuffer.putFloat(((p shr 8) and 0xFF) / 255f)
            inputBuffer.putFloat((p and 0xFF) / 255f)
        }
        inputBuffer.rewind(); return inputBuffer
    }

    fun writeFloatNCHW(pixelCount: Int): ByteBuffer {
        inputBuffer.rewind()
        for (i in 0 until pixelCount) inputBuffer.putFloat(((srcPixels[i] shr 16) and 0xFF) / 255f)
        for (i in 0 until pixelCount) inputBuffer.putFloat(((srcPixels[i] shr 8) and 0xFF) / 255f)
        for (i in 0 until pixelCount) inputBuffer.putFloat((srcPixels[i] and 0xFF) / 255f)
        inputBuffer.rewind(); return inputBuffer
    }

    fun writeUint8NHWC(pixelCount: Int): ByteBuffer {
        inputBuffer.rewind()
        for (i in 0 until pixelCount) {
            val p = srcPixels[i]
            inputBuffer.put(((p shr 16) and 0xFF).toByte())
            inputBuffer.put(((p shr 8) and 0xFF).toByte())
            inputBuffer.put((p and 0xFF).toByte())
        }
        inputBuffer.rewind(); return inputBuffer
    }

    fun writeUint8NCHW(pixelCount: Int): ByteBuffer {
        inputBuffer.rewind()
        for (i in 0 until pixelCount) inputBuffer.put(((srcPixels[i] shr 16) and 0xFF).toByte())
        for (i in 0 until pixelCount) inputBuffer.put(((srcPixels[i] shr 8) and 0xFF).toByte())
        for (i in 0 until pixelCount) inputBuffer.put((srcPixels[i] and 0xFF).toByte())
        inputBuffer.rewind(); return inputBuffer
    }

    fun writeInt8NHWC(pixelCount: Int): ByteBuffer {
        inputBuffer.rewind()
        for (i in 0 until pixelCount) {
            val p = srcPixels[i]
            inputBuffer.put((((p shr 16) and 0xFF) - 128).toByte())
            inputBuffer.put((((p shr 8) and 0xFF) - 128).toByte())
            inputBuffer.put(((p and 0xFF) - 128).toByte())
        }
        inputBuffer.rewind(); return inputBuffer
    }

    fun writeInt8NCHW(pixelCount: Int): ByteBuffer {
        inputBuffer.rewind()
        for (i in 0 until pixelCount) inputBuffer.put((((srcPixels[i] shr 16) and 0xFF) - 128).toByte())
        for (i in 0 until pixelCount) inputBuffer.put((((srcPixels[i] shr 8) and 0xFF) - 128).toByte())
        for (i in 0 until pixelCount) inputBuffer.put(((srcPixels[i] and 0xFF) - 128).toByte())
        inputBuffer.rewind(); return inputBuffer
    }

    fun recycle() {
        try { paddedBitmap.recycle() } catch (_: Throwable) {}
        try { outputTileBitmap.recycle() } catch (_: Throwable) {}
    }
}

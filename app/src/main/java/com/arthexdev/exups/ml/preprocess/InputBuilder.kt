package com.arthexdev.exups.ml.preprocess

import android.graphics.Bitmap
import com.arthexdev.exups.ml.engine.InterpreterState
import com.arthexdev.exups.ml.optimization.BufferPool
import org.tensorflow.lite.DataType
import java.nio.ByteBuffer
import java.nio.ByteOrder

object InputBuilder {

    fun buildWithPool(pool: BufferPool, state: InterpreterState): ByteBuffer {
        val size = state.inH
        val pixelCount = size * size
        val bmp = pool.paddedBitmap()
        bmp.getPixels(pool.srcPixels(), 0, size, 0, 0, size, size)

        return when (state.inputType) {
            DataType.FLOAT32 ->
                if (state.inputIsNCHW) pool.writeFloatNCHW(pixelCount)
                else pool.writeFloatNHWC(pixelCount)
            DataType.UINT8 ->
                if (state.inputIsNCHW) pool.writeUint8NCHW(pixelCount)
                else pool.writeUint8NHWC(pixelCount)
            DataType.INT8 ->
                if (state.inputIsNCHW) pool.writeInt8NCHW(pixelCount)
                else pool.writeInt8NHWC(pixelCount)
            else -> throw IllegalStateException("Input type tidak didukung: ${state.inputType}")
        }
    }

    fun build(bmp: Bitmap, state: InterpreterState): ByteBuffer {
        val size = state.inH
        val px = IntArray(size * size)
        bmp.getPixels(px, 0, size, 0, 0, size, size)
        val buf = ByteBuffer.allocateDirect(size * size * 3 * 4).order(ByteOrder.nativeOrder())
        when (state.inputType) {
            DataType.FLOAT32 -> {
                if (state.inputIsNCHW) {
                    for (i in px.indices) buf.putFloat((px[i] shr 16 and 0xFF) / 255f)
                    for (i in px.indices) buf.putFloat((px[i] shr 8 and 0xFF) / 255f)
                    for (i in px.indices) buf.putFloat((px[i] and 0xFF) / 255f)
                } else for (p in px) {
                    buf.putFloat((p shr 16 and 0xFF) / 255f)
                    buf.putFloat((p shr 8 and 0xFF) / 255f)
                    buf.putFloat((p and 0xFF) / 255f)
                }
            }
            else -> throw IllegalStateException("Legacy hanya support FLOAT32")
        }
        buf.rewind()
        return buf
    }
}

package com.example.aiupscaler.ml.preprocess

import android.graphics.Bitmap
import com.example.aiupscaler.ml.engine.InterpreterState
import org.tensorflow.lite.DataType
import java.nio.ByteBuffer
import java.nio.ByteOrder

object InputBuilder {
    fun build(bmp: Bitmap, state: InterpreterState): ByteBuffer {
        val size = state.inH
        val px = IntArray(size * size)
        bmp.getPixels(px, 0, size, 0, 0, size, size)
        return when (state.inputType) {
            DataType.FLOAT32 -> floatBuf(px, state.inputIsNCHW, size)
            DataType.UINT8 -> uint8Buf(px, state.inputIsNCHW, size)
            DataType.INT8 -> int8Buf(px, state.inputIsNCHW, size)
            else -> throw IllegalStateException("Input type tidak didukung")
        }
    }

    private fun floatBuf(px: IntArray, nchw: Boolean, size: Int): ByteBuffer {
        val b = ByteBuffer.allocateDirect(size * size * 3 * 4).order(ByteOrder.nativeOrder())
        if (nchw) {
            for (i in px.indices) b.putFloat((px[i] shr 16 and 0xFF) / 255f)
            for (i in px.indices) b.putFloat((px[i] shr 8 and 0xFF) / 255f)
            for (i in px.indices) b.putFloat((px[i] and 0xFF) / 255f)
        } else for (p in px) {
            b.putFloat((p shr 16 and 0xFF) / 255f)
            b.putFloat((p shr 8 and 0xFF) / 255f)
            b.putFloat((p and 0xFF) / 255f)
        }
        b.rewind(); return b
    }

    private fun uint8Buf(px: IntArray, nchw: Boolean, size: Int): ByteBuffer {
        val b = ByteBuffer.allocateDirect(size * size * 3).order(ByteOrder.nativeOrder())
        if (nchw) {
            for (i in px.indices) b.put(((px[i] shr 16) and 0xFF).toByte())
            for (i in px.indices) b.put(((px[i] shr 8) and 0xFF).toByte())
            for (i in px.indices) b.put((px[i] and 0xFF).toByte())
        } else for (p in px) {
            b.put(((p shr 16) and 0xFF).toByte())
            b.put(((p shr 8) and 0xFF).toByte())
            b.put((p and 0xFF).toByte())
        }
        b.rewind(); return b
    }

    private fun int8Buf(px: IntArray, nchw: Boolean, size: Int): ByteBuffer {
        val b = ByteBuffer.allocateDirect(size * size * 3).order(ByteOrder.nativeOrder())
        if (nchw) {
            for (i in px.indices) b.put((((px[i] shr 16) and 0xFF) - 128).toByte())
            for (i in px.indices) b.put((((px[i] shr 8) and 0xFF) - 128).toByte())
            for (i in px.indices) b.put(((px[i] and 0xFF) - 128).toByte())
        } else for (p in px) {
            b.put((((p shr 16) and 0xFF) - 128).toByte())
            b.put((((p shr 8) and 0xFF) - 128).toByte())
            b.put(((p and 0xFF) - 128).toByte())
        }
        b.rewind(); return b
    }
}

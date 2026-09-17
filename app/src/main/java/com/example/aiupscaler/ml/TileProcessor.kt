package com.example.aiupscaler.ml

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import org.tensorflow.lite.DataType
import java.nio.ByteBuffer
import java.nio.ByteOrder

class TileProcessor(private val interp: UpscalerInterpreter) {
    private val tile = interp.inputSize
    private val scale = interp.scale.coerceAtLeast(1)
    private val overlap = 8
    private val stride = (tile - overlap).coerceAtLeast(1)

    fun process(src: Bitmap): Bitmap {
        val srcArgb = src.copy(Bitmap.Config.ARGB_8888, false)
        val w = srcArgb.width
        val h = srcArgb.height
        if (w == 0 || h == 0) throw IllegalStateException("Gambar kosong")

        val outW = w * scale
        val outH = h * scale
        val output = Bitmap.createBitmap(outW, outH, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(output)
        val paint = Paint(Paint.FILTER_BITMAP_FLAG)

        var y = 0
        while (y < h) {
            var x = 0
            while (x < w) {
                val cw = minOf(tile, w - x)
                val ch = minOf(tile, h - y)
                val padded = extractPadded(srcArgb, x, y, cw, ch, tile)
                val outNhwc = interp.run(makeInputBuffer(padded))
                val tileBmp = toBitmap(outNhwc, tile * scale)
                val crop = Bitmap.createBitmap(tileBmp, 0, 0, cw * scale, ch * scale)
                canvas.drawBitmap(crop, (x * scale).toFloat(), (y * scale).toFloat(), paint)

                x += stride
                if (x + tile > w) x = maxOf(0, w - tile)
                if (x >= w) break
            }
            y += stride
            if (y + tile > h) y = maxOf(0, h - tile)
            if (y >= h) break
        }
        return output
    }

    private fun extractPadded(src: Bitmap, x: Int, y: Int, cw: Int, ch: Int, size: Int): Bitmap {
        val bmp = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        val patch = Bitmap.createBitmap(src, x, y, cw, ch)
        Canvas(bmp).drawBitmap(patch, 0f, 0f, null)
        return bmp
    }

    /**
     * Buat ByteBuffer input sesuai tipe data & format yang dibutuhkan model.
     */
    private fun makeInputBuffer(bmp: Bitmap): ByteBuffer {
        val px = IntArray(tile * tile)
        bmp.getPixels(px, 0, tile, 0, 0, tile, tile)

        val dtype = interp.inputDataType
        val isNchw = interp.inputIsNCHW

        return when (dtype) {
            DataType.FLOAT32 -> makeFloatBuffer(px, isNchw)
            DataType.UINT8 -> makeUint8Buffer(px, isNchw)
            DataType.INT8 -> makeInt8Buffer(px, isNchw)
            else -> throw IllegalStateException("Tipe input tidak didukung: $dtype")
        }
    }

    private fun makeFloatBuffer(px: IntArray, nchw: Boolean): ByteBuffer {
        val buf = ByteBuffer.allocateDirect(tile * tile * 3 * 4).order(ByteOrder.nativeOrder())
        if (nchw) {
            for (i in px.indices) buf.putFloat((px[i] shr 16 and 0xFF) / 255f)
            for (i in px.indices) buf.putFloat((px[i] shr 8 and 0xFF) / 255f)
            for (i in px.indices) buf.putFloat((px[i] and 0xFF) / 255f)
        } else {
            for (p in px) {
                buf.putFloat((p shr 16 and 0xFF) / 255f)
                buf.putFloat((p shr 8 and 0xFF) / 255f)
                buf.putFloat((p and 0xFF) / 255f)
            }
        }
        buf.rewind()
        return buf
    }

    private fun makeUint8Buffer(px: IntArray, nchw: Boolean): ByteBuffer {
        val buf = ByteBuffer.allocateDirect(tile * tile * 3).order(ByteOrder.nativeOrder())
        if (nchw) {
            for (i in px.indices) buf.put(((px[i] shr 16) and 0xFF).toByte())
            for (i in px.indices) buf.put(((px[i] shr 8) and 0xFF).toByte())
            for (i in px.indices) buf.put((px[i] and 0xFF).toByte())
        } else {
            for (p in px) {
                buf.put(((p shr 16) and 0xFF).toByte())
                buf.put(((p shr 8) and 0xFF).toByte())
                buf.put((p and 0xFF).toByte())
            }
        }
        buf.rewind()
        return buf
    }

    private fun makeInt8Buffer(px: IntArray, nchw: Boolean): ByteBuffer {
        val buf = ByteBuffer.allocateDirect(tile * tile * 3).order(ByteOrder.nativeOrder())
        if (nchw) {
            for (i in px.indices) buf.put((((px[i] shr 16) and 0xFF) - 128).toByte())
            for (i in px.indices) buf.put((((px[i] shr 8) and 0xFF) - 128).toByte())
            for (i in px.indices) buf.put(((px[i] and 0xFF) - 128).toByte())
        } else {
            for (p in px) {
                buf.put((((p shr 16) and 0xFF) - 128).toByte())
                buf.put((((p shr 8) and 0xFF) - 128).toByte())
                buf.put(((p and 0xFF) - 128).toByte())
            }
        }
        buf.rewind()
        return buf
    }

    /**
     * data: NHWC [size][size][3] float [0..1]
     */
    private fun toBitmap(data: Array<Array<FloatArray>>, size: Int): Bitmap {
        val bmp = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        val px = IntArray(size * size)
        var i = 0
        for (yy in 0 until size) for (xx in 0 until size) {
            val r = (data[yy][xx][0] * 255f).toInt().coerceIn(0, 255)
            val g = (data[yy][xx][1] * 255f).toInt().coerceIn(0, 255)
            val b = (data[yy][xx][2] * 255f).toInt().coerceIn(0, 255)
            px[i++] = (0xFF shl 24) or (r shl 16) or (g shl 8) or b
        }
        bmp.setPixels(px, 0, size, 0, 0, size, size)
        return bmp
    }
}

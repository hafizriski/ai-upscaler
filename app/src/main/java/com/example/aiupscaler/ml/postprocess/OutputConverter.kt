package com.example.aiupscaler.ml.postprocess

import android.graphics.Bitmap

/**
 * Konversi output interpreter (NHWC float [0..1]) ke Bitmap.
 */
object OutputConverter {

    fun toBitmap(data: Array<Array<FloatArray>>, size: Int): Bitmap {
        val bmp = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        val px = IntArray(size * size)
        var i = 0
        for (y in 0 until size) {
            for (x in 0 until size) {
                val r = (data[y][x][0] * 255f).toInt().coerceIn(0, 255)
                val g = (data[y][x][1] * 255f).toInt().coerceIn(0, 255)
                val b = (data[y][x][2] * 255f).toInt().coerceIn(0, 255)
                px[i++] = (0xFF shl 24) or (r shl 16) or (g shl 8) or b
            }
        }
        bmp.setPixels(px, 0, size, 0, 0, size, size)
        return bmp
    }
}

package com.example.aiupscaler.ml.fallback

import android.graphics.Bitmap

/**
 * Fallback upscaling dengan bilinear + unsharp mask.
 * Dijamin selalu berhasil.
 */
object BilinearUpscaler {

    fun upscale(src: Bitmap, factor: Int): Bitmap {
        val w = src.width * factor
        val h = src.height * factor
        val scaled = Bitmap.createScaledBitmap(src, w, h, true)
        return unsharpMask(scaled, amount = 0.4f)
    }

    private fun unsharpMask(src: Bitmap, amount: Float): Bitmap {
        val w = src.width
        val h = src.height
        if (w < 3 || h < 3) return src

        val px = IntArray(w * h)
        src.getPixels(px, 0, w, 0, 0, w, h)
        val out = px.copyOf()

        val center = 1 + 4 * amount
        val side = -amount

        for (y in 1 until h - 1) {
            for (x in 1 until w - 1) {
                val i = y * w + x
                val p = px[i]
                val l = px[i - 1]
                val r = px[i + 1]
                val t = px[i - w]
                val b = px[i + w]

                val rr = clamp(center * ((p shr 16) and 0xFF) +
                        side * (((l shr 16) and 0xFF) + ((r shr 16) and 0xFF) +
                                ((t shr 16) and 0xFF) + ((b shr 16) and 0xFF)))
                val gg = clamp(center * ((p shr 8) and 0xFF) +
                        side * (((l shr 8) and 0xFF) + ((r shr 8) and 0xFF) +
                                ((t shr 8) and 0xFF) + ((b shr 8) and 0xFF)))
                val bb = clamp(center * (p and 0xFF) +
                        side * ((l and 0xFF) + (r and 0xFF) +
                                (t and 0xFF) + (b and 0xFF)))

                out[i] = (0xFF shl 24) or (rr shl 16) or (gg shl 8) or bb
            }
        }
        val bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        bmp.setPixels(out, 0, w, 0, 0, w, h)
        return bmp
    }

    private fun clamp(v: Float): Int = v.toInt().coerceIn(0, 255)
}

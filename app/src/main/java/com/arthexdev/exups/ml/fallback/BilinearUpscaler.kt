package com.arthexdev.exups.ml.fallback

import android.graphics.Bitmap

object BilinearUpscaler {
    fun upscale(src: Bitmap, factor: Int): Bitmap {
        val w = src.width * factor
        val h = src.height * factor
        return Bitmap.createScaledBitmap(src, w, h, true)
    }
}

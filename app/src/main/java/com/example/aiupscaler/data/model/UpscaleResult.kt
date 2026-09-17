package com.example.aiupscaler.data.model

import android.graphics.Bitmap

sealed class UpscaleResult {
    data class Success(
        val bitmap: Bitmap,
        val width: Int,
        val height: Int,
        val elapsedMs: Long
    ) : UpscaleResult()

    data class Error(
        val message: String,
        val cause: Throwable? = null
    ) : UpscaleResult()
}

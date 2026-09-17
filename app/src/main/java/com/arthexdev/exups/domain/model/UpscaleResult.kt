package com.arthexdev.exups.domain.model

import android.graphics.Bitmap

data class UpscaleResult(
    val bitmap: Bitmap,
    val originalWidth: Int,
    val originalHeight: Int,
    val scaleFactor: Int,
    val elapsedMs: Long,
    val tilesProcessed: Int,
    val tilesFailed: Int,
    val usedFallback: Boolean,
    val backend: String
)

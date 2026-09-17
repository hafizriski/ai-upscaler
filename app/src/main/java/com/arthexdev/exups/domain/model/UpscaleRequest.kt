package com.arthexdev.exups.domain.model

import android.graphics.Bitmap
import com.arthexdev.exups.ml.engine.Backend

data class UpscaleRequest(
    val sourceBitmap: Bitmap,
    val backend: Backend = Backend.CPU,
    val threadCount: Int = 8,
    val modelId: String = "x4v3"
)

package com.example.aiupscaler.domain.model

import android.graphics.Bitmap
import com.example.aiupscaler.ml.engine.Backend

data class UpscaleRequest(
    val sourceBitmap: Bitmap,
    val backend: Backend = Backend.CPU,
    val threadCount: Int = 0    // 0 = auto
)

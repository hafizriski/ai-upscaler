package com.example.aiupscaler.domain.model

import android.graphics.Bitmap
import com.example.aiupscaler.ml.engine.Backend

data class UpscaleRequest(
    val sourceBitmap: Bitmap,
    val backend: Backend = Backend.CPU
)

package com.example.aiupscaler.domain.model

import com.example.aiupscaler.ml.engine.Backend

data class UpscaleRequest(
    val sourceBitmap: android.graphics.Bitmap,
    val backend: Backend = Backend.CPU,
    val tileSize: Int = 128,
    val tileOverlap: Int = 8,
    val outputFormat: OutputFormat = OutputFormat.PNG
)

enum class OutputFormat(val mime: String, val ext: String) {
    PNG("image/png", "png"),
    JPEG("image/jpeg", "jpg"),
    WEBP("image/webp", "webp")
}

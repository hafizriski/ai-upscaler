package com.example.aiupscaler.data.model

import com.example.aiupscaler.ml.Backend

data class UpscaleRequest(
    val sourceUri: String?,
    val backend: Backend = Backend.CPU,
    val outputFormat: String = "PNG",
    val jpegQuality: Int = 95
)

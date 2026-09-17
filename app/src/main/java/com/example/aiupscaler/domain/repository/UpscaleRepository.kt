package com.example.aiupscaler.domain.repository

import com.example.aiupscaler.core.result.AppResult
import com.example.aiupscaler.domain.model.ProgressEvent
import com.example.aiupscaler.domain.model.UpscaleRequest
import com.example.aiupscaler.domain.model.UpscaleResult

interface UpscaleRepository {
    fun isModelAvailable(): Boolean
    suspend fun upscale(
        request: UpscaleRequest,
        emit: (ProgressEvent) -> Unit
    ): AppResult<UpscaleResult>
}

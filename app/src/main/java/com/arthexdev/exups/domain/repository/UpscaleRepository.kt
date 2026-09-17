package com.arthexdev.exups.domain.repository

import com.arthexdev.exups.core.result.AppResult
import com.arthexdev.exups.domain.model.ProgressEvent
import com.arthexdev.exups.domain.model.UpscaleRequest
import com.arthexdev.exups.domain.model.UpscaleResult

interface UpscaleRepository {
    fun isModelAvailable(): Boolean
    suspend fun upscale(
        request: UpscaleRequest,
        emit: (ProgressEvent) -> Unit
    ): AppResult<UpscaleResult>
}

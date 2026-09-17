package com.example.aiupscaler.domain.usecase

import com.example.aiupscaler.core.result.AppResult
import com.example.aiupscaler.domain.model.ProgressEvent
import com.example.aiupscaler.domain.model.UpscaleRequest
import com.example.aiupscaler.domain.model.UpscaleResult
import com.example.aiupscaler.domain.repository.UpscaleRepository

class UpscaleImageUseCase(private val repo: UpscaleRepository) {
    suspend operator fun invoke(
        request: UpscaleRequest,
        emit: (ProgressEvent) -> Unit
    ): AppResult<UpscaleResult> = repo.upscale(request, emit)

    fun isModelAvailable(): Boolean = repo.isModelAvailable()
}

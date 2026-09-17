package com.arthexdev.exups.domain.usecase

import com.arthexdev.exups.core.result.AppResult
import com.arthexdev.exups.domain.model.ProgressEvent
import com.arthexdev.exups.domain.model.UpscaleRequest
import com.arthexdev.exups.domain.model.UpscaleResult
import com.arthexdev.exups.domain.repository.UpscaleRepository

class UpscaleImageUseCase(private val repo: UpscaleRepository) {
    suspend operator fun invoke(
        request: UpscaleRequest,
        emit: (ProgressEvent) -> Unit
    ): AppResult<UpscaleResult> = repo.upscale(request, emit)

    fun isModelAvailable(): Boolean = repo.isModelAvailable()
}

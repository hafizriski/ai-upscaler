package com.example.aiupscaler.data.repository

import android.content.Context
import com.example.aiupscaler.core.error.AppError
import com.example.aiupscaler.core.result.AppResult
import com.example.aiupscaler.core.telemetry.Telemetry
import com.example.aiupscaler.domain.model.ProgressEvent
import com.example.aiupscaler.domain.model.UpscaleRequest
import com.example.aiupscaler.domain.model.UpscaleResult
import com.example.aiupscaler.domain.repository.UpscaleRepository
import com.example.aiupscaler.ml.TileProcessor
import com.example.aiupscaler.ml.engine.AdaptiveInterpreter
import com.example.aiupscaler.ml.fallback.BilinearUpscaler
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class UpscaleRepositoryImpl(private val context: Context) : UpscaleRepository {

    companion object {
        const val MODEL_ASSET = "models/realesr_general_x4v3.tflite"
    }

    override fun isModelAvailable(): Boolean = try {
        context.assets.openFd(MODEL_ASSET).use { true }
    } catch (_: Throwable) { false }

    override suspend fun upscale(
        request: UpscaleRequest,
        emit: (ProgressEvent) -> Unit
    ): AppResult<UpscaleResult> = withContext(Dispatchers.Default) {

        val startTime = System.currentTimeMillis()
        emit(ProgressEvent.Log("Memuat model (threads=${request.threadCount})..."))

        val loadResult = AdaptiveInterpreter.load(
            context, MODEL_ASSET, request.backend, request.threadCount
        )
        when (loadResult) {
            is AppResult.Failure -> {
                emit(ProgressEvent.Warning("AI tidak tersedia, fallback bilinear"))
                return@withContext fallback(request, startTime, emit)
            }
            is AppResult.Success -> {
                val engine = loadResult.data
                emit(ProgressEvent.Log("Model: ${engine.state.describe()}"))
                return@withContext try {
                    when (val out = TileProcessor(engine).process(request.sourceBitmap, emit)) {
                        is TileProcessor.ProcessOutcome.Success -> {
                            val result = UpscaleResult(
                                bitmap = out.bitmap,
                                originalWidth = request.sourceBitmap.width,
                                originalHeight = request.sourceBitmap.height,
                                scaleFactor = engine.state.scaleFactor,
                                elapsedMs = out.elapsedMs,
                                tilesProcessed = out.tilesProcessed,
                                tilesFailed = out.tilesFailed,
                                usedFallback = out.tilesFailed > 0,
                                backend = request.backend.label
                            )
                            emit(ProgressEvent.Complete(result))
                            AppResult.Success(result)
                        }
                        is TileProcessor.ProcessOutcome.Failure -> {
                            emit(ProgressEvent.Warning("Proses gagal: ${out.message}"))
                            fallback(request, startTime, emit)
                        }
                    }
                } finally {
                    try { engine.close() } catch (_: Throwable) {}
                }
            }
        }
    }

    private fun fallback(
        request: UpscaleRequest,
        startTime: Long,
        emit: (ProgressEvent) -> Unit
    ): AppResult<UpscaleResult> {
        return try {
            emit(ProgressEvent.Log("Fallback bilinear 4×"))
            val out = BilinearUpscaler.upscale(request.sourceBitmap, 4)
            val result = UpscaleResult(
                bitmap = out,
                originalWidth = request.sourceBitmap.width,
                originalHeight = request.sourceBitmap.height,
                scaleFactor = 4,
                elapsedMs = System.currentTimeMillis() - startTime,
                tilesProcessed = 1, tilesFailed = 0,
                usedFallback = true, backend = "Bilinear"
            )
            emit(ProgressEvent.Complete(result))
            AppResult.Success(result)
        } catch (e: Throwable) {
            AppResult.Failure(AppError.InferenceFailed("Fallback gagal: ${e.message}", e))
        }
    }
}

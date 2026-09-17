package com.example.aiupscaler.ui.main

import android.app.Application
import android.graphics.Bitmap
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.aiupscaler.core.di.ServiceLocator
import com.example.aiupscaler.core.error.AppError
import com.example.aiupscaler.core.result.AppResult
import com.example.aiupscaler.domain.model.ProgressEvent
import com.example.aiupscaler.domain.model.UpscaleRequest
import com.example.aiupscaler.domain.model.UpscaleResult
import com.example.aiupscaler.domain.usecase.UpscaleImageUseCase
import com.example.aiupscaler.ml.engine.Backend
import com.example.aiupscaler.ml.engine.ModelRegistry
import com.example.aiupscaler.ml.engine.ModelSpec
import com.example.aiupscaler.util.GpuDetector
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

enum class StatusKind { IDLE, RUNNING, DONE, ERROR, CANCELLED }

data class MainUiState(
    val source: Bitmap? = null,
    val result: Bitmap? = null,
    val backend: Backend = Backend.CPU,
    val threadCount: Int = 4,
    val maxThreads: Int = 8,
    val gpuInfo: GpuDetector.GpuInfo? = null,
    val availableModels: List<ModelSpec> = emptyList(),
    val selectedModelId: String = "x4plus",
    val statusText: String = "Siap",
    val statusKind: StatusKind = StatusKind.IDLE,
    val progressPercent: Int = 0,
    val progressText: String = "Menunggu…",
    val info: String = "Pilih gambar untuk memulai",
    val log: List<String> = emptyList(),
    val processing: Boolean = false,
    val canCancel: Boolean = false,
    val modelAvailable: Boolean = true
) {
    val selectedModel: ModelSpec?
        get() = availableModels.firstOrNull { it.id == selectedModelId }
}

class MainViewModel(app: Application) : AndroidViewModel(app) {

    private val useCase: UpscaleImageUseCase
    private val gpuInfo: GpuDetector.GpuInfo
    private val recommendedThreads: Int
    private val maxCores: Int
    private var processingJob: Job? = null

    init {
        ServiceLocator.init(app)
        useCase = ServiceLocator.provideUpscaleUseCase()
        gpuInfo = GpuDetector.detect()
        recommendedThreads = GpuDetector.recommendedCpuThreads()
        maxCores = Runtime.getRuntime().availableProcessors().coerceAtLeast(1)
    }

    private val _state = MutableStateFlow(
        MainUiState(
            gpuInfo = gpuInfo,
            maxThreads = maxCores,
            threadCount = recommendedThreads,
            log = listOf(
                "GPU: ${gpuInfo.renderer}",
                "Adreno: ${gpuInfo.adrenoSeries}",
                "Vulkan: ${if (gpuInfo.supportsVulkan) "✅ API ${gpuInfo.vulkanApiLevel}" else "❌"}",
                "CPU threads: $recommendedThreads"
            )
        )
    )
    val state: StateFlow<MainUiState> = _state.asStateFlow()

    init {
        val available = ModelRegistry.getAvailable(getApplication())
        _state.update {
            it.copy(
                availableModels = available,
                selectedModelId = available.firstOrNull()?.id ?: "x4v3",
                modelAvailable = available.isNotEmpty()
            )
        }
    }

    fun setSource(bitmap: Bitmap) {
        _state.update {
            it.copy(source = bitmap, result = null,
                statusText = "Siap", statusKind = StatusKind.IDLE,
                progressPercent = 0, progressText = "Menunggu…",
                info = "Sumber: ${bitmap.width}×${bitmap.height}")
        }
    }

    fun setBackend(backend: Backend) { _state.update { it.copy(backend = backend) } }

    fun setThreadCount(count: Int) {
        _state.update { it.copy(threadCount = count.coerceIn(1, it.maxThreads)) }
    }

    fun setModel(modelId: String) {
        if (_state.value.processing) return
        val spec = ModelRegistry.getById(modelId)
        _state.update {
            it.copy(
                selectedModelId = modelId,
                log = (it.log + "Model: ${spec.displayName}").takeLast(40)
            )
        }
    }

    fun cancel() {
        processingJob?.cancel()
        _state.update {
            it.copy(
                processing = false, canCancel = false,
                statusText = "Dibatalkan", statusKind = StatusKind.CANCELLED,
                progressText = "Dibatalkan oleh pengguna",
                log = (it.log + "✗ Dibatalkan").takeLast(40),
                progressPercent = 0
            )
        }
    }

    fun upscale() {
        val s = _state.value
        val src = s.source ?: return
        if (s.processing) return

        _state.update {
            it.copy(processing = true, canCancel = true,
                statusText = "Memproses…", statusKind = StatusKind.RUNNING,
                progressPercent = 0, progressText = "Memulai…",
                log = it.log + "─ Mulai proses ─")
        }

        processingJob = viewModelScope.launch {
            val result = try {
                useCase(UpscaleRequest(src, s.backend, s.threadCount, s.selectedModelId)) { event ->
                    when (event) {
                        is ProgressEvent.Log -> _state.update { st -> st.copy(log = (st.log + event.message).takeLast(40)) }
                        is ProgressEvent.Warning -> _state.update { st -> st.copy(log = (st.log + "⚠ ${event.message}").takeLast(40)) }
                        is ProgressEvent.Error -> _state.update { st -> st.copy(log = (st.log + "✗ ${event.message}").takeLast(40)) }
                        is ProgressEvent.Stage -> _state.update { st ->
                            st.copy(statusText = event.phase, progressText = event.detail.ifEmpty { event.phase })
                        }
                        is ProgressEvent.TileProgress -> _state.update { st ->
                            val pct = if (event.total > 0)
                                ((event.current.toFloat() / event.total) * 100f).toInt().coerceIn(0, 100)
                            else 0
                            st.copy(statusText = "Proses tile", progressPercent = pct,
                                progressText = "Tile ${event.current.coerceAtMost(event.total)}/${event.total} · ${event.msPerTile}ms/tile")
                        }
                        is ProgressEvent.Complete -> handleComplete(event.result)
                    }
                }
            } catch (e: CancellationException) {
                null
            } catch (e: Throwable) {
                AppResult.Failure(AppError.fromThrowable(e))
            }

            if (result is AppResult.Failure) {
                _state.update {
                    it.copy(processing = false, canCancel = false,
                        statusText = "Gagal", statusKind = StatusKind.ERROR,
                        progressText = result.error.userMessage,
                        log = (it.log + "ERROR: ${result.error.techMessage}").takeLast(40))
                }
            }
        }
    }

    private fun handleComplete(r: UpscaleResult) {
        _state.update {
            it.copy(result = r.bitmap, processing = false, canCancel = false,
                statusText = if (r.usedFallback) "Selesai (fallback)" else "Selesai",
                statusKind = StatusKind.DONE, progressPercent = 100,
                progressText = "Selesai · ${r.bitmap.width}×${r.bitmap.height} · ${r.elapsedMs / 1000}s",
                info = "Hasil: ${r.bitmap.width}×${r.bitmap.height}")
        }
    }
}

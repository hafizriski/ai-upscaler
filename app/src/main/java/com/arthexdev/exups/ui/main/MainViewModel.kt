package com.arthexdev.exups.ui.main

import android.app.Application
import android.graphics.Bitmap
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.arthexdev.exups.core.di.ServiceLocator
import com.arthexdev.exups.core.error.AppError
import com.arthexdev.exups.core.result.AppResult
import com.arthexdev.exups.domain.model.ProgressEvent
import com.arthexdev.exups.domain.model.UpscaleRequest
import com.arthexdev.exups.domain.model.UpscaleResult
import com.arthexdev.exups.domain.usecase.UpscaleImageUseCase
import com.arthexdev.exups.ml.engine.Backend
import com.arthexdev.exups.ml.engine.ModelDownloader
import com.arthexdev.exups.ml.engine.ModelRegistry
import com.arthexdev.exups.ml.engine.ModelSpec
import com.arthexdev.exups.util.GpuDetector
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

enum class StatusKind { IDLE, RUNNING, DOWNLOADING, DONE, ERROR, CANCELLED }

data class MainUiState(
    val source: Bitmap? = null,
    val result: Bitmap? = null,
    val backend: Backend = Backend.CPU,
    val threadCount: Int = 4,
    val maxThreads: Int = 8,
    val gpuInfo: GpuDetector.GpuInfo? = null,
    val allModels: List<ModelSpec> = emptyList(),
    val selectedModelId: String = "x4v3",
    val selectedReady: Boolean = true,
    val statusText: String = "Siap",
    val statusKind: StatusKind = StatusKind.IDLE,
    val progressPercent: Int = 0,
    val progressText: String = "Menunggu…",
    val info: String = "Tap Add untuk pilih gambar",
    val log: List<String> = emptyList(),
    val processing: Boolean = false,
    val downloading: Boolean = false,
    val canCancel: Boolean = false,
    val modelAvailable: Boolean = true
) {
    val selectedModel: ModelSpec?
        get() = allModels.firstOrNull { it.id == selectedModelId }
}

class MainViewModel(app: Application) : AndroidViewModel(app) {

    private val useCase: UpscaleImageUseCase
    private val gpuInfo: GpuDetector.GpuInfo
    private val recommendedThreads: Int
    private val maxCores: Int
    private var processingJob: Job? = null
    private var downloadJob: Job? = null

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
                "Vulkan: ${if (gpuInfo.supportsVulkan) "OK API ${gpuInfo.vulkanApiLevel}" else "tidak didukung"}",
                "CPU threads: $recommendedThreads"
            )
        )
    )
    val state: StateFlow<MainUiState> = _state.asStateFlow()

    init {
        val all = ModelRegistry.getAllForDisplay()
        val default = all.firstOrNull { ModelRegistry.isReady(getApplication(), it) } ?: all.last()
        _state.update {
            it.copy(
                allModels = all,
                selectedModelId = default.id,
                selectedReady = ModelRegistry.isReady(getApplication(), default),
                modelAvailable = true
            )
        }
    }

    fun setSource(bitmap: Bitmap) {
        _state.update {
            it.copy(
                source = bitmap,
                result = null,
                statusText = "Siap",
                statusKind = StatusKind.IDLE,
                progressPercent = 0,
                progressText = "Menunggu…",
                info = "Sumber: ${bitmap.width}×${bitmap.height}"
            )
        }
    }

    fun setBackend(backend: Backend) {
        _state.update { it.copy(backend = backend) }
    }

    fun setThreadCount(count: Int) {
        _state.update { it.copy(threadCount = count.coerceIn(1, it.maxThreads)) }
    }

    fun setModel(modelId: String) {
        if (_state.value.processing || _state.value.downloading) return
        val spec = ModelRegistry.getById(modelId)
        val ready = ModelRegistry.isReady(getApplication(), spec)
        _state.update {
            it.copy(
                selectedModelId = modelId,
                selectedReady = ready,
                log = (it.log + "Model: ${spec.displayName} ${if (ready) "[OK]" else "[belum]"}")
                    .takeLast(40)
            )
        }
    }

    fun cancel() {
        processingJob?.cancel()
        downloadJob?.cancel()
        _state.update {
            it.copy(
                processing = false,
                downloading = false,
                canCancel = false,
                statusText = "Dibatalkan",
                statusKind = StatusKind.CANCELLED,
                progressText = "Dibatalkan oleh pengguna",
                log = (it.log + "X Dibatalkan").takeLast(40),
                progressPercent = 0
            )
        }
    }

    fun ensureModelAndUpscale() {
        val s = _state.value
        val spec = s.selectedModel ?: return
        val src = s.source ?: return
        if (src.width <= 0) return

        if (ModelRegistry.isReady(getApplication(), spec)) {
            upscale()
        } else {
            downloadThenUpscale(spec)
        }
    }

    private fun downloadThenUpscale(spec: ModelSpec) {
        if (_state.value.downloading) return

        _state.update {
            it.copy(
                downloading = true,
                canCancel = true,
                statusText = "Mengunduh model…",
                statusKind = StatusKind.DOWNLOADING,
                progressPercent = 0,
                progressText = "0 MB / ${spec.approxSizeMb} MB",
                log = (it.log + "Download ${spec.displayName}").takeLast(40)
            )
        }

        downloadJob = viewModelScope.launch {
            val success = ModelDownloader.download(
                context = getApplication(),
                url = spec.remoteUrl,
                fileName = spec.fileName,
                expectedSize = spec.approxSizeMb * 1024L * 1024L,
                onProgress = { downloaded, total, percent ->
                    val mbDone = downloaded / 1024 / 1024
                    val mbTotal = if (total > 0) total / 1024 / 1024 else spec.approxSizeMb.toLong()
                    _state.update { st ->
                        st.copy(
                            progressPercent = percent.coerceIn(0, 100),
                            progressText = "$mbDone MB / $mbTotal MB (${percent}%)"
                        )
                    }
                },
                onLog = { msg ->
                    _state.update { st ->
                        st.copy(log = (st.log + msg).takeLast(40))
                    }
                }
            )

            if (success) {
                _state.update {
                    it.copy(
                        downloading = false,
                        canCancel = false,
                        selectedReady = true,
                        log = (it.log + "OK Download selesai").takeLast(40)
                    )
                }
                upscale()
            } else {
                _state.update {
                    it.copy(
                        downloading = false,
                        canCancel = false,
                        statusText = "Download gagal",
                        statusKind = StatusKind.ERROR,
                        progressText = "Periksa koneksi internet",
                        log = (it.log + "X Download gagal").takeLast(40)
                    )
                }
            }
        }
    }

    fun upscale() {
        val s = _state.value
        val src = s.source ?: return
        if (s.processing || s.downloading) return

        _state.update {
            it.copy(
                processing = true,
                canCancel = true,
                statusText = "Memproses…",
                statusKind = StatusKind.RUNNING,
                progressPercent = 0,
                progressText = "Memulai…",
                log = it.log + "-- Mulai --"
            )
        }

        processingJob = viewModelScope.launch {
            val result = try {
                useCase(UpscaleRequest(src, s.backend, s.threadCount, s.selectedModelId)) { event ->
                    when (event) {
                        is ProgressEvent.Log -> _state.update { st ->
                            st.copy(log = (st.log + event.message).takeLast(40))
                        }
                        is ProgressEvent.Warning -> _state.update { st ->
                            st.copy(log = (st.log + "! ${event.message}").takeLast(40))
                        }
                        is ProgressEvent.Error -> _state.update { st ->
                            st.copy(log = (st.log + "X ${event.message}").takeLast(40))
                        }
                        is ProgressEvent.Stage -> _state.update { st ->
                            st.copy(
                                statusText = event.phase,
                                progressText = event.detail.ifEmpty { event.phase }
                            )
                        }
                        is ProgressEvent.TileProgress -> _state.update { st ->
                            val pct = if (event.total > 0)
                                ((event.current.toFloat() / event.total) * 100f).toInt()
                                    .coerceIn(0, 100)
                            else 0
                            st.copy(
                                statusText = "Proses tile",
                                progressPercent = pct,
                                progressText = "Tile ${event.current.coerceAtMost(event.total)}/${event.total} · ${event.msPerTile}ms/tile"
                            )
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
                    it.copy(
                        processing = false,
                        canCancel = false,
                        statusText = "Gagal",
                        statusKind = StatusKind.ERROR,
                        progressText = result.error.userMessage,
                        log = (it.log + "ERROR: ${result.error.techMessage}").takeLast(40)
                    )
                }
            }
        }
    }

    private fun handleComplete(r: UpscaleResult) {
        _state.update {
            it.copy(
                result = r.bitmap,
                processing = false,
                canCancel = false,
                statusText = if (r.usedFallback) "Selesai (fallback)" else "Selesai",
                statusKind = StatusKind.DONE,
                progressPercent = 100,
                progressText = "Selesai · ${r.bitmap.width}×${r.bitmap.height} · ${r.elapsedMs / 1000}s",
                info = "Hasil: ${r.bitmap.width}×${r.bitmap.height}"
            )
        }
    }
}

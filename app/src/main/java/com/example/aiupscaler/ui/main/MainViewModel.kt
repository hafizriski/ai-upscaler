package com.example.aiupscaler.ui.main

import android.app.Application
import android.graphics.Bitmap
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.aiupscaler.core.di.ServiceLocator
import com.example.aiupscaler.core.result.AppResult
import com.example.aiupscaler.core.telemetry.Telemetry
import com.example.aiupscaler.domain.model.ProgressEvent
import com.example.aiupscaler.domain.model.UpscaleRequest
import com.example.aiupscaler.domain.model.UpscaleResult
import com.example.aiupscaler.domain.usecase.UpscaleImageUseCase
import com.example.aiupscaler.ml.engine.Backend
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

enum class StatusKind { IDLE, RUNNING, DONE, ERROR }

data class MainUiState(
    val source: Bitmap? = null,
    val result: Bitmap? = null,
    val backend: Backend = Backend.CPU,
    val statusText: String = "Siap",
    val statusKind: StatusKind = StatusKind.IDLE,
    val progressPercent: Int = 0,
    val progressText: String = "Menunggu…",
    val info: String = "Pilih gambar untuk memulai",
    val log: List<String> = emptyList(),
    val processing: Boolean = false,
    val modelAvailable: Boolean = true,
    val usedFallback: Boolean = false
)

class MainViewModel(app: Application) : AndroidViewModel(app) {

    private val useCase: UpscaleImageUseCase = ServiceLocator.provideUpscaleUseCase()

    private val _state = MutableStateFlow(MainUiState())
    val state: StateFlow<MainUiState> = _state.asStateFlow()

    init {
        ServiceLocator.init(app)
        _state.update { it.copy(modelAvailable = useCase.isModelAvailable()) }
        Telemetry.subscribe { event ->
            // Optional: forward ke Crashlytics / analytics nanti
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
                info = "Sumber: ${bitmap.width}×${bitmap.height}",
                log = listOf("Gambar dimuat: ${bitmap.width}×${bitmap.height}"),
                usedFallback = false
            )
        }
    }

    fun setBackend(backend: Backend) {
        _state.update { it.copy(backend = backend) }
    }

    fun upscale() {
        val s = _state.value
        val src = s.source ?: return
        if (s.processing) return

        _state.update {
            it.copy(
                processing = true,
                statusText = "Memproses…",
                statusKind = StatusKind.RUNNING,
                progressPercent = 0,
                progressText = "Memulai…",
                log = it.log + "─ Memulai proses ─",
                usedFallback = false
            )
        }

        viewModelScope.launch {
            val request = UpscaleRequest(
                sourceBitmap = src,
                backend = s.backend
            )

            val result = useCase(request) { event ->
                when (event) {
                    is ProgressEvent.Log -> {
                        _state.update { it.copy(log = (it.log + event.message).takeLast(40)) }
                    }
                    is ProgressEvent.Warning -> {
                        _state.update { it.copy(log = (it.log + "⚠ ${event.message}").takeLast(40)) }
                    }
                    is ProgressEvent.Error -> {
                        _state.update { it.copy(log = (it.log + "✗ ${event.message}").takeLast(40)) }
                    }
                    is ProgressEvent.Stage -> {
                        _state.update {
                            it.copy(
                                statusText = event.phase,
                                progressText = event.detail.ifEmpty { event.phase }
                            )
                        }
                    }
                    is ProgressEvent.TileProgress -> {
                        _state.update {
                            it.copy(
                                statusText = "Proses tile",
                                progressPercent = (event.current.toFloat() / event.total * 100).toInt(),
                                progressText = String.format(
                                    "Tile %d/%d · %d ms/tile · %ds · ETA %ds",
                                    event.current, event.total, event.msPerTile,
                                    event.elapsedMs / 1000, event.etaMs / 1000
                                )
                            )
                        }
                    }
                    is ProgressEvent.Complete -> {
                        handleComplete(event.result)
                    }
                }
            }

            when (result) {
                is AppResult.Success -> { /* sudah ditangani Complete */ }
                is AppResult.Failure -> {
                    _state.update {
                        it.copy(
                            processing = false,
                            statusText = "Gagal",
                            statusKind = StatusKind.ERROR,
                            progressText = result.error.userMessage,
                            log = (it.log + "ERROR: ${result.error.techMessage}").takeLast(40)
                        )
                    }
                }
            }
        }
    }

    private fun handleComplete(result: UpscaleResult) {
        _state.update {
            it.copy(
                result = result.bitmap,
                processing = false,
                statusText = if (result.usedFallback) "Selesai (fallback)" else "Selesai",
                statusKind = StatusKind.DONE,
                progressPercent = 100,
                progressText = "Selesai · ${result.bitmap.width}×${result.bitmap.height} · ${result.elapsedMs / 1000}s",
                info = "Hasil: ${result.bitmap.width}×${result.bitmap.height}",
                usedFallback = result.usedFallback
            )
        }
    }

    override fun onCleared() {
        super.onCleared()
        Telemetry.info("MainViewModel", "onCleared")
    }
}

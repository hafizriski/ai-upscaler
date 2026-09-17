package com.example.aiupscaler.ui.main

import android.app.Application
import android.graphics.Bitmap
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.aiupscaler.core.di.ServiceLocator
import com.example.aiupscaler.core.result.AppResult
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
    val modelAvailable: Boolean = true
)

class MainViewModel(app: Application) : AndroidViewModel(app) {

    private val useCase: UpscaleImageUseCase

    init {
        ServiceLocator.init(app)
        useCase = ServiceLocator.provideUpscaleUseCase()
    }

    private val _state = MutableStateFlow(MainUiState())
    val state: StateFlow<MainUiState> = _state.asStateFlow()

    init {
        _state.update { it.copy(modelAvailable = useCase.isModelAvailable()) }
    }

    fun setSource(bitmap: Bitmap) {
        _state.update {
            it.copy(
                source = bitmap, result = null,
                statusText = "Siap", statusKind = StatusKind.IDLE,
                progressPercent = 0, progressText = "Menunggu…",
                info = "Sumber: ${bitmap.width}×${bitmap.height}",
                log = listOf("Gambar dimuat: ${bitmap.width}×${bitmap.height}")
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
                processing = true, statusText = "Memproses…",
                statusKind = StatusKind.RUNNING, progressPercent = 0,
                progressText = "Memulai…", log = it.log + "─ Mulai proses ─"
            )
        }

        viewModelScope.launch {
            val result = useCase(UpscaleRequest(src, s.backend)) { event ->
                when (event) {
                    is ProgressEvent.Log -> _state.update { st ->
                        st.copy(log = (st.log + event.message).takeLast(40))
                    }
                    is ProgressEvent.Warning -> _state.update { st ->
                        st.copy(log = (st.log + "⚠ ${event.message}").takeLast(40))
                    }
                    is ProgressEvent.Error -> _state.update { st ->
                        st.copy(log = (st.log + "✗ ${event.message}").takeLast(40))
                    }
                    is ProgressEvent.Stage -> _state.update { st ->
                        st.copy(
                            statusText = event.phase,
                            progressText = event.detail.ifEmpty { event.phase }
                        )
                    }
                    is ProgressEvent.TileProgress -> _state.update { st ->
                        val pct = if (event.total > 0) {
                            ((event.current.toFloat() / event.total) * 100f)
                                .toInt()
                                .coerceIn(0, 100)
                        } else 0
                        val currentSafe = event.current.coerceAtMost(event.total)
                        st.copy(
                            statusText = "Proses tile",
                            progressPercent = pct,
                            progressText = "Tile $currentSafe/${event.total} · ${event.msPerTile}ms/tile"
                        )
                    }
                    is ProgressEvent.Complete -> handleComplete(event.result)
                }
            }

            if (result is AppResult.Failure) {
                _state.update {
                    it.copy(
                        processing = false, statusText = "Gagal",
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
                result = r.bitmap, processing = false,
                statusText = if (r.usedFallback) "Selesai (fallback)" else "Selesai",
                statusKind = StatusKind.DONE, progressPercent = 100,
                progressText = "Selesai · ${r.bitmap.width}×${r.bitmap.height} · ${r.elapsedMs / 1000}s",
                info = "Hasil: ${r.bitmap.width}×${r.bitmap.height}"
            )
        }
    }
}

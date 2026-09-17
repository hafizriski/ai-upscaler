package com.example.aiupscaler.ui.main

import android.app.Application
import android.graphics.Bitmap
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.aiupscaler.data.repository.UpscaleRepository
import com.example.aiupscaler.ml.Backend
import com.example.aiupscaler.ml.ProgressInfo
import com.example.aiupscaler.ml.ProgressListener
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

    private val repo = UpscaleRepository(app)
    private val _state = MutableStateFlow(MainUiState())
    val state: StateFlow<MainUiState> = _state.asStateFlow()

    init {
        _state.update { it.copy(modelAvailable = repo.isModelAvailable()) }
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
                processing = true,
                statusText = "Memuat model…",
                statusKind = StatusKind.RUNNING,
                progressPercent = 0,
                progressText = "Memuat model…",
                log = it.log + "Memuat interpreter…"
            )
        }

        viewModelScope.launch {
            val listener = object : ProgressListener {
                override fun onProgress(info: ProgressInfo) {
                    _state.update {
                        it.copy(
                            statusText = info.phase,
                            statusKind = StatusKind.RUNNING,
                            progressPercent = (info.tileProgress * 100).toInt(),
                            progressText = String.format(
                                "Tile %d/%d · %.0f ms/tile · Elapsed %ds · ETA %ds",
                                info.currentTile, info.totalTiles, info.msPerTile,
                                info.elapsedMs / 1000,
                                if (info.etaMs >= 0) info.etaMs / 1000 else 0
                            )
                        )
                    }
                }
                override fun onLog(message: String) {
                    _state.update { it.copy(log = (it.log + message).takeLast(30)) }
                }
            }

            try {
                val out = repo.upscale(src, s.backend, listener)
                _state.update {
                    it.copy(
                        result = out,
                        processing = false,
                        statusText = "Selesai",
                        statusKind = StatusKind.DONE,
                        progressPercent = 100,
                        progressText = "Selesai · ${out.width}×${out.height}",
                        info = "Hasil: ${out.width}×${out.height}"
                    )
                }
            } catch (e: Throwable) {
                _state.update {
                    it.copy(
                        processing = false,
                        statusText = "Gagal",
                        statusKind = StatusKind.ERROR,
                        progressText = e.message?.take(180) ?: e.javaClass.simpleName,
                        log = (it.log + "ERR: ${e.message}").takeLast(30)
                    )
                }
            }
        }
    }
}

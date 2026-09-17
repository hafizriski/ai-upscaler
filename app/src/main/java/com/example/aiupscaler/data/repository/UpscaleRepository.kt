package com.example.aiupscaler.data.repository

import android.content.Context
import android.graphics.Bitmap
import com.example.aiupscaler.ml.Backend
import com.example.aiupscaler.ml.BilinearFallback
import com.example.aiupscaler.ml.ProgressListener
import com.example.aiupscaler.ml.TileProcessor
import com.example.aiupscaler.ml.UpscalerInterpreter
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class UpscaleRepository(private val context: Context) {

    companion object {
        const val MODEL_ASSET = "models/realesr_general_x4v3.tflite"
    }

    fun isModelAvailable(): Boolean = try {
        context.assets.openFd(MODEL_ASSET).use { true }
    } catch (_: Exception) { false }

    suspend fun upscale(
        source: Bitmap,
        backend: Backend,
        listener: ProgressListener?
    ): Bitmap = withContext(Dispatchers.Default) {

        val engine: UpscalerInterpreter? = try {
            UpscalerInterpreter(context, MODEL_ASSET, backend)
        } catch (e: Throwable) {
            listener?.onLog("❌ Gagal load model: ${e.message}")
            null
        }

        if (engine == null) {
            listener?.onLog("→ Fallback bilinear (tanpa AI)")
            return@withContext BilinearFallback.upscale(source, 4)
        }

        try {
            listener?.onLog("Model: ${engine.modelInfo}")
            TileProcessor(engine).process(source, listener)
        } catch (e: Throwable) {
            listener?.onLog("❌ AI gagal total: ${e.message}")
            listener?.onLog("→ Fallback bilinear penuh")
            BilinearFallback.upscale(source, 4)
        } finally {
            try { engine.close() } catch (_: Throwable) {}
        }
    }
}

package com.example.aiupscaler.data.repository

import android.content.Context
import android.graphics.Bitmap
import com.example.aiupscaler.ml.Backend
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
        val engine = UpscalerInterpreter(context, MODEL_ASSET, backend)
        try {
            listener?.onLog("Interpreter OK: in=${engine.inputSize} scale=${engine.scale}")
            TileProcessor(engine).process(source, listener)
        } finally {
            engine.close()
        }
    }
}

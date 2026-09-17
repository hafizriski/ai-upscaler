package com.example.aiupscaler.ml.engine

import android.content.Context
import com.example.aiupscaler.util.GpuDetector
import org.tensorflow.lite.Interpreter

object DelegateFactory {
    fun build(context: Context, backend: Backend, threadCount: Int = 0): Interpreter.Options {
        val cores = Runtime.getRuntime().availableProcessors()
        val threads = when {
            threadCount > 0 -> threadCount.coerceIn(1, cores)
            else -> GpuDetector.recommendedCpuThreads()
        }
        return Interpreter.Options().apply {
            setNumThreads(threads)
            setUseXNNPACK(true)
        }
    }
}

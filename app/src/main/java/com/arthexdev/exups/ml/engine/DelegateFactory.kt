package com.arthexdev.exups.ml.engine

import android.content.Context
import org.tensorflow.lite.Interpreter

object DelegateFactory {
    fun build(context: Context, backend: Backend, threadCount: Int = 8): Interpreter.Options {
        val cores = Runtime.getRuntime().availableProcessors()
        val threads = if (threadCount > 0) threadCount.coerceIn(1, cores) else 8
        return Interpreter.Options().apply {
            setNumThreads(threads)
            setUseXNNPACK(true)
        }
    }
}

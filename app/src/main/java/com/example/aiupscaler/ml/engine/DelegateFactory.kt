package com.example.aiupscaler.ml.engine

import android.content.Context
import org.tensorflow.lite.Interpreter

object DelegateFactory {
    fun build(context: Context, backend: Backend): Interpreter.Options {
        return Interpreter.Options().apply {
            setNumThreads(Runtime.getRuntime().availableProcessors().coerceIn(1, 4))
            setUseXNNPACK(true)
        }
    }
}

package com.example.aiupscaler.ml

import android.content.Context
import org.tensorflow.lite.Interpreter

object DelegateFactory {
    fun build(context: Context, backend: Backend): Interpreter.Options {
        return Interpreter.Options().apply {
            setNumThreads(Runtime.getRuntime().availableProcessors().coerceAtMost(4))
            setUseXNNPACK(true)
        }
    }
}

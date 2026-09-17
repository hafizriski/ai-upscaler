package com.example.aiupscaler.ml

import android.content.Context
import org.tensorflow.lite.Interpreter

enum class Backend { GPU, CPU }

object DelegateFactory {
    fun build(context: Context, backend: Backend): Pair<Interpreter.Options, List<AutoCloseable>> {
        val opts = Interpreter.Options().apply {
            setNumThreads(Runtime.getRuntime().availableProcessors().coerceAtMost(4))
            setUseXNNPACK(true)
        }
        return opts to emptyList()
    }
}

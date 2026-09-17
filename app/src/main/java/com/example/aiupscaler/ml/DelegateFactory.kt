package com.example.aiupscaler.ml

import android.content.Context
import org.tensorflow.lite.Interpreter
import org.tensorflow.lite.gpu.CompatibilityList
import org.tensorflow.lite.gpu.GpuDelegate

enum class Backend { GPU, CPU }

object DelegateFactory {

    fun build(context: Context, backend: Backend): Pair<Interpreter.Options, List<AutoCloseable>> {
        val opts = Interpreter.Options().apply { setNumThreads(4) }
        val closables = mutableListOf<AutoCloseable>()

        when (backend) {
            Backend.GPU -> {
                val compat = CompatibilityList()
                if (compat.isDelegateSupportedOnThisDevice) {
                    val gpu = GpuDelegate(compat.bestOptionsForThisDevice)
                    opts.addDelegate(gpu)
                    closables += gpu
                } else {
                    opts.setUseXNNPACK(true)
                }
            }
            Backend.CPU -> opts.setUseXNNPACK(true)
        }
        return opts to closables
    }
}

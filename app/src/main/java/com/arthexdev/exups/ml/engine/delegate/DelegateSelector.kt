package com.arthexdev.exups.ml.engine.delegate

import android.content.Context
import android.util.Log
import com.arthexdev.exups.ml.engine.Backend
import com.arthexdev.exups.ml.optimization.PerformanceConfig
import org.tensorflow.lite.Interpreter

/**
 * Auto-pick delegate terbaik: GPU → NNAPI → CPU.
 */
object DelegateSelector {
    private const val TAG = "DelegateSelector"

    data class Selected(
        val options: Interpreter.Options,
        val delegate: Any?,           // GpuDelegate atau NnApiDelegate
        val usedBackend: Backend,
        val note: String
    )

    fun select(context: Context, preferred: Backend, threadCount: Int): Selected {
        val cfg = PerformanceConfig.get(context)

        // Kalau user pilih spesifik, coba itu dulu; kalau gagal, fallback ke AUTO
        if (preferred != Backend.AUTO) {
            when (preferred) {
                Backend.GPU -> GpuDelegateFactory.build(context)?.let {
                    return Selected(it.options, it.delegate, Backend.GPU, "GPU (manual)")
                }
                Backend.NNAPI -> NnApiDelegateFactory.build()?.let {
                    return Selected(it.options, it.delegate, Backend.NNAPI, "NNAPI (manual)")
                }
                Backend.CPU -> return cpuOptions(threadCount, "CPU (manual)")
                Backend.AUTO -> { /* jatuh ke bawah */ }
            }
        }

        // AUTO: GPU → NNAPI → CPU
        GpuDelegateFactory.build(context)?.let {
            return Selected(it.options, it.delegate, Backend.GPU, "Auto → GPU")
        }
        NnApiDelegateFactory.build()?.let {
            return Selected(it.options, it.delegate, Backend.NNAPI, "Auto → NNAPI")
        }
        return cpuOptions(threadCount, "Auto → CPU/XNNPACK")
    }

    private fun cpuOptions(threadCount: Int, note: String): Selected {
        val cores = Runtime.getRuntime().availableProcessors()
        val threads = if (threadCount in 1..cores) threadCount else (cores - 1).coerceAtLeast(2)
        val opts = Interpreter.Options().apply {
            setNumThreads(threads)
            setUseXNNPACK(true)
        }
        return Selected(opts, null, Backend.CPU, note)
    }
}

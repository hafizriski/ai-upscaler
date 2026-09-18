package com.arthexdev.exups.ml.engine.delegate

import android.content.Context
import android.util.Log
import org.tensorflow.lite.Interpreter
import org.tensorflow.lite.gpu.CompatibilityList
import org.tensorflow.lite.gpu.GpuDelegate

object GpuDelegateFactory {
    private const val TAG = "GpuDelegate"

    data class Result(val options: Interpreter.Options, val delegate: GpuDelegate?)

    /**
     * Kompatibel dengan tensorflow-lite-gpu:2.14.0.
     * Pakai default GpuDelegate() tanpa Options biar tidak error API.
     */
    fun build(context: Context): Result? {
        return try {
            val compat = CompatibilityList()
            if (!compat.isDelegateSupportedOnThisDevice) {
                Log.w(TAG, "GPU delegate tidak didukung di device ini")
                return null
            }

            // Pakai default delegate — API 2.14.0 paling stabil
            val delegate = GpuDelegate()
            val interpOpts = Interpreter.Options().apply {
                addDelegate(delegate)
                setNumThreads(1)   // GPU delegate internal sudah multi-thread
            }

            Log.i(TAG, "GPU delegate aktif (default options)")
            Result(interpOpts, delegate)
        } catch (t: Throwable) {
            Log.w(TAG, "GPU delegate gagal: ${t.message}")
            null
        }
    }
}

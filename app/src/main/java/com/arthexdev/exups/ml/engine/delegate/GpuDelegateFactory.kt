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
     * Coba aktifkan GPU delegate (OpenGL ES 3.1+).
     * Return null kalau tidak kompatibel / gagal.
     */
    fun build(context: Context): Result? {
        return try {
            val compat = CompatibilityList()
            if (!compat.isDelegateSupportedOnThisDevice) {
                Log.w(TAG, "GPU delegate tidak didukung di device ini")
                return null
            }

            val opts = GpuDelegate.Options().apply {
                setPrecisionLossAllowed(true)   // FP16 fallback kalau FP32 tidak support
                setInferencePreference(GpuDelegate.Options.INFERENCE_PREFERENCE_SUSTAINED_SPEED)
            }

            val delegate = GpuDelegate(opts)
            val interpOpts = Interpreter.Options().apply {
                addDelegate(delegate)
                setNumThreads(1)   // GPU delegate sudah multi-threaded internal
            }

            Log.i(TAG, "GPU delegate aktif: ${compat.isDelegateSupportedOnThisDevice}")
            Result(interpOpts, delegate)
        } catch (t: Throwable) {
            Log.w(TAG, "GPU delegate gagal: ${t.message}")
            null
        }
    }
}

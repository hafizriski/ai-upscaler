package com.arthexdev.exups.ml.engine.delegate

import android.os.Build
import android.util.Log
import org.tensorflow.lite.Interpreter
import org.tensorflow.lite.nnapi.NnApiDelegate

object NnApiDelegateFactory {
    private const val TAG = "NnApiDelegate"

    data class Result(val options: Interpreter.Options, val delegate: NnApiDelegate?)

    /**
     * Coba aktifkan NNAPI delegate (Android 8.1+, pakai NPU/DSP kalau ada).
     */
    fun build(): Result? {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O_MR1) {
            Log.w(TAG, "NNAPI butuh Android 8.1+")
            return null
        }
        return try {
            val delegate = NnApiDelegate()
            val opts = Interpreter.Options().apply {
                addDelegate(delegate)
                setNumThreads(1)
            }
            Log.i(TAG, "NNAPI delegate aktif")
            Result(opts, delegate)
        } catch (t: Throwable) {
            Log.w(TAG, "NNAPI gagal: ${t.message}")
            null
        }
    }
}

package com.example.aiupscaler.ml.engine

import org.tensorflow.lite.DataType

/**
 * Immutable descriptor tentang model yang dimuat.
 */
data class InterpreterState(
    val inH: Int,
    val inW: Int,
    val outH: Int,
    val outW: Int,
    val inputIsNCHW: Boolean,
    val outputIsNCHW: Boolean,
    val inputType: DataType,
    val outputType: DataType,
    val numInputs: Int,
    val numOutputs: Int,
    val secondInputShape: List<Int>?,
    val secondInputType: DataType?,
    val hasSecondInput: Boolean
) {
    val scaleFactor: Int get() = if (inH > 0) outH / inH else 1

    fun describe(): String = buildString {
        append("in=${inH}×${inW}($inputType,${if (inputIsNCHW) "NCHW" else "NHWC"})")
        append(" out=${outH}×${outW}($outputType,${if (outputIsNCHW) "NCHW" else "NHWC"})")
        append(" inputs=$numInputs outputs=$numOutputs")
        if (hasSecondInput) append(" in2=${secondInputShape}($secondInputType)")
    }
}

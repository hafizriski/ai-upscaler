package com.arthexdev.exups.ml.engine

import org.tensorflow.lite.DataType

data class InterpreterState(
    val inH: Int, val inW: Int, val outH: Int, val outW: Int,
    val inputIsNCHW: Boolean, val outputIsNCHW: Boolean,
    val inputType: DataType, val outputType: DataType,
    val numInputs: Int, val numOutputs: Int,
    val secondInputShape: List<Int>?, val secondInputType: DataType?,
    val hasSecondInput: Boolean
) {
    val scaleFactor: Int get() = if (inH > 0) outH / inH else 1
    fun describe(): String = "in=${inH}×${inW} out=${outH}×${outW}"
}

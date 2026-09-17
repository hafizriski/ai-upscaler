package com.arthexdev.exups.ml.engine

import org.tensorflow.lite.DataType
import java.nio.ByteBuffer
import java.nio.ByteOrder

object StrategyBuilder {
    data class Strategy(val inputs: Array<Any>)

    fun build(imageInput: ByteBuffer, state: InterpreterState): List<Strategy> {
        if (!state.hasSecondInput) return listOf(Strategy(arrayOf(imageInput)))
        val type = state.secondInputType ?: DataType.FLOAT32
        val count = state.secondInputShape?.fold(1) { a, b -> a * b } ?: 0
        if (count == 0) return listOf(Strategy(arrayOf(imageInput)))

        val s = mutableListOf<Strategy>()
        if (count == 2) {
            s += Strategy(arrayOf(imageInput, buf(type, count) { b ->
                putVal(b, type, state.inH.toLong()); putVal(b, type, state.inW.toLong())
            }))
        }
        s += Strategy(arrayOf(imageInput, buf(type, count) { b ->
            for (i in 0 until count) putVal(b, type, 1L)
        }))
        return s
    }

    private fun putVal(b: ByteBuffer, type: DataType, v: Long) {
        when (type) {
            DataType.INT32 -> b.putInt(v.toInt())
            DataType.INT64 -> b.putLong(v)
            DataType.FLOAT32 -> b.putFloat(v.toFloat())
            else -> {}
        }
    }

    private inline fun buf(type: DataType, count: Int, fill: (ByteBuffer) -> Unit): ByteBuffer {
        val bytesPer = when (type) {
            DataType.FLOAT32, DataType.INT32 -> 4
            DataType.INT64 -> 8
            else -> 1
        }
        val b = ByteBuffer.allocateDirect(count * bytesPer).order(ByteOrder.nativeOrder())
        fill(b); b.rewind(); return b
    }
}

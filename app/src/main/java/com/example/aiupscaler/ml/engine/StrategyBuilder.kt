package com.example.aiupscaler.ml.engine

import org.tensorflow.lite.DataType
import java.nio.ByteBuffer
import java.nio.ByteOrder

/**
 * Bangun berbagai strategi untuk input kedua (kalau ada).
 * Setiap strategi adalah Array<Any> yang siap dipakai
 * runForMultipleInputsOutputs.
 */
object StrategyBuilder {

    data class Strategy(val inputs: Array<Any>)

    fun build(imageInput: ByteBuffer, state: InterpreterState): List<Strategy> {
        if (!state.hasSecondInput) {
            return listOf(Strategy(arrayOf(imageInput)))
        }

        val type = state.secondInputType ?: DataType.FLOAT32
        val count = state.secondInputShape?.fold(1) { a, b -> a * b } ?: 0
        if (count == 0) return listOf(Strategy(arrayOf(imageInput)))

        val strategies = mutableListOf<Strategy>()

        // S1: [H, W]
        if (count == 2) {
            strategies += Strategy(arrayOf(
                imageInput,
                buffer(type, count) { b ->
                    when (type) {
                        DataType.INT32 -> { b.putInt(state.inH); b.putInt(state.inW) }
                        DataType.INT64 -> { b.putLong(state.inH.toLong()); b.putLong(state.inW.toLong()) }
                        DataType.FLOAT32 -> { b.putFloat(state.inH.toFloat()); b.putFloat(state.inW.toFloat()) }
                        else -> {}
                    }
                }
            ))
        }

        // S2: semua = 1
        strategies += Strategy(arrayOf(imageInput, buffer(type, count) { b ->
            for (i in 0 until count) putValue(b, type, 1L)
        }))

        // S3: semua = 4 (scale)
        strategies += Strategy(arrayOf(imageInput, buffer(type, count) { b ->
            for (i in 0 until count) putValue(b, type, 4L)
        }))

        // S4: semua = 0
        strategies += Strategy(arrayOf(imageInput, buffer(type, count) { b ->
            for (i in 0 until count) putValue(b, type, 0L)
        }))

        // S5: float 0.5
        if (type == DataType.FLOAT32) {
            strategies += Strategy(arrayOf(imageInput, buffer(type, count) { b ->
                for (i in 0 until count) b.putFloat(0.5f)
            }))
        }

        // S6: mirror [W, H]
        if (count == 2) {
            strategies += Strategy(arrayOf(
                imageInput,
                buffer(type, count) { b ->
                    when (type) {
                        DataType.INT32 -> { b.putInt(state.inW); b.putInt(state.inH) }
                        DataType.INT64 -> { b.putLong(state.inW.toLong()); b.putLong(state.inH.toLong()) }
                        DataType.FLOAT32 -> { b.putFloat(state.inW.toFloat()); b.putFloat(state.inH.toFloat()) }
                        else -> {}
                    }
                }
            ))
        }

        return strategies
    }

    private fun putValue(b: ByteBuffer, type: DataType, v: Long) {
        when (type) {
            DataType.INT32 -> b.putInt(v.toInt())
            DataType.INT64 -> b.putLong(v)
            DataType.FLOAT32 -> b.putFloat(v.toFloat())
            else -> {}
        }
    }

    private inline fun buffer(type: DataType, count: Int, fill: (ByteBuffer) -> Unit): ByteBuffer {
        val bytesPer = when (type) {
            DataType.FLOAT32, DataType.INT32 -> 4
            DataType.INT64 -> 8
            else -> 1
        }
        val b = ByteBuffer.allocateDirect(count * bytesPer).order(ByteOrder.nativeOrder())
        fill(b)
        b.rewind()
        return b
    }
}

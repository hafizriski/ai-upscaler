package com.example.aiupscaler.ml

import android.content.Context
import org.tensorflow.lite.DataType
import org.tensorflow.lite.Interpreter
import java.io.FileInputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.channels.FileChannel

/**
 * Interpreter adaptif yang otomatis mendeteksi format model & mencoba
 * beberapa strategi input kedua sampai berhasil.
 */
class UpscalerInterpreter(
    context: Context,
    modelAsset: String,
    backend: Backend = Backend.CPU
) : AutoCloseable {

    private val interpreter: Interpreter

    val inH: Int
    val inW: Int
    val outH: Int
    val outW: Int
    val inputIsNCHW: Boolean
    val outputIsNCHW: Boolean
    val inputDataType: DataType
    val hasSecondInput: Boolean
    val numInputs: Int
    val secondInputType: DataType?
    val secondInputShape: IntArray?
    val secondInputElementCount: Int

    // Strategi input kedua yang berhasil (index)
    var workingStrategy: Int = -1
        private set

    init {
        val opts = DelegateFactory.build(context, backend)
        interpreter = Interpreter(loadModel(context, modelAsset), opts)

        numInputs = interpreter.inputTensorCount
        val inTensor = interpreter.getInputTensor(0)
        val inShape = inTensor.shape()
        inputDataType = inTensor.dataType()
        val outShape = interpreter.getOutputTensor(0).shape()

        if (inShape.size == 4 && inShape[3] == 3) {
            inputIsNCHW = false; inH = inShape[1]; inW = inShape[2]
        } else if (inShape.size == 4 && inShape[1] == 3) {
            inputIsNCHW = true; inH = inShape[2]; inW = inShape[3]
        } else throw IllegalStateException("Input shape: ${inShape.toList()}")

        if (outShape.size == 4 && outShape[3] == 3) {
            outputIsNCHW = false; outH = outShape[1]; outW = outShape[2]
        } else if (outShape.size == 4 && outShape[1] == 3) {
            outputIsNCHW = true; outH = outShape[2]; outW = outShape[3]
        } else throw IllegalStateException("Output shape: ${outShape.toList()}")

        hasSecondInput = numInputs >= 2
        if (hasSecondInput) {
            val t2 = interpreter.getInputTensor(1)
            secondInputType = t2.dataType()
            secondInputShape = t2.shape()
            secondInputElementCount = secondInputShape!!.fold(1) { a, b -> a * b }
        } else {
            secondInputType = null
            secondInputShape = null
            secondInputElementCount = 0
        }

        if (inH == 0 || outH == 0) throw IllegalStateException("Invalid dims")
    }

    val inputSize: Int get() = inH
    val scale: Int get() = if (inH > 0) outH / inH else 1
    val modelInfo: String
        get() = buildString {
            append("in=${inH}×${inW}(${inputDataType},${if (inputIsNCHW) "NCHW" else "NHWC"})")
            append(" out=${outH}×${outW}(${if (outputIsNCHW) "NCHW" else "NHWC"})")
            append(" inputs=$numInputs")
            if (hasSecondInput) append(" in2=${secondInputShape?.toList()}($secondInputType)")
        }

    fun run(input: ByteBuffer): Array<Array<FloatArray>> {
        val outIsFloat = interpreter.getOutputTensor(0).dataType() == DataType.FLOAT32
        return if (outIsFloat) runFloat(input) else runQuantized(input)
    }

    private fun runFloat(input: ByteBuffer): Array<Array<FloatArray>> {
        val buf = if (outputIsNCHW)
            Array(1) { Array(3) { Array(outH) { FloatArray(outW) } } }
        else
            Array(1) { Array(outH) { Array(outW) { FloatArray(3) } } }

        val outputs = mutableMapOf<Int, Any>(0 to buf)
        runWithFallback(input, outputs)

        @Suppress("UNCHECKED_CAST")
        return if (outputIsNCHW) {
            val nchw = buf as Array<Array<Array<FloatArray>>>
            Array(outH) { y -> Array(outW) { x -> FloatArray(3) { c -> nchw[0][c][y][x] } } }
        } else (buf as Array<Array<Array<FloatArray>>>)[0]
    }

    private fun runQuantized(input: ByteBuffer): Array<Array<FloatArray>> {
        val buf = Array(1) { Array(outH) { Array(outW) { ByteArray(3) } } }
        val outputs = mutableMapOf<Int, Any>(0 to buf)
        runWithFallback(input, outputs)
        return Array(outH) { y -> Array(outW) { x ->
            FloatArray(3) { c -> (buf[0][y][x][c].toInt() and 0xFF) / 255f }
        } }
    }

    /**
     * Coba berbagai strategi sampai berhasil. Kalau satu gagal, coba berikutnya.
     */
    private fun runWithFallback(input: ByteBuffer, outputs: MutableMap<Int, Any>) {
        val strategies = buildStrategies(input)
        var lastError: Throwable? = null

        // Kalau sudah tahu strategi yang berhasil, pakai itu dulu
        val ordered = if (workingStrategy >= 0) {
            listOf(strategies[workingStrategy]) + strategies.filterIndexed { i, _ -> i != workingStrategy }
        } else strategies

        for ((idx, strategy) in ordered.withIndex()) {
            try {
                interpreter.runForMultipleInputsOutputs(strategy.inputs, outputs)
                workingStrategy = strategies.indexOf(strategy)
                return
            } catch (e: Throwable) {
                lastError = e
                if (idx < ordered.size - 1) {
                    // Coba strategi berikutnya, output mungkin sudah terisi
                    // jadi tidak apa-apa, akan ditimpa
                }
            }
        }
        throw lastError ?: IllegalStateException("Semua strategi gagal")
    }

    private data class Strategy(val inputs: Array<Any>)

    private fun buildStrategies(image: ByteBuffer): List<Strategy> {
        if (!hasSecondInput) return listOf(Strategy(arrayOf(image)))

        val strategies = mutableListOf<Strategy>()
        val type = secondInputType ?: DataType.FLOAT32
        val count = secondInputElementCount

        // Strategi 1: [H, W] sesuai tipe model
        if (count == 2) {
            strategies += Strategy(arrayOf(
                image,
                buffer(type, count) { b ->
                    when (type) {
                        DataType.INT32 -> { b.putInt(inH); b.putInt(inW) }
                        DataType.INT64 -> { b.putLong(inH.toLong()); b.putLong(inW.toLong()) }
                        DataType.FLOAT32 -> { b.putFloat(inH.toFloat()); b.putFloat(inW.toFloat()) }
                        else -> {}
                    }
                }
            ))
        }

        // Strategi 2: isi semua elemen dengan 1
        strategies += Strategy(arrayOf(
            image,
            buffer(type, count) { b ->
                for (i in 0 until count) {
                    when (type) {
                        DataType.INT32 -> b.putInt(1)
                        DataType.INT64 -> b.putLong(1L)
                        DataType.FLOAT32 -> b.putFloat(1f)
                        else -> {}
                    }
                }
            }
        ))

        // Strategi 3: isi semua dengan 4 (scale factor x4)
        strategies += Strategy(arrayOf(
            image,
            buffer(type, count) { b ->
                for (i in 0 until count) {
                    when (type) {
                        DataType.INT32 -> b.putInt(4)
                        DataType.INT64 -> b.putLong(4L)
                        DataType.FLOAT32 -> b.putFloat(4f)
                        else -> {}
                    }
                }
            }
        ))

        // Strategi 4: isi dengan 0
        strategies += Strategy(arrayOf(
            image,
            buffer(type, count) { b ->
                for (i in 0 until count) {
                    when (type) {
                        DataType.INT32 -> b.putInt(0)
                        DataType.INT64 -> b.putLong(0L)
                        DataType.FLOAT32 -> b.putFloat(0f)
                        else -> {}
                    }
                }
            }
        ))

        // Strategi 5: isi dengan 0.5 (kalau float)
        if (type == DataType.FLOAT32) {
            strategies += Strategy(arrayOf(
                image,
                buffer(type, count) { b ->
                    for (i in 0 until count) b.putFloat(0.5f)
                }
            ))
        }

        return strategies
    }

    private inline fun buffer(type: DataType, count: Int, fill: (ByteBuffer) -> Unit): ByteBuffer {
        val sizePerElement = when (type) {
            DataType.FLOAT32, DataType.INT32 -> 4
            DataType.INT64 -> 8
            else -> 1
        }
        val b = ByteBuffer.allocateDirect(count * sizePerElement).order(ByteOrder.nativeOrder())
        fill(b)
        b.rewind()
        return b
    }

    override fun close() { try { interpreter.close() } catch (_: Throwable) {} }

    private fun loadModel(context: Context, name: String): ByteBuffer {
        context.assets.openFd(name).use { fd ->
            FileInputStream(fd.fileDescriptor).use { fis ->
                return fis.channel.map(
                    FileChannel.MapMode.READ_ONLY,
                    fd.startOffset, fd.declaredLength
                ).order(ByteOrder.nativeOrder())
            }
        }
    }
}

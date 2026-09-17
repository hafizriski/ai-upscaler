package com.example.aiupscaler.ml

import android.content.Context
import org.tensorflow.lite.DataType
import org.tensorflow.lite.Interpreter
import java.io.FileInputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.channels.FileChannel

class UpscalerInterpreter(
    context: Context,
    modelAsset: String,
    backend: Backend = Backend.CPU
) : AutoCloseable {

    private val interpreter: Interpreter

    val inH: Int; val inW: Int
    val outH: Int; val outW: Int
    val inputIsNCHW: Boolean
    val outputIsNCHW: Boolean
    val inputDataType: DataType
    val hasSecondInput: Boolean
    val numInputs: Int

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
        if (inH == 0 || outH == 0) throw IllegalStateException("Invalid dims")
    }

    val inputSize: Int get() = inH
    val scale: Int get() = if (inH > 0) outH / inH else 1

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
        interpreter.runForMultipleInputsOutputs(buildInputs(input), outputs)

        @Suppress("UNCHECKED_CAST")
        return if (outputIsNCHW) {
            val nchw = buf as Array<Array<Array<FloatArray>>>
            Array(outH) { y -> Array(outW) { x -> FloatArray(3) { c -> nchw[0][c][y][x] } } }
        } else (buf as Array<Array<Array<FloatArray>>>)[0]
    }

    private fun runQuantized(input: ByteBuffer): Array<Array<FloatArray>> {
        val buf = Array(1) { Array(outH) { Array(outW) { ByteArray(3) } } }
        val outputs = mutableMapOf<Int, Any>(0 to buf)
        interpreter.runForMultipleInputsOutputs(buildInputs(input), outputs)
        return Array(outH) { y -> Array(outW) { x ->
            FloatArray(3) { c -> (buf[0][y][x][c].toInt() and 0xFF) / 255f }
        } }
    }

    private fun buildInputs(image: ByteBuffer): Array<Any> {
        if (!hasSecondInput) return arrayOf(image)
        val second = interpreter.getInputTensor(1)
        val shape = second.shape()
        val count = shape.fold(1) { a, b -> a * b }
        val buf = when (second.dataType()) {
            DataType.FLOAT32 -> ByteBuffer.allocateDirect(count * 4).order(ByteOrder.nativeOrder())
                .also { repeat(count) { it.putFloat(1f) }; it.rewind() }
            DataType.INT32 -> ByteBuffer.allocateDirect(count * 4).order(ByteOrder.nativeOrder())
                .also { repeat(count) { it.putInt(1) }; it.rewind() }
            else -> ByteBuffer.allocateDirect(count).order(ByteOrder.nativeOrder())
        }
        return arrayOf(image, buf)
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

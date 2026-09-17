package com.example.aiupscaler.ml

import android.content.Context
import org.tensorflow.lite.DataType
import org.tensorflow.lite.Interpreter
import java.io.FileInputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.channels.FileChannel

/**
 * Fully adaptive interpreter untuk berbagai varian model upscaler TFLite.
 *
 * Mendukung:
 * - Format NHWC: [1, H, W, 3]
 * - Format NCHW: [1, 3, H, W]
 * - DataType: FLOAT32, UINT8, INT8
 * - Model dengan 1 input (gambar) atau 2 input (gambar + scale)
 *
 * Output yang dikembalikan selalu dalam format NHWC: Array[outH][outW][3] float 0..1
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
    val numOutputs: Int

    init {
        val (opts, _) = DelegateFactory.build(context, backend)
        interpreter = Interpreter(loadModel(context, modelAsset), opts)

        numInputs = interpreter.inputTensorCount
        numOutputs = interpreter.outputTensorCount

        val inTensor = interpreter.getInputTensor(0)
        val inShape = inTensor.shape()
        inputDataType = inTensor.dataType()

        val outTensor = interpreter.getOutputTensor(0)
        val outShape = outTensor.shape()

        // Deteksi input format
        if (inShape.size == 4 && inShape[3] == 3) {
            inputIsNCHW = false
            inH = inShape[1]; inW = inShape[2]
        } else if (inShape.size == 4 && inShape[1] == 3) {
            inputIsNCHW = true
            inH = inShape[2]; inW = inShape[3]
        } else {
            throw IllegalStateException(
                "Format input tidak dikenal: ${inShape.toList()} (dtype=${inTensor.dataType()})"
            )
        }

        // Deteksi output format
        if (outShape.size == 4 && outShape[3] == 3) {
            outputIsNCHW = false
            outH = outShape[1]; outW = outShape[2]
        } else if (outShape.size == 4 && outShape[1] == 3) {
            outputIsNCHW = true
            outH = outShape[2]; outW = outShape[3]
        } else {
            throw IllegalStateException(
                "Format output tidak dikenal: ${outShape.toList()} (dtype=${outTensor.dataType()})"
            )
        }

        hasSecondInput = numInputs >= 2

        if (inH == 0 || inW == 0 || outH == 0 || outW == 0) {
            throw IllegalStateException("Dimensi tensor tidak valid (ada yang 0)")
        }
    }

    val inputSize: Int get() = inH
    val scale: Int get() = if (inH > 0) outH / inH else 1

    /**
     * Jalankan inference. Input NHWC float [0..1] dalam ByteBuffer.
     * Return NHWC Array[outH][outW][3] float [0..1].
     */
    fun run(inputNhwcBuffer: ByteBuffer): Array<Array<FloatArray>> {
        // Output container sesuai format model
        val outIsFloat = interpreter.getOutputTensor(0).dataType() == DataType.FLOAT32

        return if (outIsFloat) {
            runFloatOutput(inputNhwcBuffer)
        } else {
            runQuantizedOutput(inputNhwcBuffer)
        }
    }

    private fun runFloatOutput(input: ByteBuffer): Array<Array<FloatArray>> {
        val outputBuffer = if (outputIsNCHW) {
            Array(1) { Array(3) { Array(outH) { FloatArray(outW) } } }
        } else {
            Array(1) { Array(outH) { Array(outW) { FloatArray(3) } } }
        }

        val inputs = buildInputs(input)
        val outputs = mutableMapOf<Int, Any>(0 to outputBuffer)

        interpreter.runForMultipleInputsOutputs(inputs, outputs)

        // Konversi ke NHWC uniform
        @Suppress("UNCHECKED_CAST")
        val raw = outputBuffer as Any
        return if (outputIsNCHW) {
            @Suppress("UNCHECKED_CAST")
            val nchw = raw as Array<Array<Array<FloatArray>>>
            Array(outH) { y ->
                Array(outW) { x ->
                    FloatArray(3) { c -> nchw[0][c][y][x] }
                }
            }
        } else {
            @Suppress("UNCHECKED_CAST")
            (raw as Array<Array<Array<FloatArray>>>)[0]
        }
    }

    private fun runQuantizedOutput(input: ByteBuffer): Array<Array<FloatArray>> {
        // Output quantized: shape NHWC [1, H, W, 3], dtype UINT8 atau INT8
        val outputArray = Array(1) { Array(outH) { Array(outW) { ByteArray(3) } } }

        val inputs = buildInputs(input)
        val outputs = mutableMapOf<Int, Any>(0 to outputArray)

        interpreter.runForMultipleInputsOutputs(inputs, outputs)

        // Konversi ByteArray -> FloatArray [0..1]
        return Array(outH) { y ->
            Array(outW) { x ->
                FloatArray(3) { c ->
                    val raw = outputArray[0][y][x][c].toInt() and 0xFF
                    raw / 255f
                }
            }
        }
    }

    private fun buildInputs(imageBuffer: ByteBuffer): Array<Any> {
        return if (hasSecondInput) {
            // Second input biasanya scale factor (biasanya float32 skalar atau int32)
            val secondTensor = interpreter.getInputTensor(1)
            val secondShape = secondTensor.shape()
            val second = when (secondTensor.dataType()) {
                DataType.FLOAT32 -> {
                    val count = secondShape.fold(1) { acc, i -> acc * i }
                    ByteBuffer.allocateDirect(count * 4)
                        .order(ByteOrder.nativeOrder())
                        .also { buf ->
                            repeat(count) { buf.putFloat(1f) }
                            buf.rewind()
                        }
                }
                DataType.INT32 -> {
                    val count = secondShape.fold(1) { acc, i -> acc * i }
                    ByteBuffer.allocateDirect(count * 4)
                        .order(ByteOrder.nativeOrder())
                        .also { buf ->
                            repeat(count) { buf.putInt(1) }
                            buf.rewind()
                        }
                }
                else -> {
                    val count = secondShape.fold(1) { acc, i -> acc * i }
                    ByteBuffer.allocateDirect(count)
                        .order(ByteOrder.nativeOrder())
                }
            }
            arrayOf(imageBuffer, second)
        } else {
            arrayOf(imageBuffer)
        }
    }

    override fun close() {
        try { interpreter.close() } catch (_: Throwable) {}
    }

    private fun loadModel(context: Context, name: String): ByteBuffer {
        context.assets.openFd(name).use { fd ->
            FileInputStream(fd.fileDescriptor).use { fis ->
                return fis.channel.map(
                    FileChannel.MapMode.READ_ONLY,
                    fd.startOffset,
                    fd.declaredLength
                ).order(ByteOrder.nativeOrder())
            }
        }
    }
}

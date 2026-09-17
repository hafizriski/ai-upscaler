package com.example.aiupscaler.ml.engine

import android.content.Context
import com.example.aiupscaler.core.error.AppError
import com.example.aiupscaler.core.result.AppResult
import com.example.aiupscaler.core.result.runCatchingResult
import com.example.aiupscaler.core.telemetry.Telemetry
import org.tensorflow.lite.DataType
import org.tensorflow.lite.Interpreter
import java.io.FileInputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.channels.FileChannel

class AdaptiveInterpreter private constructor(
    private val interpreter: Interpreter
) : AutoCloseable {

    val state: InterpreterState

    @Volatile private var workingStrategyIndex: Int = -1

    init {
        state = inspectModel()
        Telemetry.info("Interpreter", "Model loaded: ${state.describe()}")
    }

    private fun inspectModel(): InterpreterState {
        val numInputs = interpreter.inputTensorCount
        val numOutputs = interpreter.outputTensorCount

        val inTensor = interpreter.getInputTensor(0)
        val inShape = inTensor.shape()
        val inType = inTensor.dataType()

        val outTensor = interpreter.getOutputTensor(0)
        val outShape = outTensor.shape()
        val outType = outTensor.dataType()

        val inIsNCHW: Boolean; val inH: Int; val inW: Int
        if (inShape.size == 4 && inShape[3] == 3) {
            inIsNCHW = false; inH = inShape[1]; inW = inShape[2]
        } else if (inShape.size == 4 && inShape[1] == 3) {
            inIsNCHW = true; inH = inShape[2]; inW = inShape[3]
        } else throw IllegalStateException("Input shape: ${inShape.toList()}")

        val outIsNCHW: Boolean; val outH: Int; val outW: Int
        if (outShape.size == 4 && outShape[3] == 3) {
            outIsNCHW = false; outH = outShape[1]; outW = outShape[2]
        } else if (outShape.size == 4 && outShape[1] == 3) {
            outIsNCHW = true; outH = outShape[2]; outW = outShape[3]
        } else throw IllegalStateException("Output shape: ${outShape.toList()}")

        val hasSecond = numInputs >= 2
        val secondShape = if (hasSecond) interpreter.getInputTensor(1).shape().toList() else null
        val secondType = if (hasSecond) interpreter.getInputTensor(1).dataType() else null

        return InterpreterState(
            inH, inW, outH, outW, inIsNCHW, outIsNCHW,
            inType, outType, numInputs, numOutputs,
            secondShape, secondType, hasSecond
        )
    }

    fun run(input: ByteBuffer): AppResult<Array<Array<FloatArray>>> =
        runCatchingResult { executeWithFallback(input) }

    private fun executeWithFallback(input: ByteBuffer): Array<Array<FloatArray>> {
        val output: Any = if (state.outputType == DataType.FLOAT32) {
            if (state.outputIsNCHW)
                Array(1) { Array(3) { Array(state.outH) { FloatArray(state.outW) } } }
            else
                Array(1) { Array(state.outH) { Array(state.outW) { FloatArray(3) } } }
        } else {
            Array(1) { Array(state.outH) { Array(state.outW) { ByteArray(3) } } }
        }

        val outputs = mutableMapOf<Int, Any>(0 to output)
        val strategies = StrategyBuilder.build(input, state)

        val ordered = if (workingStrategyIndex in strategies.indices) {
            listOf(strategies[workingStrategyIndex]) +
                strategies.filterIndexed { i, _ -> i != workingStrategyIndex }
        } else strategies

        var lastError: Throwable? = null
        for (strategy in ordered) {
            try {
                interpreter.runForMultipleInputsOutputs(strategy.inputs, outputs)
                workingStrategyIndex = strategies.indexOf(strategy)
                return convertOutput(output)
            } catch (e: Throwable) {
                lastError = e
            }
        }
        throw lastError ?: IllegalStateException("Semua strategi gagal")
    }

    private fun convertOutput(raw: Any): Array<Array<FloatArray>> {
        return when (state.outputType) {
            DataType.FLOAT32 -> {
                @Suppress("UNCHECKED_CAST")
                if (state.outputIsNCHW) {
                    val nchw = raw as Array<Array<Array<FloatArray>>>
                    Array(state.outH) { y -> Array(state.outW) { x ->
                        FloatArray(3) { c -> nchw[0][c][y][x] }
                    } }
                } else (raw as Array<Array<Array<FloatArray>>>)[0]
            }
            else -> {
                @Suppress("UNCHECKED_CAST")
                val bytes = raw as Array<Array<Array<ByteArray>>>
                Array(state.outH) { y -> Array(state.outW) { x ->
                    FloatArray(3) { c -> (bytes[0][y][x][c].toInt() and 0xFF) / 255f }
                } }
            }
        }
    }

    override fun close() { try { interpreter.close() } catch (_: Throwable) {} }

    companion object {
        fun load(context: Context, assetName: String, backend: Backend): AppResult<AdaptiveInterpreter> =
            runCatchingResult {
                val opts = DelegateFactory.build(context, backend)
                val buffer = loadModelBuffer(context, assetName)
                AdaptiveInterpreter(Interpreter(buffer, opts))
            }.let { result ->
                when (result) {
                    is AppResult.Success -> result
                    is AppResult.Failure -> AppResult.Failure(
                        AppError.ModelLoadFailed(result.error.techMessage, result.error.cause)
                    )
                }
            }

        private fun loadModelBuffer(context: Context, name: String): ByteBuffer {
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
}

package com.example.aiupscaler.ml

import android.content.Context
import org.tensorflow.lite.Interpreter
import java.io.FileInputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.channels.FileChannel

class UpscalerInterpreter(
    context: Context,
    modelAsset: String,
    backend: Backend = Backend.GPU
) : AutoCloseable {

    private val interpreter: Interpreter
    private val closables: List<AutoCloseable>
    private val inH: Int
    private val inW: Int
    private val outH: Int
    private val outW: Int

    init {
        val (opts, c) = DelegateFactory.build(context, backend)
        closables = c
        interpreter = Interpreter(loadModel(context, modelAsset), opts)

        val inShape = interpreter.getInputTensor(0).shape()
        val outShape = interpreter.getOutputTensor(0).shape()
        inH = inShape[1]; inW = inShape[2]
        outH = outShape[1]; outW = outShape[2]
    }

    val inputSize: Int get() = inH
    val scale: Int get() = outH / inH

    fun run(input: ByteBuffer): Array<Array<Array<FloatArray>>> {
        val out = Array(1) { Array(outH) { Array(outW) { FloatArray(3) } } }
        interpreter.run(input, out)
        return out
    }

    override fun close() {
        interpreter.close()
        closables.forEach { runCatching { it.close() } }
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

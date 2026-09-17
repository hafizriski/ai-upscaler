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
    backend: Backend = Backend.CPU
) : AutoCloseable {

    private val interpreter: Interpreter
    private val inH: Int
    private val inW: Int
    private val outH: Int
    private val outW: Int

    init {
        val (opts, _) = DelegateFactory.build(context, backend)
        interpreter = Interpreter(loadModel(context, modelAsset), opts)
        val inShape = interpreter.getInputTensor(0).shape()
        val outShape = interpreter.getOutputTensor(0).shape()
        // Real-ESRGAN x4v3: [1,128,128,3] -> [1,512,512,3] NHWC
        inH = inShape[1]; inW = inShape[2]
        outH = outShape[1]; outW = outShape[2]
        if (inH == 0 || inW == 0 || outH == 0 || outW == 0) {
            throw IllegalStateException("Model tidak valid: dimensi 0")
        }
    }

    val inputSize: Int get() = inH
    val scale: Int get() = outH / inH

    fun run(input: ByteBuffer): Array<Array<Array<FloatArray>>> {
        val out = Array(1) { Array(outH) { Array(outW) { FloatArray(3) } } }
        interpreter.run(input, out)
        return out
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

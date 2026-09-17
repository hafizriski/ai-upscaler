package com.example.aiupscaler.ml

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import com.example.aiupscaler.core.result.AppResult
import com.example.aiupscaler.core.telemetry.Telemetry
import com.example.aiupscaler.domain.model.ProgressEvent
import com.example.aiupscaler.ml.engine.AdaptiveInterpreter
import com.example.aiupscaler.ml.fallback.BilinearUpscaler
import com.example.aiupscaler.ml.postprocess.OutputConverter
import com.example.aiupscaler.ml.preprocess.InputBuilder

class TileProcessor(private val engine: AdaptiveInterpreter) {

    sealed class ProcessOutcome {
        data class Success(
            val bitmap: Bitmap,
            val tilesProcessed: Int,
            val tilesFailed: Int,
            val elapsedMs: Long
        ) : ProcessOutcome()
        data class Failure(val message: String) : ProcessOutcome()
    }

    fun process(src: Bitmap, emit: (ProgressEvent) -> Unit): ProcessOutcome {
        val argb = src.copy(Bitmap.Config.ARGB_8888, false)
        val w = argb.width
        val h = argb.height
        if (w <= 0 || h <= 0) return ProcessOutcome.Failure("Gambar kosong")

        val state = engine.state
        val tile = state.inH
        val scale = state.scaleFactor.coerceAtLeast(1)

        val overlap = if (w > tile || h > tile) 8 else 0
        val stride = (tile - overlap).coerceAtLeast(1)

        val outW = w * scale
        val outH = h * scale

        emit(ProgressEvent.Stage("Persiapan", "Output ${outW}×${outH}"))

        val output = Bitmap.createBitmap(outW, outH, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(output)
        val paint = Paint(Paint.FILTER_BITMAP_FLAG)

        // Precompute posisi unik — tidak ada duplikat
        val xPositions = computePositions(w, tile, stride)
        val yPositions = computePositions(h, tile, stride)
        val total = xPositions.size * yPositions.size

        var done = 0
        var failed = 0
        val startTime = System.currentTimeMillis()

        emit(ProgressEvent.Log("Total $total tile (${xPositions.size}×${yPositions.size})"))

        outer@ for (y in yPositions) {
            for (x in xPositions) {
                if (done >= total) break@outer

                val cw = minOf(tile, w - x)
                val ch = minOf(tile, h - y)
                val tileStart = System.currentTimeMillis()

                val ok = try {
                    val patch = Bitmap.createBitmap(argb, x, y, cw, ch)
                    val padded = Bitmap.createBitmap(tile, tile, Bitmap.Config.ARGB_8888)
                    Canvas(padded).drawBitmap(patch, 0f, 0f, null)
                    val inputBuf = InputBuilder.build(padded, state)
                    when (val r = engine.run(inputBuf)) {
                        is AppResult.Success -> {
                            val bmp = OutputConverter.toBitmap(r.data, tile * scale)
                            val cropW = cw * scale
                            val cropH = ch * scale
                            if (cropW <= bmp.width && cropH <= bmp.height) {
                                val crop = Bitmap.createBitmap(bmp, 0, 0, cropW, cropH)
                                canvas.drawBitmap(
                                    crop,
                                    (x * scale).toFloat(),
                                    (y * scale).toFloat(),
                                    paint
                                )
                                true
                            } else false
                        }
                        is AppResult.Failure -> false
                    }
                } catch (e: Throwable) {
                    Telemetry.warn("Tile", "tile gagal: ${e.message}")
                    false
                }

                if (!ok) {
                    failed++
                    try {
                        val patch = Bitmap.createBitmap(argb, x, y, cw, ch)
                        val scaled = BilinearUpscaler.upscale(patch, scale)
                        canvas.drawBitmap(
                            scaled,
                            (x * scale).toFloat(),
                            (y * scale).toFloat(),
                            paint
                        )
                    } catch (_: Throwable) {}
                }

                done++
                val current = done.coerceAtMost(total)
                val elapsed = System.currentTimeMillis() - startTime
                val msPerTile = System.currentTimeMillis() - tileStart
                val avg = if (current > 0) elapsed / current else 0
                val remaining = (total - current).coerceAtLeast(0)
                val eta = remaining * avg

                emit(ProgressEvent.TileProgress(
                    current = current,
                    total = total,
                    msPerTile = msPerTile,
                    elapsedMs = elapsed,
                    etaMs = eta
                ))
            }
        }

        val elapsedTotal = System.currentTimeMillis() - startTime
        emit(ProgressEvent.Log("Selesai ${elapsedTotal / 1000.0}s"))

        return ProcessOutcome.Success(output, done, failed, elapsedTotal)
    }

    /**
     * Hitung posisi tile unik. Tidak ada duplikat, tidak ada yang lewat batas.
     */
    private fun computePositions(size: Int, tile: Int, stride: Int): List<Int> {
        if (size <= tile) return listOf(0)

        val result = mutableListOf<Int>()
        var pos = 0
        while (pos + tile <= size) {
            if (result.isEmpty() || result.last() != pos) result.add(pos)
            pos += stride
        }
        val last = size - tile
        if (result.isEmpty() || result.last() != last) result.add(last)
        return result
    }
}

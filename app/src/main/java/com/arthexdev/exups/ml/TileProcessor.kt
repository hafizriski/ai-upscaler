package com.arthexdev.exups.ml

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.PorterDuff
import android.graphics.Rect
import com.arthexdev.exups.core.result.AppResult
import com.arthexdev.exups.domain.model.ProgressEvent
import com.arthexdev.exups.ml.engine.AdaptiveInterpreter
import com.arthexdev.exups.ml.fallback.BilinearUpscaler
import com.arthexdev.exups.ml.optimization.BufferPool
import com.arthexdev.exups.ml.postprocess.OutputConverter
import com.arthexdev.exups.ml.preprocess.InputBuilder
import kotlinx.coroutines.ensureActive
import kotlin.coroutines.coroutineContext

class TileProcessor(private val engine: AdaptiveInterpreter) {

    sealed class ProcessOutcome {
        data class Success(
            val bitmap: Bitmap, val tilesProcessed: Int,
            val tilesFailed: Int, val elapsedMs: Long
        ) : ProcessOutcome()
        data class Failure(val message: String) : ProcessOutcome()
    }

    suspend fun process(
        src: Bitmap,
        emit: (ProgressEvent) -> Unit,
        overlap: Int = 8,
        batchEmitMs: Long = 200L
    ): ProcessOutcome {
        val argb = if (src.config == Bitmap.Config.ARGB_8888) src
                   else src.copy(Bitmap.Config.ARGB_8888, false)
        val w = argb.width; val h = argb.height
        if (w <= 0 || h <= 0) return ProcessOutcome.Failure("Gambar kosong")

        val state = engine.state
        val tile = state.inH
        val scale = state.scaleFactor.coerceAtLeast(1)
        val effectiveOverlap = if (w > tile || h > tile) overlap else 0
        val stride = (tile - effectiveOverlap).coerceAtLeast(1)
        val outW = w * scale; val outH = h * scale

        emit(ProgressEvent.Stage("Persiapan", "Output ${outW}×${outH} • GPU-ready"))

        val output = Bitmap.createBitmap(outW, outH, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(output)

        val pool = BufferPool(tile, scale)
        val outTileSize = pool.outputTileSize()
        val fastPaint: Paint = pool.fastPaint

        val xPositions = computePositions(w, tile, stride)
        val yPositions = computePositions(h, tile, stride)
        val total = xPositions.size * yPositions.size

        var done = 0; var failed = 0
        val startTime = System.currentTimeMillis()
        var lastEmit = startTime
        var lastMsPerTile = 0L

        val padded = pool.paddedBitmap()
        val paddedCanvas = pool.paddedCanvas()

        try {
            outer@ for (y in yPositions) {
                for (x in xPositions) {
                    if (done >= total) break@outer
                    coroutineContext.ensureActive()

                    val cw = minOf(tile, w - x)
                    val ch = minOf(tile, h - y)
                    val tileStart = System.currentTimeMillis()

                    val ok = try {
                        if (cw != tile || ch != tile) {
                            paddedCanvas.drawColor(0, PorterDuff.Mode.SRC)
                        }
                        val srcRect = Rect(x, y, x + cw, y + ch)
                        val dstRect = Rect(0, 0, cw, ch)
                        paddedCanvas.drawBitmap(argb, srcRect, dstRect, null)

                        val inputBuf = InputBuilder.buildWithPool(pool, state)

                        when (val r = engine.run(inputBuf)) {
                            is AppResult.Success -> {
                                val bmp = OutputConverter.toBitmapWithPool(pool, r.data, outTileSize)
                                val cropW = cw * scale
                                val cropH = ch * scale
                                if (cropW <= bmp.width && cropH <= bmp.height) {
                                    val srcR = Rect(0, 0, cropW, cropH)
                                    val dstR = Rect(x * scale, y * scale,
                                                    x * scale + cropW,
                                                    y * scale + cropH)
                                    canvas.drawBitmap(bmp, srcR, dstR, fastPaint)
                                    true
                                } else false
                            }
                            is AppResult.Failure -> false
                        }
                    } catch (e: kotlinx.coroutines.CancellationException) {
                        throw e
                    } catch (e: Throwable) {
                        false
                    }

                    if (!ok) {
                        failed++
                        try {
                            val patch = Bitmap.createBitmap(argb, x, y, cw, ch)
                            val scaled = BilinearUpscaler.upscale(patch, scale)
                            canvas.drawBitmap(scaled, (x * scale).toFloat(),
                                              (y * scale).toFloat(), fastPaint)
                        } catch (_: Throwable) {}
                    }

                    done++
                    val now = System.currentTimeMillis()
                    lastMsPerTile = now - tileStart

                    if (now - lastEmit >= batchEmitMs || done == total) {
                        val current = done.coerceAtMost(total)
                        val elapsed = now - startTime
                        val avg = if (current > 0) elapsed / current else 0
                        val eta = (total - current).coerceAtLeast(0) * avg
                        emit(ProgressEvent.TileProgress(current, total, lastMsPerTile, elapsed, eta))
                        lastEmit = now
                    }
                }
            }
        } finally {
            pool.recycle()
        }

        val elapsedTotal = System.currentTimeMillis() - startTime
        return ProcessOutcome.Success(output, done, failed, elapsedTotal)
    }

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

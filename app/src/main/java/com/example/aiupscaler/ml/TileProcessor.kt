package com.example.aiupscaler.ml

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import com.example.aiupscaler.core.telemetry.PerformanceProfiler
import com.example.aiupscaler.core.telemetry.Telemetry
import com.example.aiupscaler.domain.model.ProgressEvent
import com.example.aiupscaler.ml.engine.AdaptiveInterpreter
import com.example.aiupscaler.ml.fallback.BilinearUpscaler
import com.example.aiupscaler.ml.postprocess.OutputConverter
import com.example.aiupscaler.ml.preprocess.InputBuilder

/**
 * Tile-based upscaler dengan:
 * - Per-tile error boundary
 * - Per-tile fallback
 * - Telemetry lengkap
 * - Progress events
 */
class TileProcessor(private val engine: AdaptiveInterpreter) {

    fun process(
        src: Bitmap,
        emit: (ProgressEvent) -> Unit
    ): ProcessOutcome {
        val argb = src.copy(Bitmap.Config.ARGB_8888, false)
        val w = argb.width
        val h = argb.height
        val state = engine.state

        if (w <= 0 || h <= 0) {
            return ProcessOutcome.Failure("Gambar kosong")
        }

        val tile = state.inH
        val scale = state.scaleFactor
        val overlap = 8
        val stride = (tile - overlap).coerceAtLeast(1)

        val outW = w * scale
        val outH = h * scale

        emit(ProgressEvent.Stage("Persiapan", "Output ${outW}×${outH}"))

        val output = Bitmap.createBitmap(outW, outH, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(output)
        val paint = Paint(Paint.FILTER_BITMAP_FLAG)

        val tilesX = if (w <= tile) 1 else ((w - tile) / stride) + 2
        val tilesY = if (h <= tile) 1 else ((h - tile) / stride) + 2
        val total = tilesX * tilesY

        var done = 0
        var failed = 0
        val startTime = System.currentTimeMillis()

        emit(ProgressEvent.Log("Total $total tile · $tilesX×$tilesY grid"))

        var y = 0
        while (y < h) {
            var x = 0
            while (x < w) {
                val cw = minOf(tile, w - x)
                val ch = minOf(tile, h - y)
                val tileStart = System.currentTimeMillis()

                val tileResult = PerformanceProfiler.measure("tile.process") {
                    processTile(argb, x, y, cw, ch, tile, scale, state, engine)
                }

                when (tileResult) {
                    is TileResult.Success -> {
                        val crop = if (tileResult.bitmap.width >= cw * scale &&
                                      tileResult.bitmap.height >= ch * scale) {
                            Bitmap.createBitmap(tileResult.bitmap, 0, 0, cw * scale, ch * scale)
                        } else {
                            Bitmap.createScaledBitmap(tileResult.bitmap, cw * scale, ch * scale, true)
                        }
                        canvas.drawBitmap(crop, (x * scale).toFloat(), (y * scale).toFloat(), paint)
                    }
                    is TileResult.Fallback -> {
                        failed++
                        val patch = Bitmap.createBitmap(argb, x, y, cw, ch)
                        val scaled = BilinearUpscaler.upscale(patch, scale)
                        canvas.drawBitmap(scaled, (x * scale).toFloat(), (y * scale).toFloat(), paint)
                    }
                }

                done++
                val elapsed = System.currentTimeMillis() - startTime
                val msPerTile = System.currentTimeMillis() - tileStart
                val avg = if (done > 0) elapsed / done else 0
                val eta = (total - done) * avg

                emit(ProgressEvent.TileProgress(
                    current = done, total = total,
                    msPerTile = msPerTile, elapsedMs = elapsed, etaMs = eta
                ))

                x += stride
                if (x + tile > w) x = maxOf(0, w - tile)
                if (x >= w) break
            }
            y += stride
            if (y + tile > h) y = maxOf(0, h - tile)
            if (y >= h) break
        }

        val elapsedTotal = System.currentTimeMillis() - startTime
        Telemetry.metric("TileProcessor", "total_ms", elapsedTotal)
        Telemetry.metric("TileProcessor", "tiles_total", total)
        Telemetry.metric("TileProcessor", "tiles_failed", failed)

        emit(ProgressEvent.Log("Selesai dalam ${elapsedTotal / 1000.0}s"))

        return ProcessOutcome.Success(
            bitmap = output,
            tilesProcessed = done,
            tilesFailed = failed,
            elapsedMs = elapsedTotal
        )
    }

    private fun processTile(
        argb: Bitmap,
        x: Int, y: Int, cw: Int, ch: Int,
        tileSize: Int, scale: Int,
        state: com.example.aiupscaler.ml.engine.InterpreterState,
        engine: AdaptiveInterpreter
    ): TileResult {
        return try {
            val patch = Bitmap.createBitmap(argb, x, y, cw, ch)
            val padded = Bitmap.createBitmap(tileSize, tileSize, Bitmap.Config.ARGB_8888)
            Canvas(padded).drawBitmap(patch, 0f, 0f, null)

            val inputBuf = InputBuilder.build(padded, state)

            when (val r = engine.run(inputBuf)) {
                is com.example.aiupscaler.core.result.AppResult.Success -> {
                    val bmp = OutputConverter.toBitmap(r.data, tileSize * scale)
                    TileResult.Success(bmp)
                }
                is com.example.aiupscaler.core.result.AppResult.Failure -> {
                    Telemetry.warn("TileProcessor", "AI gagal tile: ${r.error.techMessage}")
                    TileResult.Fallback
                }
            }
        } catch (e: Throwable) {
            Telemetry.warn("TileProcessor", "Exception tile: ${e.message}")
            TileResult.Fallback
        }
    }

    private sealed class TileResult {
        data class Success(val bitmap: Bitmap) : TileResult()
        object Fallback : TileResult()
    }

    sealed class ProcessOutcome {
        data class Success(
            val bitmap: Bitmap,
            val tilesProcessed: Int,
            val tilesFailed: Int,
            val elapsedMs: Long
        ) : ProcessOutcome()
        data class Failure(val message: String) : ProcessOutcome()
    }
}

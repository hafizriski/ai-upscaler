package com.example.aiupscaler.ml

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import org.tensorflow.lite.DataType
import java.nio.ByteBuffer
import java.nio.ByteOrder

class TileProcessor(private val interp: UpscalerInterpreter) {

    private val tile = interp.inputSize
    private val scale = interp.scale.coerceAtLeast(1)
    private val overlap = 8
    private val stride = (tile - overlap).coerceAtLeast(1)

    fun process(src: Bitmap, listener: ProgressListener? = null): Bitmap {
        val argb = src.copy(Bitmap.Config.ARGB_8888, false)
        val w = argb.width
        val h = argb.height
        require(w > 0 && h > 0) { "Gambar kosong" }

        val outW = w * scale
        val outH = h * scale
        val output = Bitmap.createBitmap(outW, outH, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(output)
        val paint = Paint(Paint.FILTER_BITMAP_FLAG)

        val tilesX = if (w <= tile) 1 else ((w - tile) / stride) + 2
        val tilesY = if (h <= tile) 1 else ((h - tile) / stride) + 2
        val total = tilesX * tilesY
        var done = 0
        val t0 = System.currentTimeMillis()
        var lastTileStart = t0

        listener?.onLog("Mulai: ${w}×${h} → ${outW}×${outH} · $total tile")

        var y = 0
        while (y < h) {
            var x = 0
            while (x < w) {
                val cw = minOf(tile, w - x)
                val ch = minOf(tile, h - y)

                lastTileStart = System.currentTimeMillis()
                listener?.onLog("Tile #${done + 1}/$total @ ($x,$y) ${cw}×$ch")

                // Coba AI dulu, kalau gagal → bilinear
                val upscaledTile: Bitmap = try {
                    val padded = extract(argb, x, y, cw, ch, tile)
                    val out = interp.run(makeInput(padded))
                    toBitmap(out, tile * scale)
                } catch (e: Throwable) {
                    listener?.onLog("  ⚠️ AI gagal: ${e.message?.take(80)}")
                    listener?.onLog("  → Fallback bilinear untuk tile ini")
                    val patch = Bitmap.createBitmap(argb, x, y, cw, ch)
                    BilinearFallback.upscale(patch, scale)
                }

                // Kalau AI berhasil, tile lebih besar dari crop; kalau bilinear, tepat crop
                val crop = if (upscaledTile.width >= cw * scale && upscaledTile.height >= ch * scale) {
                    Bitmap.createBitmap(upscaledTile, 0, 0, cw * scale, ch * scale)
                } else {
                    Bitmap.createScaledBitmap(upscaledTile, cw * scale, ch * scale, true)
                }

                canvas.drawBitmap(crop, (x * scale).toFloat(), (y * scale).toFloat(), paint)

                done++
                val elapsed = System.currentTimeMillis() - t0
                val msPerTile = System.currentTimeMillis() - lastTileStart
                val avg = elapsed / done
                val eta = (total - done) * avg

                listener?.onProgress(ProgressInfo(
                    phase = "Proses tile",
                    currentTile = done,
                    totalTiles = total,
                    elapsedMs = elapsed,
                    etaMs = eta,
                    tileProgress = done.toFloat() / total,
                    overallProgress = done.toFloat() / total,
                    msPerTile = msPerTile
                ))

                x += stride
                if (x + tile > w) x = maxOf(0, w - tile)
                if (x >= w) break
            }
            y += stride
            if (y + tile > h) y = maxOf(0, h - tile)
            if (y >= h) break
        }
        listener?.onLog("Selesai: ${(System.currentTimeMillis() - t0) / 1000.0}s")
        return output
    }

    private fun extract(src: Bitmap, x: Int, y: Int, cw: Int, ch: Int, size: Int): Bitmap {
        val bmp = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        val patch = Bitmap.createBitmap(src, x, y, cw, ch)
        Canvas(bmp).drawBitmap(patch, 0f, 0f, null)
        return bmp
    }

    private fun makeInput(bmp: Bitmap): ByteBuffer {
        val px = IntArray(tile * tile)
        bmp.getPixels(px, 0, tile, 0, 0, tile, tile)
        val nchw = interp.inputIsNCHW
        return when (interp.inputDataType) {
            DataType.FLOAT32 -> floatBuf(px, nchw)
            DataType.UINT8 -> uint8Buf(px, nchw)
            DataType.INT8 -> int8Buf(px, nchw)
            else -> throw IllegalStateException("Unsupported dtype: ${interp.inputDataType}")
        }
    }

    private fun floatBuf(px: IntArray, nchw: Boolean): ByteBuffer {
        val b = ByteBuffer.allocateDirect(tile * tile * 3 * 4).order(ByteOrder.nativeOrder())
        if (nchw) {
            for (i in px.indices) b.putFloat((px[i] shr 16 and 0xFF) / 255f)
            for (i in px.indices) b.putFloat((px[i] shr 8 and 0xFF) / 255f)
            for (i in px.indices) b.putFloat((px[i] and 0xFF) / 255f)
        } else for (p in px) {
            b.putFloat((p shr 16 and 0xFF) / 255f)
            b.putFloat((p shr 8 and 0xFF) / 255f)
            b.putFloat((p and 0xFF) / 255f)
        }
        b.rewind(); return b
    }

    private fun uint8Buf(px: IntArray, nchw: Boolean): ByteBuffer {
        val b = ByteBuffer.allocateDirect(tile * tile * 3).order(ByteOrder.nativeOrder())
        if (nchw) {
            for (i in px.indices) b.put(((px[i] shr 16) and 0xFF).toByte())
            for (i in px.indices) b.put(((px[i] shr 8) and 0xFF).toByte())
            for (i in px.indices) b.put((px[i] and 0xFF).toByte())
        } else for (p in px) {
            b.put(((p shr 16) and 0xFF).toByte())
            b.put(((p shr 8) and 0xFF).toByte())
            b.put((p and 0xFF).toByte())
        }
        b.rewind(); return b
    }

    private fun int8Buf(px: IntArray, nchw: Boolean): ByteBuffer {
        val b = ByteBuffer.allocateDirect(tile * tile * 3).order(ByteOrder.nativeOrder())
        if (nchw) {
            for (i in px.indices) b.put((((px[i] shr 16) and 0xFF) - 128).toByte())
            for (i in px.indices) b.put((((px[i] shr 8) and 0xFF) - 128).toByte())
            for (i in px.indices) b.put(((px[i] and 0xFF) - 128).toByte())
        } else for (p in px) {
            b.put((((p shr 16) and 0xFF) - 128).toByte())
            b.put((((p shr 8) and 0xFF) - 128).toByte())
            b.put(((p and 0xFF) - 128).toByte())
        }
        b.rewind(); return b
    }

    private fun toBitmap(d: Array<Array<FloatArray>>, size: Int): Bitmap {
        val bmp = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        val px = IntArray(size * size)
        var i = 0
        for (y in 0 until size) for (x in 0 until size) {
            val r = (d[y][x][0] * 255f).toInt().coerceIn(0, 255)
            val g = (d[y][x][1] * 255f).toInt().coerceIn(0, 255)
            val b = (d[y][x][2] * 255f).toInt().coerceIn(0, 255)
            px[i++] = (0xFF shl 24) or (r shl 16) or (g shl 8) or b
        }
        bmp.setPixels(px, 0, size, 0, 0, size, size)
        return bmp
    }
}

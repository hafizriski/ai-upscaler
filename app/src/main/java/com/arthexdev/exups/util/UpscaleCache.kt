package com.arthexdev.exups.util

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import java.io.File
import java.security.MessageDigest

/**
 * Cache hasil upscale berdasarkan hash gambar + parameter.
 * Kalau user upscale gambar sama dengan setting sama → langsung ambil cache.
 */
object UpscaleCache {

    private const val CACHE_DIR = "upscale_cache"
    private const val MAX_CACHE_MB = 200

    private fun dir(context: Context): File {
        val d = File(context.cacheDir, CACHE_DIR)
        if (!d.exists()) d.mkdirs()
        return d
    }

    private fun key(bitmap: Bitmap, modelId: String, threads: Int, scale: Int): String {
        // Hash: ukuran + model + threads + sample pixel
        val md = MessageDigest.getInstance("MD5")
        md.update("${bitmap.width}x${bitmap.height}_${modelId}_${threads}_${scale}".toByteArray())
        // Sample 100 pixel tengah
        val px = IntArray(100)
        try {
            bitmap.getPixels(px, 0, 10, bitmap.width / 4, bitmap.height / 4,
                            10, 10)
            for (p in px) md.update(p.toString().toByteArray())
        } catch (_: Throwable) {}
        return md.digest().joinToString("") { "%02x".format(it) }
    }

    fun get(context: Context, bitmap: Bitmap, modelId: String, threads: Int, scale: Int): Bitmap? {
        val f = File(dir(context), key(bitmap, modelId, threads, scale) + ".png")
        if (!f.exists() || f.length() < 1024) return null
        return try { BitmapFactory.decodeFile(f.absolutePath) } catch (_: Throwable) { null }
    }

    fun put(context: Context, bitmap: Bitmap, result: Bitmap, modelId: String, threads: Int, scale: Int) {
        try {
            val f = File(dir(context), key(bitmap, modelId, threads, scale) + ".png")
            f.outputStream().use { result.compress(Bitmap.CompressFormat.PNG, 100, it) }
            trim(context)
        } catch (_: Throwable) {}
    }

    private fun trim(context: Context) {
        try {
            val d = dir(context)
            val files = d.listFiles()?.sortedByDescending { it.lastModified() } ?: return
            var total = 0L
            val maxBytes = MAX_CACHE_MB * 1024L * 1024L
            for (f in files) {
                total += f.length()
                if (total > maxBytes) f.delete()
            }
        } catch (_: Throwable) {}
    }

    fun clear(context: Context) {
        try { dir(context).listFiles()?.forEach { it.delete() } } catch (_: Throwable) {}
    }
}

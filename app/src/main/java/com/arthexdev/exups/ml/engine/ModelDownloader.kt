package com.arthexdev.exups.ml.engine

import android.content.Context
import com.arthexdev.exups.core.telemetry.Telemetry
import com.arthexdev.exups.util.NetworkUtils
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.net.HttpURLConnection
import java.net.URL

object ModelDownloader {

    private const val TAG = "ModelDownloader"
    private const val BUFFER_SIZE = 8192
    private const val TIMEOUT_MS = 60000

    fun getModelDir(context: Context): File {
        val dir = File(context.filesDir, "models")
        if (!dir.exists()) dir.mkdirs()
        return dir
    }

    fun getLocalPath(context: Context, fileName: String): File =
        File(getModelDir(context), fileName)

    fun isDownloaded(context: Context, fileName: String, expectedSize: Long = 0): Boolean {
        val f = getLocalPath(context, fileName)
        if (!f.exists()) return false
        if (expectedSize > 0 && f.length() < expectedSize * 0.95) return false
        return f.length() > 1024
    }

    fun deleteLocal(context: Context, fileName: String) {
        val f = getLocalPath(context, fileName)
        if (f.exists()) f.delete()
        val temp = File(f.parentFile, "$fileName.part")
        if (temp.exists()) temp.delete()
    }

    suspend fun download(
        context: Context,
        url: String,
        fileName: String,
        expectedSize: Long = 0,
        onProgress: (downloaded: Long, total: Long, percent: Int) -> Unit = { _, _, _ -> },
        onLog: (String) -> Unit = {}
    ): Boolean = withContext(Dispatchers.IO) {

        if (!NetworkUtils.isOnline(context)) {
            onLog("❌ Tidak ada koneksi internet")
            onLog("   Cek WiFi / data seluler Anda")
            return@withContext false
        }

        val target = getLocalPath(context, fileName)
        val temp = File(target.parentFile, "$fileName.part")

        if (isDownloaded(context, fileName, expectedSize)) {
            onLog("✓ Model sudah ada di storage")
            return@withContext true
        }

        onLog("→ Memulai download…")
        onLog("Koneksi: ${NetworkUtils.describe(context)}")
        onLog("URL: ${url.take(80)}…")

        var conn: HttpURLConnection? = null

        try {
            val startAt = if (temp.exists()) temp.length() else 0L
            if (startAt > 0) onLog("Resume dari: ${startAt / 1024 / 1024} MB")

            var currentUrl = url
            var redirects = 0
            val maxRedirects = 8
            var responseCode = 0

            while (redirects < maxRedirects) {
                val urlObj = URL(currentUrl)
                val c = urlObj.openConnection() as HttpURLConnection
                c.connectTimeout = TIMEOUT_MS
                c.readTimeout = TIMEOUT_MS
                c.instanceFollowRedirects = false
                c.setRequestProperty("User-Agent", "ExUpscaler/1.0")
                c.setRequestProperty("Accept", "*/*")
                if (startAt > 0) c.setRequestProperty("Range", "bytes=$startAt-")

                responseCode = try {
                    c.responseCode
                } catch (e: Throwable) {
                    onLog("❌ Gagal connect: ${e.javaClass.simpleName}")
                    onLog("   ${e.message?.take(100)}")
                    return@withContext false
                }

                onLog("HTTP $responseCode ← ${urlObj.host}")

                if (responseCode in 300..399) {
                    val newUrl = c.getHeaderField("Location")
                    c.disconnect()
                    if (newUrl.isNullOrBlank()) {
                        onLog("❌ Redirect tanpa Location")
                        return@withContext false
                    }
                    currentUrl = newUrl
                    redirects++
                } else {
                    conn = c
                    break
                }
            }

            if (conn == null) {
                onLog("❌ Terlalu banyak redirect")
                return@withContext false
            }

            if (responseCode !in listOf(200, 206)) {
                onLog("❌ HTTP $responseCode")
                when (responseCode) {
                    404 -> onLog("   File tidak ditemukan")
                    403 -> onLog("   Akses ditolak")
                    500, 502, 503 -> onLog("   Server error")
                }
                conn.disconnect()
                return@withContext false
            }

            val contentLength = conn.contentLengthLong
            val totalSize = when {
                responseCode == HttpURLConnection.HTTP_PARTIAL -> {
                    val cr = conn.getHeaderField("Content-Range") ?: ""
                    cr.substringAfter("/").toLongOrNull() ?: (startAt + contentLength)
                }
                else -> contentLength.takeIf { it > 0 } ?: expectedSize
            }

            onLog("Total size: ${totalSize / 1024 / 1024} MB")

            val appendMode = responseCode == HttpURLConnection.HTTP_PARTIAL && startAt > 0
            var downloaded = if (appendMode) startAt else 0L
            if (!appendMode && temp.exists()) temp.delete()

            conn.inputStream.use { input ->
                FileOutputStream(temp, appendMode).use { output ->
                    val buffer = ByteArray(BUFFER_SIZE)
                    var bytes: Int
                    var lastUpdate = 0L

                    while (input.read(buffer).also { bytes = it } != -1) {
                        output.write(buffer, 0, bytes)
                        downloaded += bytes

                        val now = System.currentTimeMillis()
                        if (now - lastUpdate > 200) {
                            val pct = if (totalSize > 0)
                                ((downloaded.toFloat() / totalSize) * 100).toInt().coerceIn(0, 100)
                            else 0
                            onProgress(downloaded, totalSize, pct)
                            lastUpdate = now
                        }
                    }
                }
            }

            if (target.exists()) target.delete()
            if (!temp.renameTo(target)) {
                temp.copyTo(target, overwrite = true)
                temp.delete()
            }

            val finalSize = target.length()
            onLog("✓ Download selesai: ${finalSize / 1024 / 1024} MB")
            onProgress(finalSize, finalSize, 100)
            return@withContext true

        } catch (e: Throwable) {
            onLog("❌ Exception: ${e.javaClass.simpleName}")
            onLog("   ${e.message?.take(100)}")
            Telemetry.error(TAG, "Download gagal: ${e.message}")
            try { conn?.disconnect() } catch (_: Throwable) {}
            return@withContext false
        }
    }
}

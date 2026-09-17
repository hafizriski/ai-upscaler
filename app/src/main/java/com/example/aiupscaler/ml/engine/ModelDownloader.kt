package com.example.aiupscaler.ml.engine

import android.content.Context
import com.example.aiupscaler.core.telemetry.Telemetry
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.net.HttpURLConnection
import java.net.URL

object ModelDownloader {

    private const val TAG = "ModelDownloader"
    private const val BUFFER_SIZE = 8192

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
        onProgress: (downloaded: Long, total: Long, percent: Int) -> Unit = { _, _, _ -> }
    ): Boolean = withContext(Dispatchers.IO) {

        val target = getLocalPath(context, fileName)
        val temp = File(target.parentFile, "$fileName.part")

        if (isDownloaded(context, fileName, expectedSize)) {
            Telemetry.info(TAG, "Model sudah ada: $fileName")
            return@withContext true
        }

        try {
            val startAt = if (temp.exists()) temp.length() else 0L

            var urlObj = URL(url)
            var conn: HttpURLConnection
            var redirects = 0

            // Follow redirect (GitHub releases → S3)
            while (true) {
                conn = (urlObj.openConnection() as HttpURLConnection).apply {
                    connectTimeout = 30000
                    readTimeout = 30000
                    instanceFollowRedirects = false
                    if (startAt > 0) setRequestProperty("Range", "bytes=$startAt-")
                }

                val code = conn.responseCode
                if (code in 300..399) {
                    val newUrl = conn.getHeaderField("Location")
                    conn.disconnect()
                    if (newUrl == null || redirects >= 5) {
                        Telemetry.error(TAG, "Terlalu banyak redirect")
                        return@withContext false
                    }
                    urlObj = URL(newUrl)
                    redirects++
                } else break
            }

            val responseCode = conn.responseCode
            val contentLength = conn.contentLengthLong
            val totalSize = when {
                responseCode == HttpURLConnection.HTTP_PARTIAL -> {
                    val contentRange = conn.getHeaderField("Content-Range") ?: ""
                    contentRange.substringAfter("/").toLongOrNull()
                        ?: (startAt + contentLength)
                }
                else -> contentLength.takeIf { it > 0 } ?: expectedSize
            }

            if (responseCode !in listOf(200, 206)) {
                Telemetry.error(TAG, "HTTP $responseCode")
                return@withContext false
            }

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

            Telemetry.info(TAG, "Download OK: $fileName (${target.length() / 1024 / 1024} MB)")
            onProgress(target.length(), target.length(), 100)
            true
        } catch (e: Throwable) {
            Telemetry.error(TAG, "Download gagal: ${e.message}")
            false
        }
    }
}

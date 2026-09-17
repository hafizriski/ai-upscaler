package com.arthexdev.exups.data.repository

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import java.io.File
import java.io.FileOutputStream

class GalleryRepository(context: Context) {

    private val galleryDir = File(context.filesDir, "gallery").apply {
        if (!exists()) mkdirs()
    }

    data class GalleryItem(
        val file: File,
        val timestamp: Long
    ) {
        val name: String get() = file.name
    }

    fun saveResult(bitmap: Bitmap): File? {
        return try {
            val ts = System.currentTimeMillis()
            val file = File(galleryDir, "upscaled_$ts.png")
            FileOutputStream(file).use { out ->
                bitmap.compress(Bitmap.CompressFormat.PNG, 100, out)
            }
            file
        } catch (_: Throwable) { null }
    }

    fun listItems(): List<GalleryItem> {
        return galleryDir.listFiles()
            ?.filter { it.isFile && (it.extension == "png" || it.extension == "jpg") }
            ?.map { GalleryItem(it, it.lastModified()) }
            ?.sortedByDescending { it.timestamp }
            ?: emptyList()
    }

    fun loadBitmap(item: GalleryItem): Bitmap? = try {
        BitmapFactory.decodeFile(item.file.absolutePath)
    } catch (_: Throwable) { null }

    fun delete(item: GalleryItem): Boolean = item.file.delete()

    fun count(): Int = galleryDir.listFiles()?.count { it.isFile } ?: 0
}

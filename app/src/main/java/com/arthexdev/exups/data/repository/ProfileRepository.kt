package com.arthexdev.exups.data.repository

import android.content.Context
import android.content.SharedPreferences
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import java.io.File
import java.io.FileOutputStream

class ProfileRepository(context: Context) {
    private val prefs: SharedPreferences =
        context.getSharedPreferences("exups_profile", Context.MODE_PRIVATE)
    private val appContext = context.applicationContext

    companion object {
        private const val KEY_USERNAME = "username"
        private const val KEY_PHOTO_PATH = "photo_path"
        private const val DEFAULT_USERNAME = "User"
    }

    fun getUsername(): String =
        prefs.getString(KEY_USERNAME, DEFAULT_USERNAME) ?: DEFAULT_USERNAME

    fun setUsername(name: String) {
        prefs.edit().putString(KEY_USERNAME, name.trim().ifEmpty { DEFAULT_USERNAME }).apply()
    }

    private fun getPhotoFile(): File = File(appContext.filesDir, "profile_photo.jpg")

    fun getPhoto(): Bitmap? {
        val file = getPhotoFile()
        if (!file.exists()) return null
        return try { BitmapFactory.decodeFile(file.absolutePath) } catch (_: Throwable) { null }
    }

    fun savePhotoFromUri(uri: Uri): Boolean {
        return try {
            val input = appContext.contentResolver.openInputStream(uri) ?: return false
            val bmp = BitmapFactory.decodeStream(input)
            input.close()
            if (bmp == null) return false

            val size = 512
            val scaled = if (bmp.width != bmp.height) {
                val minSide = minOf(bmp.width, bmp.height)
                val x = (bmp.width - minSide) / 2
                val y = (bmp.height - minSide) / 2
                Bitmap.createBitmap(bmp, x, y, minSide, minSide)
            } else bmp

            val final = Bitmap.createScaledBitmap(scaled, size, size, true)

            FileOutputStream(getPhotoFile()).use { out ->
                final.compress(Bitmap.CompressFormat.JPEG, 90, out)
            }

            prefs.edit().putString(KEY_PHOTO_PATH, getPhotoFile().absolutePath).apply()
            true
        } catch (_: Throwable) { false }
    }

    fun hasPhoto(): Boolean = getPhotoFile().exists()
}

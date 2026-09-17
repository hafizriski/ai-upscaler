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

    companion object { private const val KEY_USERNAME = "username" }

    fun getUsername(): String = prefs.getString(KEY_USERNAME, "User") ?: "User"

    fun setUsername(name: String) {
        prefs.edit().putString(KEY_USERNAME, name.trim().ifEmpty { "User" }).apply()
    }

    private fun getPhotoFile(): File = File(appContext.filesDir, "profile_photo.jpg")

    fun getPhoto(): Bitmap? {
        val f = getPhotoFile()
        if (!f.exists()) return null
        return try { BitmapFactory.decodeFile(f.absolutePath) } catch (_: Throwable) { null }
    }

    fun savePhotoFromUri(uri: Uri): Boolean {
        return try {
            val input = appContext.contentResolver.openInputStream(uri) ?: return false
            val bmp = BitmapFactory.decodeStream(input)
            input.close()
            if (bmp == null) return false
            val minSide = minOf(bmp.width, bmp.height)
            val x = (bmp.width - minSide) / 2
            val y = (bmp.height - minSide) / 2
            val square = Bitmap.createBitmap(bmp, x, y, minSide, minSide)
            val final = Bitmap.createScaledBitmap(square, 512, 512, true)
            FileOutputStream(getPhotoFile()).use { out ->
                final.compress(Bitmap.CompressFormat.JPEG, 90, out)
            }
            true
        } catch (_: Throwable) { false }
    }
}

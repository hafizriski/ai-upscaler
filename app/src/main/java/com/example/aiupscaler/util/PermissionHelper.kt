package com.example.aiupscaler.util

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.content.ContextCompat

/**
 * Helper untuk menangani storage permission di berbagai versi Android.
 *
 * Android 9-12 (API 28-32): READ_EXTERNAL_STORAGE
 * Android 13 (API 33):      READ_MEDIA_IMAGES
 * Android 14+ (API 34+):    READ_MEDIA_IMAGES + READ_MEDIA_VISUAL_USER_SELECTED
 */
object PermissionHelper {

    /**
     * Return list permission yang dibutuhkan sesuai versi Android.
     */
    fun getRequiredPermissions(): Array<String> {
        return when {
            Build.VERSION.SDK_INT >= 34 -> arrayOf(
                Manifest.permission.READ_MEDIA_IMAGES,
                Manifest.permission.READ_MEDIA_VISUAL_USER_SELECTED
            )
            Build.VERSION.SDK_INT >= 33 -> arrayOf(
                Manifest.permission.READ_MEDIA_IMAGES
            )
            else -> arrayOf(
                Manifest.permission.READ_EXTERNAL_STORAGE
            )
        }
    }

    /**
     * Cek apakah semua permission sudah granted.
     */
    fun hasPermission(context: Context): Boolean {
        return getRequiredPermissions().all {
            ContextCompat.checkSelfPermission(context, it) == PackageManager.PERMISSION_GRANTED
        }
    }

    /**
     * Cek apakah butuh WRITE untuk menyimpan ke galeri di Android 9.
     */
    fun needsLegacyWritePermission(): Boolean =
        Build.VERSION.SDK_INT <= Build.VERSION_CODES.P
}

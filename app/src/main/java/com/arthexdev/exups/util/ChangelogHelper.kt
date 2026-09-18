package com.arthexdev.exups.util

import android.content.Context
import androidx.appcompat.app.AlertDialog

/**
 * Tampilkan dialog "What's New" 1× per versi.
 */
object ChangelogHelper {
    private const val PREFS = "exups_changelog"
    private const val KEY_LAST_VERSION = "last_version"

    private val CHANGES = mapOf(
        12 to listOf(
            "Before/After slider interaktif",
            "Batch processing multi gambar",
            "GPU/NNAPI auto-delegate",
            "Multi-bahasa (EN + ID)",
            "Light/Dark mode otomatis",
            "Cache hasil upscale",
            "Sort + search gallery"
        )
    )

    fun showIfNew(context: Context, versionCode: Int) {
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val lastSeen = prefs.getInt(KEY_LAST_VERSION, 0)
        if (lastSeen >= versionCode) return

        val changes = CHANGES[versionCode] ?: return

        AlertDialog.Builder(context)
            .setTitle("What's New")
            .setMessage(changes.joinToString("\n") { "• $it" })
            .setPositiveButton("OK") { _, _ ->
                prefs.edit().putInt(KEY_LAST_VERSION, versionCode).apply()
            }
            .setCancelable(false)
            .show()
    }
}

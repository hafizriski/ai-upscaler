package com.arthexdev.exups.util

import android.content.Context
import android.content.Intent
import android.net.Uri

/**
 * Minta user rate app setelah N kali upscale sukses.
 * Hanya muncul 1× seumur hidup install.
 */
object RateHelper {
    private const val PREFS = "exups_rate"
    private const val KEY_COUNT = "upscale_count"
    private const val KEY_SHOWN = "rate_shown"
    private const val THRESHOLD = 3

    fun onUpscaleSuccess(context: Context): Boolean {
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        if (prefs.getBoolean(KEY_SHOWN, false)) return false
        val count = prefs.getInt(KEY_COUNT, 0) + 1
        prefs.edit().putInt(KEY_COUNT, count).apply()
        return count >= THRESHOLD
    }

    fun markShown(context: Context) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit().putBoolean(KEY_SHOWN, true).apply()
    }

    fun openPlayStore(context: Context) {
        try {
            val intent = Intent(Intent.ACTION_VIEW,
                Uri.parse("market://details?id=${context.packageName}"))
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            context.startActivity(intent)
        } catch (_: Throwable) {
            try {
                context.startActivity(Intent(Intent.ACTION_VIEW,
                    Uri.parse("https://play.google.com/store/apps/details?id=${context.packageName}"))
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
            } catch (_: Throwable) {}
        }
    }
}

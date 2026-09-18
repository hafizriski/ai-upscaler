package com.arthexdev.exups.util

import android.content.Context
import android.content.SharedPreferences
import androidx.appcompat.app.AppCompatDelegate

/**
 * Simpan preferensi tema (system / dark / light).
 * Auto-apply saat app dibuka.
 */
object ThemePreferences {
    private const val PREFS = "exups_theme"
    private const val KEY_MODE = "theme_mode"

    const val MODE_SYSTEM = 0
    const val MODE_DARK = 1
    const val MODE_LIGHT = 2

    private fun prefs(context: Context): SharedPreferences =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    fun getMode(context: Context): Int =
        prefs(context).getInt(KEY_MODE, MODE_SYSTEM)

    fun setMode(context: Context, mode: Int) {
        prefs(context).edit().putInt(KEY_MODE, mode).apply()
        apply(mode)
    }

    fun apply(mode: Int) {
        val night = when (mode) {
            MODE_DARK -> AppCompatDelegate.MODE_NIGHT_YES
            MODE_LIGHT -> AppCompatDelegate.MODE_NIGHT_NO
            else -> AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM
        }
        AppCompatDelegate.setDefaultNightMode(night)
    }

    fun applyStored(context: Context) = apply(getMode(context))
}

package com.arthexdev.exups.ui.settings

import android.content.Context
import android.content.SharedPreferences

/**
 * Settings user — disimpan di SharedPreferences.
 */
object SettingsPreferences {
    private const val PREFS = "exups_settings"

    // Keys
    private const val KEY_SPECIFY_TILESIZE = "specify_tilesize"
    private const val KEY_TILESIZE = "tilesize"
    private const val KEY_LIMIT_GPU = "limit_gpu"
    private const val KEY_KEEP_SCREEN_ON = "keep_screen_on"
    private const val KEY_PICK_FROM_GALLERY = "pick_from_gallery"
    private const val KEY_SAVE_LOCATION = "save_location"
    private const val KEY_OUTPUT_FORMAT = "output_format"
    private const val KEY_SCALE = "scale"

    // Defaults
    const val DEFAULT_TILESIZE = 128
    const val MIN_TILESIZE = 64
    const val MAX_TILESIZE = 256
    const val FORMAT_AUTO = "auto"
    const val FORMAT_PNG = "png"
    const val FORMAT_JPG = "jpg"
    const val FORMAT_WEBP = "webp"

    private fun prefs(c: Context): SharedPreferences =
        c.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    // Getters
    fun isSpecifyTilesize(c: Context) = prefs(c).getBoolean(KEY_SPECIFY_TILESIZE, false)
    fun getTilesize(c: Context) = prefs(c).getInt(KEY_TILESIZE, DEFAULT_TILESIZE)
    fun isLimitGpu(c: Context) = prefs(c).getBoolean(KEY_LIMIT_GPU, false)
    fun isKeepScreenOn(c: Context) = prefs(c).getBoolean(KEY_KEEP_SCREEN_ON, false)
    fun isPickFromGallery(c: Context) = prefs(c).getBoolean(KEY_PICK_FROM_GALLERY, false)
    fun getSaveLocation(c: Context) = prefs(c).getString(KEY_SAVE_LOCATION, "") ?: ""
    fun getOutputFormat(c: Context) = prefs(c).getString(KEY_OUTPUT_FORMAT, FORMAT_AUTO) ?: FORMAT_AUTO
    fun getScale(c: Context) = prefs(c).getInt(KEY_SCALE, 4)

    // Setters
    fun setSpecifyTilesize(c: Context, v: Boolean) = prefs(c).edit().putBoolean(KEY_SPECIFY_TILESIZE, v).apply()
    fun setTilesize(c: Context, v: Int) = prefs(c).edit().putInt(KEY_TILESIZE, v.coerceIn(MIN_TILESIZE, MAX_TILESIZE)).apply()
    fun setLimitGpu(c: Context, v: Boolean) = prefs(c).edit().putBoolean(KEY_LIMIT_GPU, v).apply()
    fun setKeepScreenOn(c: Context, v: Boolean) = prefs(c).edit().putBoolean(KEY_KEEP_SCREEN_ON, v).apply()
    fun setPickFromGallery(c: Context, v: Boolean) = prefs(c).edit().putBoolean(KEY_PICK_FROM_GALLERY, v).apply()
    fun setSaveLocation(c: Context, v: String) = prefs(c).edit().putString(KEY_SAVE_LOCATION, v).apply()
    fun setOutputFormat(c: Context, v: String) = prefs(c).edit().putString(KEY_OUTPUT_FORMAT, v).apply()
    fun setScale(c: Context, v: Int) = prefs(c).edit().putInt(KEY_SCALE, v.coerceIn(2, 4)).apply()

    /**
     * Resolve format: kalau AUTO, ambil dari ekstensi file sumber.
     */
    fun resolveFormat(c: Context, sourceExtension: String?): String {
        val pref = getOutputFormat(c)
        if (pref != FORMAT_AUTO) return pref
        val ext = sourceExtension?.lowercase() ?: "png"
        return when (ext) {
            "jpg", "jpeg" -> FORMAT_JPG
            "webp" -> FORMAT_WEBP
            else -> FORMAT_PNG
        }
    }
}

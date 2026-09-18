package com.arthexdev.exups.util

import android.content.Context
import android.view.Gravity
import android.widget.TextView
import android.widget.Toast

/**
 * Toast custom dengan style iOS — rounded, center bottom, elevation.
 */
object ToastHelper {
    fun show(context: Context, msg: String) {
        try {
            val tv = TextView(context).apply {
                text = msg
                setPadding(48, 32, 48, 32)
                setBackgroundColor(0xCC000000.toInt())
                setTextColor(0xFFFFFFFF.toInt())
                textSize = 14f
            }
            Toast(context).apply {
                view = tv
                duration = Toast.LENGTH_SHORT
                setGravity(Gravity.BOTTOM or Gravity.CENTER_HORIZONTAL, 0, 200)
                show()
            }
        } catch (_: Throwable) {
            Toast.makeText(context, msg, Toast.LENGTH_SHORT).show()
        }
    }
}

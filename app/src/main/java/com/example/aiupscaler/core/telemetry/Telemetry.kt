package com.example.aiupscaler.core.telemetry

import android.util.Log

object Telemetry {
    private const val TAG = "AIUpscaler"
    fun debug(tag: String, msg: String) = Log.d(TAG, "[$tag] $msg")
    fun info(tag: String, msg: String) = Log.i(TAG, "[$tag] $msg")
    fun warn(tag: String, msg: String) = Log.w(TAG, "[$tag] $msg")
    fun error(tag: String, msg: String) = Log.e(TAG, "[$tag] $msg")
}

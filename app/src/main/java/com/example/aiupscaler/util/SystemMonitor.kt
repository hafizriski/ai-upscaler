package com.example.aiupscaler.util

import android.app.ActivityManager
import android.content.Context
import java.io.File

data class SystemStats(
    val ramUsedMb: Long, val ramTotalMb: Long, val ramPercent: Float,
    val cpuCores: Int, val cpuFreqMhz: Int, val gpuLabel: String
)

object SystemMonitor {
    fun snapshot(context: Context, gpuLabel: String): SystemStats {
        val am = context.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
        val mi = ActivityManager.MemoryInfo()
        am.getMemoryInfo(mi)
        val total = mi.totalMem / (1024 * 1024)
        val avail = mi.availMem / (1024 * 1024)
        val used = total - avail
        val pct = if (total > 0) used.toFloat() / total * 100f else 0f
        return SystemStats(used, total, pct,
            Runtime.getRuntime().availableProcessors(), readFreq(), gpuLabel)
    }
    private fun readFreq(): Int = try {
        val f = File("/sys/devices/system/cpu/cpu0/cpufreq/scaling_cur_freq")
        if (f.exists()) (f.readText().trim().toLong() / 1000).toInt() else 0
    } catch (_: Throwable) { 0 }
}

package com.example.aiupscaler.util

import android.app.ActivityManager
import android.content.Context

data class SystemStats(
    val ramUsedMb: Long,
    val ramTotalMb: Long,
    val ramPercent: Float,
    val cpuCores: Int,
    val cpuFreqMhz: Int,
    val thermal: String,
    val gpuLabel: String
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

        return SystemStats(
            ramUsedMb = used,
            ramTotalMb = total,
            ramPercent = pct,
            cpuCores = Runtime.getRuntime().availableProcessors(),
            cpuFreqMhz = readFreq(),
            thermal = readThermal(),
            gpuLabel = gpuLabel
        )
    }

    private fun readFreq(): Int = try {
        val f = java.io.File("/sys/devices/system/cpu/cpu0/cpufreq/scaling_cur_freq")
        if (f.exists()) (f.readText().trim().toLong() / 1000).toInt() else 0
    } catch (_: Throwable) { 0 }

    private fun readThermal(): String = try {
        val paths = listOf(
            "/sys/class/thermal/thermal_zone0/temp",
            "/sys/class/thermal/thermal_zone1/temp"
        )
        for (p in paths) {
            val f = java.io.File(p)
            if (f.exists()) {
                val raw = f.readText().trim().toLong()
                val c = if (raw > 1000) raw / 1000.0 else raw.toDouble()
                return String.format("%.1f°C", c)
            }
        }
        "—"
    } catch (_: Throwable) { "—" }
}

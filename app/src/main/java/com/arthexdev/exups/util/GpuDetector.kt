package com.arthexdev.exups.util

import java.io.File

object GpuDetector {
    data class GpuInfo(
        val renderer: String, val isAdreno: Boolean, val adrenoSeries: String,
        val supportsVulkan: Boolean, val vulkanApiLevel: Int, val supportsFp16: Boolean
    )
    fun detect(): GpuInfo {
        val renderer = try {
            val f = File("/sys/class/kgsl/kgsl-3d0/gpu_model")
            if (f.exists()) f.readText().trim() else "Unknown GPU"
        } catch (_: Throwable) { "Unknown GPU" }
        val isAdreno = renderer.contains("Adreno", ignoreCase = true)
        val series = when {
            isAdreno && renderer.contains("8") -> "8xx"
            isAdreno && renderer.contains("7") -> "7xx"
            isAdreno && renderer.contains("6") -> "6xx"
            isAdreno && renderer.contains("5") -> "5xx"
            else -> "unknown"
        }
        val hasVulkan = File("/system/lib64/libvulkan.so").exists()
        val apiLevel = when (series) {
            "8xx", "7xx" -> 3; "6xx" -> 2; "5xx" -> 1
            else -> if (hasVulkan) 1 else 0
        }
        val fp16 = series in listOf("6xx", "7xx", "8xx")
        return GpuInfo(renderer, isAdreno, series, hasVulkan, apiLevel, fp16)
    }
}

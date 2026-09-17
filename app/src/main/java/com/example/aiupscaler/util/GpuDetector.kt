package com.example.aiupscaler.util

import java.io.File

object GpuDetector {

    data class GpuInfo(
        val renderer: String,
        val isAdreno: Boolean,
        val adrenoSeries: String,
        val supportsVulkan: Boolean,
        val vulkanApiLevel: Int,
        val supportsFp16: Boolean,
        val recommendedBackend: String
    )

    fun detect(): GpuInfo {
        val renderer = readGpuFromSystem()
        val isAdreno = renderer.contains("Adreno", ignoreCase = true)
        val adrenoSeries = when {
            isAdreno && renderer.contains("8") -> "8xx"
            isAdreno && renderer.contains("7") -> "7xx"
            isAdreno && renderer.contains("6") -> "6xx"
            isAdreno && renderer.contains("5") -> "5xx"
            else -> "unknown"
        }
        val hasVulkan = File("/system/lib64/libvulkan.so").exists() ||
                File("/vendor/lib64/hw/vulkan.adreno.so").exists()
        val apiLevel = when (adrenoSeries) {
            "8xx", "7xx" -> 3
            "6xx" -> 2
            "5xx" -> 1
            else -> if (hasVulkan) 1 else 0
        }
        val fp16 = adrenoSeries in listOf("6xx", "7xx", "8xx")
        val recommended = if (hasVulkan && fp16) "Vulkan" else "CPU"
        return GpuInfo(renderer, isAdreno, adrenoSeries, hasVulkan, apiLevel, fp16, recommended)
    }

    fun recommendedCpuThreads(): Int {
        val cores = Runtime.getRuntime().availableProcessors()
        return when {
            cores >= 8 -> 4
            cores >= 6 -> 4
            cores >= 4 -> 3
            else -> 2
        }
    }

    private fun readGpuFromSystem(): String {
        // Coba baca dari /proc atau fallback ke Build.MODEL
        try {
            val f = File("/sys/class/kgsl/kgsl-3d0/gpu_model")
            if (f.exists()) return f.readText().trim()
        } catch (_: Throwable) {}
        try {
            val f = File("/sys/class/kgsl/kgsl-3d0/devfreq/cur_freq")
            if (f.exists()) return "Adreno"
        } catch (_: Throwable) {}
        return "Unknown GPU"
    }
}

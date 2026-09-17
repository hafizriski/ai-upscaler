package com.example.aiupscaler.util

import android.opengl.GLES20
import java.io.File

object GpuDetector {

    data class GpuInfo(
        val renderer: String,
        val vendor: String,
        val isAdreno: Boolean,
        val adrenoSeries: String,
        val supportsVulkan: Boolean,
        val vulkanApiLevel: Int,
        val supportsFp16: Boolean,
        val recommendedBackend: String
    )

    fun detect(): GpuInfo {
        val renderer = readGlString(GLES20.GL_RENDERER)
        val vendor = readGlString(GLES20.GL_VENDOR)
        val isAdreno = renderer.contains("Adreno", ignoreCase = true)

        val adrenoSeries = when {
            isAdreno && renderer.contains("Adreno (TM) 8") -> "8xx"
            isAdreno && renderer.contains("Adreno (TM) 7") -> "7xx"
            isAdreno && renderer.contains("Adreno (TM) 6") -> "6xx"
            isAdreno && renderer.contains("Adreno (TM) 5") -> "5xx"
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

        return GpuInfo(renderer, vendor, isAdreno, adrenoSeries,
            hasVulkan, apiLevel, fp16, recommended)
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

    private fun readGlString(name: Int): String = try {
        GLES20.glGetString(name) ?: "Unknown"
    } catch (_: Throwable) { "Unknown" }
}

package com.example.aiupscaler.util

import android.app.ActivityManager
import android.content.Context
import android.opengl.GLES20
import android.util.Log
import java.io.File

/**
 * Deteksi GPU + Vulkan support + Adreno variant.
 */
object GpuDetector {

    data class GpuInfo(
        val renderer: String,
        val vendor: String,
        val isAdreno: Boolean,
        val adrenoSeries: String,       // "6xx", "7xx", "5xx", "unknown"
        val supportsVulkan: Boolean,
        val vulkanApiLevel: Int,        // 0 kalau tidak support
        val supportsFp16: Boolean,      // FP16 acceleration di Vulkan
        val recommendedBackend: String  // "Vulkan", "OpenCL", "CPU"
    )

    fun detect(context: Context): GpuInfo {
        val renderer = readGlString(GLES20.GL_RENDERER)
        val vendor = readGlString(GLES20.GL_VENDOR)
        val isAdreno = renderer.contains("Adreno", ignoreCase = true)

        val adrenoSeries = when {
            isAdreno && renderer.contains("Adreno (TM) 7") -> "7xx"
            isAdreno && renderer.contains("Adreno (TM) 6") -> "6xx"
            isAdreno && renderer.contains("Adreno (TM) 5") -> "5xx"
            isAdreno && renderer.contains("Adreno (TM) 8") -> "8xx"
            else -> "unknown"
        }

        // Cek Vulkan dari file sistem
        val vulkanFile = File("/system/lib64/libvulkan.so")
        val hasVulkan = vulkanFile.exists() ||
                File("/vendor/lib64/hw/vulkan.adreno.so").exists() ||
                File("/vendor/lib64/hw/vulkan.msm.so").exists()

        // Cek Vulkan API level dari build property
        val apiLevel = try {
            val cl = Class.forName("android.os.SystemProperties")
            val get = cl.getMethod("get", String::class.java)
            get.invoke(null, "ro.hardware.vulkan") as String?
            // Fallback: cek GPU
            when {
                adrenoSeries == "7xx" || adrenoSeries == "8xx" -> 3
                adrenoSeries == "6xx" -> 2
                adrenoSeries == "5xx" -> 1
                else -> 0
            }
        } catch (_: Throwable) { 0 }

        val supportsFp16 = adrenoSeries in listOf("6xx", "7xx", "8xx")

        val recommended = when {
            hasVulkan && supportsFp16 -> "Vulkan"
            adrenoSeries == "6xx" -> "OpenCL"
            else -> "CPU"
        }

        return GpuInfo(
            renderer = renderer,
            vendor = vendor,
            isAdreno = isAdreno,
            adrenoSeries = adrenoSeries,
            supportsVulkan = hasVulkan,
            vulkanApiLevel = apiLevel,
            supportsFp16 = supportsFp16,
            recommendedBackend = recommended
        )
    }

    private fun readGlString(name: Int): String {
        return try {
            GLES20.glGetString(name) ?: "Unknown"
        } catch (_: Throwable) { "Unknown" }
    }

    /**
     * Rekomendasi jumlah thread CPU berdasarkan CPU affinity + jumlah core.
     */
    fun recommendedCpuThreads(): Int {
        val cores = Runtime.getRuntime().availableProcessors()
        // Untuk HP, umumnya 4 thread optimal (hindari throttling)
        return when {
            cores >= 8 -> 4
            cores >= 6 -> 4
            cores >= 4 -> 3
            else -> 2
        }
    }

    /**
     * Cek jumlah core per cluster (big.LITTLE).
     */
    fun cpuClusters(): List<Int> {
        val base = File("/sys/devices/system/cpu")
        val clusters = mutableListOf<Int>()
        try {
            val cpus = base.listFiles { f -> f.name.matches(Regex("cpu\\d+")) } ?: return emptyList()
            val freqs = mutableMapOf<Long, Int>()
            for (cpu in cpus) {
                val max = File(cpu, "cpufreq/cpuinfo_max_freq")
                if (max.exists()) {
                    val f = max.readText().trim().toLongOrNull() ?: continue
                    freqs[f] = (freqs[f] ?: 0) + 1
                }
            }
            clusters.addAll(freqs.values)
        } catch (_: Throwable) {}
        return clusters
    }
}

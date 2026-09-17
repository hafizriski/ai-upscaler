package com.arthexdev.exups.ml.optimization

import android.app.ActivityManager
import android.content.Context
import android.opengl.GLES20
import android.os.Build
import java.io.File

object PerformanceConfig {

    data class Config(
        val optimalThreads: Int,
        val maxThreads: Int,
        val tileOverlap: Int,
        val batchEmitMs: Long,
        val useXNNPACK: Boolean,
        val allowFp16: Boolean,
        val ramTierMb: Int,
        val deviceTier: Tier,
        val gpu: GpuCapabilities
    )

    enum class Tier { HIGH, MID, LOW }

    data class GpuCapabilities(
        val renderer: String,
        val vendor: String,
        val isAdreno: Boolean,
        val isMali: Boolean,
        val adrenoSeries: String,
        val vulkanApiLevel: Int,
        val supportsVulkan: Boolean,
        val supportsOpenGlEs31: Boolean,
        val supportsFp16: Boolean,
        val hasNnApi: Boolean,
        val nnApiVersion: Int
    )

    @Volatile private var cached: Config? = null

    fun get(context: Context): Config {
        cached?.let { return it }
        synchronized(this) {
            cached?.let { return it }
            val cfg = detect(context)
            cached = cfg
            return cfg
        }
    }

    private fun detect(context: Context): Config {
        val cores = Runtime.getRuntime().availableProcessors().coerceAtLeast(2)

        val am = context.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
        val mi = ActivityManager.MemoryInfo()
        am.getMemoryInfo(mi)
        val ramMb = (mi.totalMem / (1024L * 1024L)).toInt()

        val gpu = detectGpu()

        val tier = when {
            cores >= 8 && ramMb >= 6000 && gpu.supportsVulkan -> Tier.HIGH
            cores >= 6 && ramMb >= 3500 -> Tier.MID
            else -> Tier.LOW
        }

        val optimal = (cores - 1).coerceIn(2, 8)

        val overlap = when (tier) {
            Tier.HIGH -> 8
            Tier.MID -> 6
            Tier.LOW -> 4
        }

        val batchEmit = when (tier) {
            Tier.HIGH -> 120L
            Tier.MID -> 180L
            Tier.LOW -> 250L
        }

        return Config(
            optimalThreads = optimal,
            maxThreads = cores,
            tileOverlap = overlap,
            batchEmitMs = batchEmit,
            useXNNPACK = true,
            allowFp16 = gpu.supportsFp16,
            ramTierMb = ramMb,
            deviceTier = tier,
            gpu = gpu
        )
    }

    private fun detectGpu(): GpuCapabilities {
        // Renderer info via /sys (Adreno)
        val renderer = try {
            val f = File("/sys/class/kgsl/kgsl-3d0/gpu_model")
            if (f.exists()) f.readText().trim() else "Unknown"
        } catch (_: Throwable) { "Unknown" }

        val isAdreno = renderer.contains("Adreno", ignoreCase = true)
        val isMali = renderer.contains("Mali", ignoreCase = true)

        val adrenoSeries = when {
            isAdreno && renderer.contains("8") -> "8xx"
            isAdreno && renderer.contains("7") -> "7xx"
            isAdreno && renderer.contains("6") -> "6xx"
            isAdreno && renderer.contains("5") -> "5xx"
            else -> "unknown"
        }

        val hasVulkan = File("/system/lib64/libvulkan.so").exists() ||
                        File("/vendor/lib64/hw/vulkan.*.so").exists()

        val apiLevel = when (adrenoSeries) {
            "8xx", "7xx" -> 3
            "6xx" -> 2
            "5xx" -> 1
            else -> if (hasVulkan) 1 else 0
        }

        // OpenGL ES 3.1+ — dianggap true kalau Vulkan ada atau Adreno 6xx+
        val glEs31 = apiLevel >= 1 || hasVulkan

        // FP16: Adreno 6xx+ dan Mali-G series
        val fp16 = adrenoSeries in listOf("6xx", "7xx", "8xx") || isMali

        // NNAPI — Android 8.1+ (API 27+)
        val sdk = Build.VERSION.SDK_INT
        val hasNnApi = sdk >= Build.VERSION_CODES.O_MR1
        val nnApiVer = when {
            sdk >= 30 -> 1_3_0
            sdk >= 29 -> 1_2_0
            sdk >= 28 -> 1_1_0
            sdk >= 27 -> 1_0_0
            else -> 0
        }

        return GpuCapabilities(
            renderer = renderer,
            vendor = if (isAdreno) "Qualcomm" else if (isMali) "ARM" else "Unknown",
            isAdreno = isAdreno,
            isMali = isMali,
            adrenoSeries = adrenoSeries,
            vulkanApiLevel = apiLevel,
            supportsVulkan = hasVulkan,
            supportsOpenGlEs31 = glEs31,
            supportsFp16 = fp16,
            hasNnApi = hasNnApi,
            nnApiVersion = nnApiVer
        )
    }

    fun describe(context: Context): String {
        val c = get(context)
        return "tier=${c.deviceTier} cores=${c.maxThreads} threads=${c.optimalThreads} " +
               "ram=${c.ramTierMb}MB gpu=${c.gpu.renderer} " +
               "vulkan=${c.gpu.supportsVulkan} fp16=${c.gpu.supportsFp16} " +
               "nnapi=${c.gpu.hasNnApi}"
    }
}

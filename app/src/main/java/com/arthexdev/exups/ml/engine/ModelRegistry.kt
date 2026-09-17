package com.arthexdev.exups.ml.engine

import android.content.Context

data class ModelSpec(
    val id: String,
    val displayName: String,
    val fileName: String,
    val remoteUrl: String,
    val description: String,
    val scale: Int,
    val approxSizeMb: Int,
    val recommendedFor: String,
    val speedLabel: String,
    val isBundled: Boolean
)

object ModelRegistry {
    private const val RELEASE_BASE =
        "https://github.com/hafizriski/ai-upscaler/releases/download/v1-models"

    val ALL = listOf(
        ModelSpec(
            id = "x4plus",
            displayName = "Real-ESRGAN x4plus",
            fileName = "realesrgan_x4plus.tflite",
            remoteUrl = "$RELEASE_BASE/realesrgan_x4plus.tflite",
            description = "Kualitas tertinggi untuk foto natural",
            scale = 4,
            approxSizeMb = 64,
            recommendedFor = "Foto, pemandangan, portrait",
            speedLabel = "Lambat",
            isBundled = false
        ),
        ModelSpec(
            id = "x4v3",
            displayName = "Real-ESRGAN x4v3",
            fileName = "realesr_general_x4v3.tflite",
            remoteUrl = "$RELEASE_BASE/realesr_general_x4v3.tflite",
            description = "Ringan & cepat, siap pakai tanpa download",
            scale = 4,
            approxSizeMb = 4,
            recommendedFor = "Penggunaan sehari-hari",
            speedLabel = "Sedang",
            isBundled = true
        )
    )

    fun getById(id: String): ModelSpec = ALL.firstOrNull { it.id == id } ?: ALL.last()
    fun getAllForDisplay(): List<ModelSpec> = ALL

    fun isReady(context: Context, spec: ModelSpec): Boolean {
        if (spec.isBundled) {
            try {
                context.assets.openFd("models/${spec.fileName}").use { return true }
            } catch (_: Throwable) {}
        }
        return ModelDownloader.isDownloaded(
            context, spec.fileName, spec.approxSizeMb * 1024L * 1024L
        )
    }

    fun hasAny(context: Context): Boolean = ALL.any { isReady(context, it) }
}

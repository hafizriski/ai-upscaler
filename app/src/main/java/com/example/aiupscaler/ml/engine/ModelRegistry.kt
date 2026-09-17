package com.example.aiupscaler.ml.engine

import android.content.Context

data class ModelSpec(
    val id: String,
    val displayName: String,
    val assetName: String,
    val description: String,
    val scale: Int,
    val approxSizeMb: Int,
    val recommendedFor: String,
    val speedLabel: String
)

object ModelRegistry {

    val ALL = listOf(
        ModelSpec(
            id = "x4plus",
            displayName = "Real-ESRGAN x4plus",
            assetName = "models/realesrgan_x4plus.tflite",
            description = "Kualitas tertinggi untuk foto natural, detail tajam",
            scale = 4,
            approxSizeMb = 64,
            recommendedFor = "Foto, pemandangan, portrait",
            speedLabel = "Lambat"
        ),
        ModelSpec(
            id = "x4v3",
            displayName = "Real-ESRGAN x4v3",
            assetName = "models/realesr_general_x4v3.tflite",
            description = "Seimbang antara kualitas dan kecepatan",
            scale = 4,
            approxSizeMb = 4,
            recommendedFor = "Penggunaan sehari-hari",
            speedLabel = "Sedang"
        )
    )

    fun getById(id: String): ModelSpec =
        ALL.firstOrNull { it.id == id } ?: ALL.last()

    fun getAvailable(context: Context): List<ModelSpec> =
        ALL.filter { spec ->
            try {
                context.assets.openFd(spec.assetName).use { true }
            } catch (_: Throwable) { false }
        }

    fun hasAny(context: Context): Boolean = getAvailable(context).isNotEmpty()
}

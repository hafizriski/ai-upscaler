package com.example.aiupscaler.ml

data class ProgressInfo(
    val phase: String,
    val currentTile: Int = 0,
    val totalTiles: Int = 0,
    val elapsedMs: Long = 0L,
    val etaMs: Long = -1L,
    val tileProgress: Float = 0f,
    val overallProgress: Float = 0f,
    val msPerTile: Long = 0L
)

interface ProgressListener {
    fun onProgress(info: ProgressInfo)
    fun onLog(message: String)
}

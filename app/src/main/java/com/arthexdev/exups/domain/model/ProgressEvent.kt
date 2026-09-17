package com.arthexdev.exups.domain.model

sealed class ProgressEvent {
    data class Log(val message: String) : ProgressEvent()
    data class Stage(val phase: String, val detail: String = "") : ProgressEvent()
    data class TileProgress(
        val current: Int, val total: Int,
        val msPerTile: Long, val elapsedMs: Long, val etaMs: Long
    ) : ProgressEvent()
    data class Warning(val message: String) : ProgressEvent()
    data class Error(val message: String) : ProgressEvent()
    data class Complete(val result: UpscaleResult) : ProgressEvent()
}

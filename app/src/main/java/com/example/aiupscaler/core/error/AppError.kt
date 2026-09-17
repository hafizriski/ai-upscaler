package com.example.aiupscaler.core.error

sealed class AppError(
    val userMessage: String,
    val techMessage: String,
    val cause: Throwable? = null
) {
    class ModelNotFound(msg: String) : AppError("Model AI tidak ditemukan", msg)
    class ModelLoadFailed(msg: String, cause: Throwable?) : AppError("Gagal memuat model AI", msg, cause)
    class InferenceFailed(msg: String, cause: Throwable?) : AppError("Gagal memproses gambar", msg, cause)
    class ImageLoadFailed(msg: String, cause: Throwable?) : AppError("Gagal memuat gambar", msg, cause)
    class SaveFailed(msg: String, cause: Throwable?) : AppError("Gagal menyimpan hasil", msg, cause)
    class OutOfMemory(msg: String, cause: Throwable?) : AppError("Memori tidak cukup", msg, cause)
    class Unknown(msg: String, cause: Throwable?) : AppError("Terjadi kesalahan", msg, cause)

    companion object {
        fun fromThrowable(t: Throwable): AppError = when (t) {
            is OutOfMemoryError -> OutOfMemory(t.message ?: "OOM", t)
            is IllegalStateException -> InferenceFailed(t.message ?: "State", t)
            else -> Unknown(t.message ?: "Unknown", t)
        }
    }
}

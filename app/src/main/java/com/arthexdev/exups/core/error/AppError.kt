package com.arthexdev.exups.core.error

sealed class AppError(
    val userMessage: String,
    val techMessage: String,
    val cause: Throwable? = null
) {
    class ModelLoadFailed(msg: String, cause: Throwable?) : AppError("Gagal memuat model AI", msg, cause)
    class InferenceFailed(msg: String, cause: Throwable?) : AppError("Gagal memproses gambar", msg, cause)
    class Unknown(msg: String, cause: Throwable?) : AppError("Terjadi kesalahan", msg, cause)

    companion object {
        fun fromThrowable(t: Throwable): AppError = when (t) {
            is OutOfMemoryError -> Unknown(t.message ?: "OOM", t)
            is IllegalStateException -> InferenceFailed(t.message ?: "State", t)
            else -> Unknown(t.message ?: "Unknown", t)
        }
    }
}

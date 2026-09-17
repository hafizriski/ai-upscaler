package com.arthexdev.exups.core.result

import com.arthexdev.exups.core.error.AppError

sealed class AppResult<out T> {
    data class Success<T>(val data: T) : AppResult<T>()
    data class Failure(val error: AppError) : AppResult<Nothing>()
}

inline fun <T> runCatchingResult(block: () -> T): AppResult<T> = try {
    AppResult.Success(block())
} catch (e: Throwable) {
    AppResult.Failure(AppError.fromThrowable(e))
}

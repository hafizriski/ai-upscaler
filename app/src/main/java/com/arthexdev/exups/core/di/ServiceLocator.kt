package com.arthexdev.exups.core.di

import android.content.Context
import com.arthexdev.exups.data.repository.UpscaleRepositoryImpl
import com.arthexdev.exups.domain.repository.UpscaleRepository
import com.arthexdev.exups.domain.usecase.UpscaleImageUseCase

object ServiceLocator {
    @Volatile private var appContext: Context? = null
    @Volatile private var repo: UpscaleRepository? = null
    @Volatile private var useCase: UpscaleImageUseCase? = null

    fun init(context: Context) { appContext = context.applicationContext }

    fun provideRepository(): UpscaleRepository {
        repo?.let { return it }
        synchronized(this) {
            repo?.let { return it }
            val ctx = appContext ?: throw IllegalStateException("ServiceLocator belum di-init")
            return UpscaleRepositoryImpl(ctx).also { repo = it }
        }
    }

    fun provideUpscaleUseCase(): UpscaleImageUseCase {
        useCase?.let { return it }
        synchronized(this) {
            useCase?.let { return it }
            return UpscaleImageUseCase(provideRepository()).also { useCase = it }
        }
    }
}

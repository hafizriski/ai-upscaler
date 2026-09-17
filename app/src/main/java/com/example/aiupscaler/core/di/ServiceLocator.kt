package com.example.aiupscaler.core.di

import android.content.Context
import com.example.aiupscaler.data.repository.UpscaleRepositoryImpl
import com.example.aiupscaler.domain.repository.UpscaleRepository
import com.example.aiupscaler.domain.usecase.UpscaleImageUseCase

/**
 * Service Locator ringan.
 * Ganti dengan Hilt/Dagger kalau project membesar.
 */
object ServiceLocator {

    private lateinit var appContext: Context
    private var repo: UpscaleRepository? = null
    private var useCase: UpscaleImageUseCase? = null

    fun init(context: Context) {
        appContext = context.applicationContext
    }

    fun provideRepository(): UpscaleRepository {
        return repo ?: UpscaleRepositoryImpl(appContext).also { repo = it }
    }

    fun provideUpscaleUseCase(): UpscaleImageUseCase {
        return useCase ?: UpscaleImageUseCase(provideRepository()).also { useCase = it }
    }
}

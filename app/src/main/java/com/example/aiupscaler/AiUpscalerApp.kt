package com.example.aiupscaler

import android.app.Application
import android.os.Build
import com.example.aiupscaler.core.di.ServiceLocator
import com.example.aiupscaler.core.telemetry.Telemetry

class AiUpscalerApp : Application() {

    override fun onCreate() {
        super.onCreate()
        ServiceLocator.init(this)
        Telemetry.info("App", "Started on Android ${Build.VERSION.SDK_INT} (${Build.VERSION.RELEASE})")
        Telemetry.info("App", "Device: ${Build.MANUFACTURER} ${Build.MODEL}")
    }
}

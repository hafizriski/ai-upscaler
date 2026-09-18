package com.arthexdev.exups.ui.main

import android.app.ActivityManager
import android.content.Context
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.view.LayoutInflater
import android.view.View
import android.view.Window
import android.widget.TextView
import androidx.appcompat.app.AlertDialog
import com.arthexdev.exups.R
import com.arthexdev.exups.ml.engine.ModelSpec
import com.arthexdev.exups.ui.settings.SettingsPreferences
import com.google.android.material.materialswitch.MaterialSwitch
import com.google.android.material.slider.Slider

object ProcessOptionsDialog {

    data class Result(
        val scale: Int,
        val denoise: Int,
        val useGpu: Boolean
    )

    fun show(
        context: Context,
        selectedModel: ModelSpec?,
        onProcess: (Result) -> Unit
    ) {
        val view = LayoutInflater.from(context)
            .inflate(R.layout.dialog_process_options, null)

        val tvModelType = view.findViewById<TextView>(R.id.tvDialogModelType)
        val tvModel = view.findViewById<TextView>(R.id.tvDialogModel)
        val sliderScale = view.findViewById<Slider>(R.id.sliderScale)
        val tvScale = view.findViewById<TextView>(R.id.tvDialogScale)
        val sliderDenoise = view.findViewById<Slider>(R.id.sliderDenoise)
        val tvDenoise = view.findViewById<TextView>(R.id.tvDialogDenoise)
        val swGpu = view.findViewById<MaterialSwitch>(R.id.swDialogGpu)
        val tvRam = view.findViewById<TextView>(R.id.tvDialogRam)
        val progressRam = view.findViewById<com.google.android.material.progressindicator.LinearProgressIndicator>(R.id.progressDialogRam)
        val btnProcess = view.findViewById<com.google.android.material.button.MaterialButton>(R.id.btnDialogProcess)

        // Set model info
        if (selectedModel != null) {
            tvModel.text = selectedModel.displayName
        } else {
            tvModel.text = "—"
        }

        // Scale slider
        val initialScale = SettingsPreferences.getScale(context)
        sliderScale.value = initialScale.toFloat()
        tvScale.text = "${initialScale}X"
        sliderScale.addOnChangeListener { _, value, _ ->
            tvScale.text = "${value.toInt()}X"
        }

        // Denoise slider
        tvDenoise.text = "0"
        sliderDenoise.addOnChangeListener { _, value, _ ->
            tvDenoise.text = value.toInt().toString()
        }

        // GPU toggle
        swGpu.isChecked = !SettingsPreferences.isLimitGpu(context)

        // RAM indicator
        updateRam(context, tvRam, progressRam)

        val dialog = AlertDialog.Builder(context)
            .setView(view)
            .create()

        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE)
        dialog.window?.setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))

        btnProcess.setOnClickListener {
            val scale = sliderScale.value.toInt().coerceIn(2, 4)
            val denoise = sliderDenoise.value.toInt().coerceIn(0, 100)
            val gpu = swGpu.isChecked

            SettingsPreferences.setScale(context, scale)

            dialog.dismiss()
            onProcess(Result(scale, denoise, gpu))
        }

        dialog.show()
    }

    private fun updateRam(context: Context, tvRam: TextView, progress: com.google.android.material.progressindicator.LinearProgressIndicator) {
        try {
            val am = context.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
            val mi = ActivityManager.MemoryInfo()
            am.getMemoryInfo(mi)
            val availMb = mi.availMem / (1024 * 1024)
            val totalMb = mi.totalMem / (1024 * 1024)
            val estMb = (totalMb * 0.1).toLong()
            tvRam.text = "$estMb MB / $availMb MB"
            val pct = ((estMb.toFloat() / availMb.coerceAtLeast(1)) * 100).toInt().coerceIn(0, 100)
            progress.setProgressCompat(pct, true)
        } catch (_: Throwable) {
            tvRam.text = "— MB / — MB"
        }
    }
}

package com.arthexdev.exups.ui.main

import android.animation.ObjectAnimator
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.HapticFeedbackConstants
import android.view.View
import android.view.animation.AccelerateDecelerateInterpolator
import android.view.animation.OvershootInterpolator
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.FileProvider
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.arthexdev.exups.R
import com.arthexdev.exups.databinding.ActivityMainBinding
import com.arthexdev.exups.ml.engine.Backend
import com.arthexdev.exups.ml.engine.ModelRegistry
import com.arthexdev.exups.ml.engine.ModelSpec
import com.arthexdev.exups.util.ImageSaver
import com.arthexdev.exups.util.PermissionHelper
import com.arthexdev.exups.util.SystemMonitor
import com.google.android.material.chip.Chip
import com.google.android.material.snackbar.Snackbar
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private val vm: MainViewModel by viewModels()
    private val ui = Handler(Looper.getMainLooper())
    private val chipModelMap = mutableMapOf<String, Chip>()

    private val pick = registerForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        uri?.let { loadUri(it) }
    }

    private val requestPermission = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { result ->
        if (result.values.all { it }) toast("Izin diberikan") else toast("Izin ditolak")
    }

    private val systemTicker = object : Runnable {
        override fun run() {
            refreshSystem()
            ui.postDelayed(this, 1500)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setupListeners()
        setupAnimations()
        observeState()
        ensurePermissions()
        ui.post(systemTicker)
    }

    private fun setupAnimations() {
        // FAB entrance animation
        binding.fabAdd.scaleX = 0f
        binding.fabAdd.scaleY = 0f
        binding.fabAdd.animate()
            .scaleX(1f).scaleY(1f)
            .setDuration(400)
            .setInterpolator(OvershootInterpolator(1.2f))
            .start()

        // Bottom nav slide up
        binding.bottomNav.translationY = 100f
        binding.bottomNav.alpha = 0f
        binding.bottomNav.animate()
            .translationY(0f).alpha(1f)
            .setDuration(400)
            .setInterpolator(AccelerateDecelerateInterpolator())
            .start()

        // Toolbar fade in
        binding.toolbar.alpha = 0f
        binding.toolbar.animate().alpha(1f).setDuration(300).start()
    }

    private fun ensurePermissions() {
        if (!PermissionHelper.hasStoragePermission(this)) {
            requestPermission.launch(PermissionHelper.getRequiredPermissions())
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        ui.removeCallbacks(systemTicker)
    }

    private fun setupListeners() {
        // FAB
        binding.fabAdd.setOnClickListener {
            it.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
            it.animate().scaleX(0.9f).scaleY(0.9f).setDuration(100).withEndAction {
                it.animate().scaleX(1f).scaleY(1f).setDuration(100).start()
            }.start()

            if (!PermissionHelper.hasStoragePermission(this)) {
                requestPermission.launch(PermissionHelper.getRequiredPermissions())
            } else {
                pick.launch("image/*")
            }
        }

        // Bottom nav (placeholder untuk sekarang)
        binding.navHome.setOnClickListener {
            it.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
            toast("Home")
        }
        binding.navGallery.setOnClickListener {
            it.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
            toast("Gallery — coming soon")
        }
        binding.navSettings.setOnClickListener {
            it.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
            toast("Settings — coming soon")
        }

        // Cancel
        binding.btnCancel.setOnClickListener {
            vm.cancel()
            toast("Dibatalkan")
        }

        // Thread slider
        val s = vm.state.value
        binding.sliderThreads.valueTo = s.maxThreads.toFloat().coerceAtLeast(1f)
        binding.sliderThreads.valueFrom = 1f
        binding.sliderThreads.value = s.threadCount.toFloat().coerceIn(1f, s.maxThreads.toFloat())
        binding.tvThreadCount.text = s.threadCount.toString()
        binding.sliderThreads.addOnChangeListener { _, value, fromUser ->
            if (fromUser) {
                val threads = value.toInt().coerceAtLeast(1)
                vm.setThreadCount(threads)
                binding.tvThreadCount.text = threads.toString()
            }
        }

        // Log toggle
        binding.logHeader.setOnClickListener {
            val visible = binding.tvLog.visibility == View.VISIBLE
            if (visible) {
                binding.tvLog.animate().alpha(0f).setDuration(150).withEndAction {
                    binding.tvLog.visibility = View.GONE
                }.start()
            } else {
                binding.tvLog.visibility = View.VISIBLE
                binding.tvLog.alpha = 0f
                binding.tvLog.animate().alpha(1f).setDuration(150).start()
            }
            binding.logChevron.text = if (visible) "▸" else "▾"
        }
    }

    private fun buildModelChips(models: List<ModelSpec>, selectedId: String) {
        binding.chipGroupModels.removeAllViews()
        chipModelMap.clear()
        models.forEach { spec ->
            val ready = ModelRegistry.isReady(this, spec)
            val chip = Chip(this).apply {
                text = "${spec.displayName} ${if (ready) "" else "⬇"}"
                isCheckable = true
                isChecked = spec.id == selectedId
                isChipIconVisible = false
                textSize = 12f
                setOnClickListener {
                    it.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
                    vm.setModel(spec.id)
                }
            }
            binding.chipGroupModels.addView(chip)
            chipModelMap[spec.id] = chip
        }
    }

    private fun updateModelDetail(spec: ModelSpec?) {
        if (spec == null) {
            binding.tvModelName.text = "—"
            binding.tvModelDesc.text = "—"
            binding.chipScale.text = "—"
            binding.chipSize.text = "— MB"
            binding.chipSpeed.text = "—"
            return
        }
        val ready = ModelRegistry.isReady(this, spec)
        binding.tvModelName.text = spec.displayName + if (ready) "" else " · perlu download"
        binding.tvModelDesc.text = spec.description
        binding.chipScale.text = "×${spec.scale}"
        binding.chipSize.text = "${spec.approxSizeMb} MB"
        binding.chipSpeed.text = spec.speedLabel
    }

    private fun observeState() {
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                vm.state.collect { s ->
                    // Preview
                    binding.imagePreview.setImageBitmap(s.result ?: s.source)
                    binding.previewPlaceholder.visibility =
                        if (s.result == null && s.source == null) View.VISIBLE else View.GONE

                    // Info
                    binding.tvInfo.text = s.info

                    // Status
                    binding.tvStatus.text = s.statusText
                    binding.tvPercent.text = if (s.progressPercent > 0) "${s.progressPercent}%" else ""
                    binding.tvProgress.text = s.progressText
                    binding.statusDot.setBackgroundResource(
                        when (s.statusKind) {
                            StatusKind.IDLE -> R.drawable.dot_idle
                            StatusKind.RUNNING -> R.drawable.dot_running
                            StatusKind.DOWNLOADING -> R.drawable.dot_downloading
                            StatusKind.DONE -> R.drawable.dot_done
                            StatusKind.ERROR -> R.drawable.dot_error
                            StatusKind.CANCELLED -> R.drawable.dot_idle
                        }
                    )

                    // Progress
                    val busy = s.processing || s.downloading
                    if (busy) {
                        binding.linearProgress.visibility = View.VISIBLE
                        binding.linearProgress.isIndeterminate = s.progressPercent == 0
                        binding.linearProgress.setProgressCompat(s.progressPercent, true)

                        binding.progressOverlay.visibility = View.VISIBLE
                        binding.progressScrim.visibility = View.VISIBLE
                        binding.circularProgress.isIndeterminate = s.progressPercent == 0
                        if (s.progressPercent > 0) {
                            binding.circularProgress.setProgressCompat(s.progressPercent, true)
                        }
                    } else if (s.progressPercent > 0 && s.progressPercent < 100) {
                        binding.linearProgress.visibility = View.VISIBLE
                        binding.linearProgress.isIndeterminate = false
                        binding.linearProgress.setProgressCompat(s.progressPercent, true)
                        binding.progressOverlay.visibility = View.GONE
                        binding.progressScrim.visibility = View.GONE
                    } else {
                        binding.linearProgress.visibility = View.INVISIBLE
                        binding.progressOverlay.visibility = View.GONE
                        binding.progressScrim.visibility = View.GONE
                    }

                    // Cancel button
                    binding.btnCancel.visibility = if (s.canCancel) View.VISIBLE else View.GONE

                    // FAB enable state
                    binding.fabAdd.isEnabled = !busy

                    // Threads
                    binding.tvThreadCount.text = s.threadCount.toString()
                    binding.sliderThreads.isEnabled = !busy

                    // Log
                    binding.tvLog.text = s.log.takeLast(14).joinToString("\n")

                    // Model chips
                    if (binding.chipGroupModels.childCount != s.allModels.size) {
                        buildModelChips(s.allModels, s.selectedModelId)
                    } else {
                        chipModelMap[s.selectedModelId]?.isChecked = true
                    }
                    updateModelDetail(s.selectedModel)
                    binding.chipGroupModels.isEnabled = !busy

                    // GPU
                    s.gpuInfo?.let { gpu ->
                        binding.tvGpu.text = if (gpu.isAdreno && gpu.adrenoSeries != "unknown")
                            "Adreno ${gpu.adrenoSeries}" else "GPU"
                        binding.tvVulkan.text = if (gpu.supportsVulkan)
                            "Vulkan: API ${gpu.vulkanApiLevel}" +
                                if (gpu.supportsFp16) " · FP16" else ""
                        else "Vulkan: tidak didukung"
                    }
                }
            }
        }
    }

    private fun refreshSystem() {
        try {
            val stats = SystemMonitor.snapshot(this, vm.state.value.backend.label)
            binding.tvCpu.text = "${stats.cpuCores}c · ${stats.cpuFreqMhz}MHz"
            binding.tvRam.text = "${stats.ramUsedMb}/${stats.ramTotalMb}M"
        } catch (_: Throwable) {}
    }

    private fun loadUri(uri: Uri) {
        lifecycleScope.launch(Dispatchers.IO) {
            try {
                val s = contentResolver.openInputStream(uri) ?: return@launch
                val bmp = BitmapFactory.decodeStream(s)
                s.close()
                if (bmp == null) withContext(Dispatchers.Main) { toast("Gagal memuat") }
                else withContext(Dispatchers.Main) {
                    vm.setSource(bmp)
                    // Animate preview
                    binding.imagePreview.alpha = 0f
                    binding.imagePreview.animate().alpha(1f).setDuration(300).start()
                }
            } catch (e: Throwable) {
                withContext(Dispatchers.Main) { toast("Error: ${e.message}") }
            }
        }
    }

    private fun toast(m: String) = Toast.makeText(this, m, Toast.LENGTH_SHORT).show()
}

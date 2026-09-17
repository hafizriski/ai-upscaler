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
import android.view.animation.OvershootInterpolator
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.FileProvider
import androidx.dynamicanimation.animation.DynamicAnimation
import androidx.dynamicanimation.animation.SpringAnimation
import androidx.dynamicanimation.animation.SpringForce
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.recyclerview.widget.GridLayoutManager
import com.arthexdev.exups.R
import com.arthexdev.exups.data.repository.GalleryRepository
import com.arthexdev.exups.data.repository.ProfileRepository
import com.arthexdev.exups.databinding.ActivityMainBinding
import com.arthexdev.exups.ml.engine.Backend
import com.arthexdev.exups.ml.engine.ModelRegistry
import com.arthexdev.exups.ml.engine.ModelSpec
import com.arthexdev.exups.ui.gallery.GalleryAdapter
import com.arthexdev.exups.util.ImageSaver
import com.arthexdev.exups.util.PermissionHelper
import com.arthexdev.exups.util.SystemMonitor
import com.google.android.material.chip.Chip
import com.google.android.material.snackbar.Snackbar
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private val vm: MainViewModel by viewModels()
    private val ui = Handler(Looper.getMainLooper())
    private val chipModelMap = mutableMapOf<String, Chip>()

    private lateinit var profileRepo: ProfileRepository
    private lateinit var galleryRepo: GalleryRepository
    private lateinit var galleryAdapter: GalleryAdapter

    private var currentTab = 0

    private val pick = registerForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        uri?.let { loadUri(it) }
    }

    private val pickPhoto = registerForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        uri?.let {
            if (profileRepo.savePhotoFromUri(it)) {
                loadProfilePhoto()
                toast("Foto profil tersimpan")
            } else toast("Gagal simpan foto")
        }
    }

    private val requestPermission = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { result ->
        if (result.values.all { it }) toast("Izin diberikan") else toast("Izin ditolak")
    }

    private val systemTicker = object : Runnable {
        override fun run() {
            if (currentTab == 0) refreshSystem()
            ui.postDelayed(this, 1500)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        profileRepo = ProfileRepository(this)
        galleryRepo = GalleryRepository(this)

        setupGallery()
        setupProfile()
        setupListeners()
        setupAnimations()
        observeState()
        ensurePermissions()
        showTab(0)
        ui.post(systemTicker)
    }

    private fun setupGallery() {
        galleryAdapter = GalleryAdapter { toast("Tap: ${it.name}") }
        binding.rvGallery.layoutManager = GridLayoutManager(this, 2)
        binding.rvGallery.adapter = galleryAdapter
        refreshGallery()
    }

    private fun refreshGallery() {
        val items = galleryRepo.listItems()
        galleryAdapter.update(items)
        binding.tvGalleryCount.text = "${items.size} item"
        binding.galleryEmpty.visibility = if (items.isEmpty()) View.VISIBLE else View.GONE
        binding.rvGallery.visibility = if (items.isEmpty()) View.GONE else View.VISIBLE
    }

    private fun setupProfile() {
        binding.etUsername.setText(profileRepo.getUsername())
        loadProfilePhoto()

        binding.avatarContainer.setOnClickListener {
            it.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
            pickPhoto.launch("image/*")
        }

        binding.btnSaveProfile.setOnClickListener {
            it.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
            val username = binding.etUsername.text?.toString()?.trim() ?: ""
            if (username.isEmpty()) {
                toast("Username tidak boleh kosong")
                return@setOnClickListener
            }
            profileRepo.setUsername(username)
            springScale(it)
            Snackbar.make(binding.root, getString(R.string.profile_saved), Snackbar.LENGTH_SHORT).show()
        }
    }

    private fun loadProfilePhoto() {
        val bmp = profileRepo.getPhoto()
        if (bmp != null) {
            binding.ivProfilePhoto.setImageBitmap(bmp)
            binding.ivProfilePhoto.visibility = View.VISIBLE
            binding.ivProfilePhotoDefault.visibility = View.GONE
        } else {
            binding.ivProfilePhoto.visibility = View.GONE
            binding.ivProfilePhotoDefault.visibility = View.VISIBLE
        }
    }

    private fun setupAnimations() {
        // iOS spring animation for FAB
        binding.fabAdd.scaleX = 0f
        binding.fabAdd.scaleY = 0f
        SpringAnimation(binding.fabAdd, DynamicAnimation.SCALE_X, 1f).apply {
            spring = SpringForce(1f).apply {
                dampingRatio = SpringForce.DAMPING_RATIO_MEDIUM_BOUNCY
                stiffness = SpringForce.STIFFNESS_LOW
            }
            setStartValue(0f)
            start()
        }
        SpringAnimation(binding.fabAdd, DynamicAnimation.SCALE_Y, 1f).apply {
            spring = SpringForce(1f).apply {
                dampingRatio = SpringForce.DAMPING_RATIO_MEDIUM_BOUNCY
                stiffness = SpringForce.STIFFNESS_LOW
            }
            setStartValue(0f)
            start()
        }

        // Bottom nav slide up
        binding.bottomNav.translationY = 100f
        binding.bottomNav.animate().translationY(0f).alpha(1f).setDuration(400).start()
    }

    private fun springScale(v: View) {
        SpringAnimation(v, DynamicAnimation.SCALE_X, 1f).apply {
            spring = SpringForce(1f).apply {
                dampingRatio = SpringForce.DAMPING_RATIO_MEDIUM_BOUNCY
            }
            setStartValue(0.92f)
            start()
        }
        SpringAnimation(v, DynamicAnimation.SCALE_Y, 1f).apply {
            spring = SpringForce(1f).apply {
                dampingRatio = SpringForce.DAMPING_RATIO_MEDIUM_BOUNCY
            }
            setStartValue(0.92f)
            start()
        }
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
        binding.fabAdd.setOnClickListener {
            it.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
            springScale(it)
            if (!PermissionHelper.hasStoragePermission(this)) {
                requestPermission.launch(PermissionHelper.getRequiredPermissions())
            } else pick.launch("image/*")
        }

        binding.navHome.setOnClickListener {
            it.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
            showTab(0)
        }
        binding.navGallery.setOnClickListener {
            it.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
            showTab(1)
            refreshGallery()
        }
        binding.navProfile.setOnClickListener {
            it.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
            showTab(2)
            loadProfilePhoto()
        }

        binding.btnCancel.setOnClickListener {
            vm.cancel()
            toast("Dibatalkan")
        }

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

    private fun showTab(index: Int) {
        currentTab = index

        binding.tabHome.visibility = View.GONE
        binding.tabGallery.visibility = View.GONE
        binding.tabProfile.visibility = View.GONE

        binding.ivNavHome.setColorFilter(getColor(R.color.ios_label_secondary))
        binding.tvNavHome.setTextColor(getColor(R.color.ios_label_secondary))
        binding.ivNavGallery.setColorFilter(getColor(R.color.ios_label_secondary))
        binding.tvNavGallery.setTextColor(getColor(R.color.ios_label_secondary))
        binding.ivNavProfile.setColorFilter(getColor(R.color.ios_label_secondary))
        binding.tvNavProfile.setTextColor(getColor(R.color.ios_label_secondary))

        when (index) {
            0 -> {
                binding.tabHome.visibility = View.VISIBLE
                binding.fabAdd.visibility = View.VISIBLE
                binding.ivNavHome.setColorFilter(getColor(R.color.ios_blue))
                binding.tvNavHome.setTextColor(getColor(R.color.ios_blue))
            }
            1 -> {
                binding.tabGallery.visibility = View.VISIBLE
                binding.fabAdd.visibility = View.GONE
                binding.ivNavGallery.setColorFilter(getColor(R.color.ios_blue))
                binding.tvNavGallery.setTextColor(getColor(R.color.ios_blue))
                refreshGallery()
            }
            2 -> {
                binding.tabProfile.visibility = View.VISIBLE
                binding.fabAdd.visibility = View.GONE
                binding.ivNavProfile.setColorFilter(getColor(R.color.ios_blue))
                binding.tvNavProfile.setTextColor(getColor(R.color.ios_blue))
            }
        }

        val target = when (index) { 0 -> binding.tabHome; 1 -> binding.tabGallery; else -> binding.tabProfile }
        target.alpha = 0f
        target.animate().alpha(1f).setDuration(200).start()
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
                textSize = 15f
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
                    binding.imagePreview.setImageBitmap(s.result ?: s.source)
                    binding.previewPlaceholder.visibility =
                        if (s.result == null && s.source == null) View.VISIBLE else View.GONE

                    binding.tvInfo.text = s.info
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

                    val busy = s.processing || s.downloading
                    if (busy) {
                        binding.linearProgress.visibility = View.VISIBLE
                        binding.linearProgress.isIndeterminate = s.progressPercent == 0
                        binding.linearProgress.setProgressCompat(s.progressPercent, true)
                        binding.progressOverlay.visibility = View.VISIBLE
                        binding.progressScrim.visibility = View.VISIBLE
                        binding.circularProgress.isIndeterminate = s.progressPercent == 0
                        if (s.progressPercent > 0) binding.circularProgress.setProgressCompat(s.progressPercent, true)
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

                    binding.btnCancel.visibility = if (s.canCancel) View.VISIBLE else View.GONE
                    binding.fabAdd.isEnabled = !busy

                    binding.tvThreadCount.text = s.threadCount.toString()
                    binding.sliderThreads.isEnabled = !busy

                    binding.tvLog.text = s.log.takeLast(14).joinToString("\n")

                    if (binding.chipGroupModels.childCount != s.allModels.size) {
                        buildModelChips(s.allModels, s.selectedModelId)
                    } else chipModelMap[s.selectedModelId]?.isChecked = true
                    updateModelDetail(s.selectedModel)
                    binding.chipGroupModels.isEnabled = !busy

                    s.gpuInfo?.let { gpu ->
                        binding.tvGpu.text = if (gpu.isAdreno && gpu.adrenoSeries != "unknown")
                            "Adreno ${gpu.adrenoSeries}" else "GPU"
                        binding.tvVulkan.text = if (gpu.supportsVulkan)
                            "Vulkan: API ${gpu.vulkanApiLevel}" + if (gpu.supportsFp16) " · FP16" else ""
                        else "Vulkan: tidak didukung"
                    }

                    // Save hasil ke gallery lokal (sekali per hasil)
                    if (s.result != null && s.statusKind == StatusKind.DONE && !s.processing) {
                        if (binding.imagePreview.tag != s.result) {
                            binding.imagePreview.tag = s.result
                            galleryRepo.saveResult(s.result)
                            refreshGallery()
                        }
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

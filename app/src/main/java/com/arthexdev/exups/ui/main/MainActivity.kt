package com.arthexdev.exups.ui.main

import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.HapticFeedbackConstants
import android.view.View
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.FileProvider
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
import com.arthexdev.exups.ui.batch.BatchActivity
import com.arthexdev.exups.ui.gallery.GalleryAdapter
import com.arthexdev.exups.ui.gallery.GalleryViewerActivity
import com.arthexdev.exups.ui.settings.SettingsActivity
import com.arthexdev.exups.ui.settings.SettingsPreferences
import com.arthexdev.exups.ui.widget.BeforeAfterSlider
import com.arthexdev.exups.util.ImageSaver
import com.arthexdev.exups.util.NotificationHelper
import com.arthexdev.exups.util.PermissionHelper
import com.arthexdev.exups.util.SystemMonitor
import com.arthexdev.exups.util.ThemePreferences
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
            }
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
        ThemePreferences.applyStored(this)
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        profileRepo = ProfileRepository(this)
        galleryRepo = GalleryRepository(this)
        NotificationHelper.createChannels(this)

        setupGallery()
        setupProfile()
        setupListeners()
        observeState()
        ensurePermissions()
        showTab(0)
        ui.post(systemTicker)
    }

    private fun setupGallery() {
        galleryAdapter = GalleryAdapter(
            onItemClick = { item ->
                val i = Intent(this, GalleryViewerActivity::class.java).apply {
                    putExtra(GalleryViewerActivity.EXTRA_PATH, item.file.absolutePath)
                }
                startActivity(i)
            },
            onItemLongClick = { item ->
                androidx.appcompat.app.AlertDialog.Builder(this)
                    .setTitle(item.name)
                    .setMessage(getString(R.string.delete_confirm))
                    .setPositiveButton(getString(R.string.action_delete)) { _, _ ->
                        if (item.file.delete()) {
                            refreshGallery()
                            toast(getString(R.string.deleted_success))
                        }
                    }
                    .setNegativeButton(getString(R.string.action_cancel), null)
                    .show()
            }
        )
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
            pickPhoto.launch("image/*")
        }

        binding.btnSaveProfile.setOnClickListener { view ->
            view.performHapticFeedback(android.view.HapticFeedbackConstants.KEYBOARD_TAP)
            val username = binding.etUsername.text?.toString()?.trim() ?: ""
            if (username.isEmpty()) {
                toast("Username tidak boleh kosong")
                return@setOnClickListener
            }
            profileRepo.setUsername(username)
            Snackbar.make(binding.root, getString(R.string.profile_saved), Snackbar.LENGTH_SHORT).show()
        }
    }

        binding.btnOpenSettings.setOnClickListener { view ->
            view.performHapticFeedback(android.view.HapticFeedbackConstants.KEYBOARD_TAP)
            startActivity(Intent(this, SettingsActivity::class.java))
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

    private fun ensurePermissions() {
        if (!PermissionHelper.hasPermission(this)) {
            requestPermission.launch(PermissionHelper.getRequiredPermissions())
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        ui.removeCallbacks(systemTicker)
    }

    private fun setupListeners() {
        binding.fabAdd.setOnLongClickListener { view ->
            view.performHapticFeedback(android.view.HapticFeedbackConstants.LONG_PRESS)
            startActivity(Intent(this, BatchActivity::class.java))
            true
        }
        binding.fabAdd.setOnClickListener { view ->
            view.performHapticFeedback(android.view.HapticFeedbackConstants.KEYBOARD_TAP)
            if (!PermissionHelper.hasStoragePermission(this)) {
                requestPermission.launch(PermissionHelper.getRequiredPermissions())
            } else pick.launch("image/*")
        }

        binding.btnUpscale.setOnClickListener { view ->
            view.performHapticFeedback(android.view.HapticFeedbackConstants.KEYBOARD_TAP)
            val selectedModel = vm.state.value.selectedModel
            ProcessOptionsDialog.show(this, selectedModel) { result ->
                vm.setBackend(
                    if (result.useGpu) com.arthexdev.exups.ml.engine.Backend.AUTO
                    else com.arthexdev.exups.ml.engine.Backend.CPU
                )
                vm.ensureModelAndUpscale()
            }
        }
        binding.btnSave.setOnClickListener { view ->
            view.performHapticFeedback(android.view.HapticFeedbackConstants.KEYBOARD_TAP)
            saveResult()
        }
        binding.btnShare.setOnClickListener { view ->
            view.performHapticFeedback(android.view.HapticFeedbackConstants.KEYBOARD_TAP)
            shareResult()
        }

        binding.navHome.setOnClickListener {
            showTab(0)
        }
        binding.navGallery.setOnClickListener {
            showTab(1)
            refreshGallery()
        }
        binding.navProfile.setOnClickListener {
            showTab(2)
            loadProfilePhoto()
        }

        binding.btnCancel.setOnClickListener { view ->
            view.performHapticFeedback(android.view.HapticFeedbackConstants.KEYBOARD_TAP)
            vm.cancel()
            toast("Dibatalkan")
        }

        binding.btnOpenSettings.setOnClickListener { view ->
            view.performHapticFeedback(android.view.HapticFeedbackConstants.KEYBOARD_TAP)
            startActivity(Intent(this, SettingsActivity::class.java))
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
            binding.tvLog.visibility = if (visible) View.GONE else View.VISIBLE
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
                binding.tabHome.alpha = 0f
                binding.tabHome.animate().alpha(1f).setDuration(220).start()
                binding.fabAdd.visibility = View.VISIBLE
                binding.ivNavHome.setColorFilter(getColor(R.color.ios_blue))
                binding.tvNavHome.setTextColor(getColor(R.color.ios_blue))
            }
            1 -> {
                binding.tabGallery.visibility = View.VISIBLE
                binding.tabGallery.alpha = 0f
                binding.tabGallery.animate().alpha(1f).setDuration(220).start()
                binding.fabAdd.visibility = View.GONE
                binding.ivNavGallery.setColorFilter(getColor(R.color.ios_blue))
                binding.tvNavGallery.setTextColor(getColor(R.color.ios_blue))
                refreshGallery()
            }
            2 -> {
                binding.tabProfile.visibility = View.VISIBLE
                binding.tabProfile.alpha = 0f
                binding.tabProfile.animate().alpha(1f).setDuration(220).start()
                binding.fabAdd.visibility = View.GONE
                binding.ivNavProfile.setColorFilter(getColor(R.color.ios_blue))
                binding.tvNavProfile.setTextColor(getColor(R.color.ios_blue))
            }
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
                setOnClickListener { vm.setModel(spec.id) }
            }
            binding.chipGroupModels.addView(chip)
            chipModelMap[spec.id] = chip
        }
    }

    private fun updateModelDetail(spec: ModelSpec?) {
        if (spec == null) return
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

                    // Before/After slider (findViewById, bukan binding include)
                    val slider = findViewById<BeforeAfterSlider>(R.id.beforeAfterSlider)
                    if (s.source != null && s.result != null && slider != null) {
                        slider.setBitmaps(s.source, s.result)
                        slider.visibility = View.VISIBLE
                        binding.imagePreview.visibility = View.GONE
                    } else {
                        slider?.visibility = View.GONE
                        binding.imagePreview.visibility = View.VISIBLE
                    }

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
                        if (s.progressPercent > 0) {
                            binding.circularProgress.setProgressCompat(s.progressPercent, true)
                        }
                        binding.tvOverlayPercent.text =
                            if (s.progressPercent > 0) "${s.progressPercent}%" else ""
                    } else {
                        binding.linearProgress.visibility = View.INVISIBLE
                        binding.progressOverlay.visibility = View.GONE
                        binding.progressScrim.visibility = View.GONE
                    }

                    binding.btnCancel.visibility = if (s.canCancel) View.VISIBLE else View.GONE
                    binding.fabAdd.isEnabled = !busy

                    val hasSource = s.source != null
                    val hasResult = s.result != null
                    binding.btnUpscale.visibility =
                        if (hasSource && !hasResult && !busy) View.VISIBLE else View.GONE
                    binding.resultActions.visibility =
                        if (hasResult && !busy) View.VISIBLE else View.GONE
                    binding.emptyActionHint.visibility =
                        if (!hasSource) View.VISIBLE else View.GONE

                    if (binding.chipGroupModels.childCount != s.allModels.size) {
                        buildModelChips(s.allModels, s.selectedModelId)
                    } else chipModelMap[s.selectedModelId]?.isChecked = true
                    updateModelDetail(s.selectedModel)
                    binding.chipGroupModels.isEnabled = !busy

                    binding.tvLog.text = s.log.takeLast(14).joinToString("\n")

                    s.gpuInfo?.let { gpu ->
                        binding.tvGpu.text = if (gpu.isAdreno && gpu.adrenoSeries != "unknown")
                            "Adreno ${gpu.adrenoSeries}" else "GPU"
                        binding.tvVulkan.text = if (gpu.supportsVulkan)
                            "Vulkan: API ${gpu.vulkanApiLevel}" + if (gpu.supportsFp16) " · FP16" else ""
                        else "Vulkan: tidak didukung"
                    }

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
            val stats = SystemMonitor.snapshot(this)
            binding.tvCpu.text = "${stats.cpuCores}c · ${stats.cpuFreqMhz}MHz"
            binding.tvRam.text = "${stats.ramUsedMb}/${stats.ramTotalMb}M"
        } catch (_: Throwable) {}
    }

    private fun loadUri(uri: Uri) {
        lifecycleScope.launch(Dispatchers.IO) {
            try {
                val stream = contentResolver.openInputStream(uri) ?: return@launch
                val bmp = BitmapFactory.decodeStream(stream)
                stream.close()
                if (bmp == null) {
                    withContext(Dispatchers.Main) { toast("Gagal memuat") }
                } else {
                    withContext(Dispatchers.Main) {
                        vm.setSource(bmp)
                        binding.imagePreview.alpha = 0f
                        binding.imagePreview.animate().alpha(1f).setDuration(300).start()
                    }
                }
            } catch (e: Throwable) {
                withContext(Dispatchers.Main) { toast("Error: ${e.message}") }
            }
        }
    }

    private fun saveResult() {
        val bmp = vm.state.value.result ?: return
        lifecycleScope.launch(Dispatchers.IO) {
            val uri = ImageSaver.saveToGallery(
                this@MainActivity, bmp,
                "upscaled_${System.currentTimeMillis()}.png"
            )
            withContext(Dispatchers.Main) {
                if (uri != null) {
                    Snackbar.make(binding.root, getString(R.string.saved_success), Snackbar.LENGTH_LONG).show()
                } else toast("Gagal menyimpan")
            }
        }
    }

    private fun shareResult() {
        val bmp = vm.state.value.result ?: return
        lifecycleScope.launch(Dispatchers.IO) {
            try {
                val path = File(cacheDir, "share_${System.currentTimeMillis()}.png")
                path.outputStream().use { bmp.compress(Bitmap.CompressFormat.PNG, 100, it) }
                val uri = FileProvider.getUriForFile(
                    this@MainActivity, "$packageName.fileprovider", path
                )
                withContext(Dispatchers.Main) {
                    val intent = Intent(Intent.ACTION_SEND).apply {
                        type = "image/png"
                        putExtra(Intent.EXTRA_STREAM, uri)
                        putExtra(Intent.EXTRA_TEXT, "Upscaled with Ex Upscaler")
                        putExtra(Intent.EXTRA_TITLE, "Ex Upscaler")
                        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                    }
                    startActivity(Intent.createChooser(intent, "Bagikan"))
                }
            } catch (e: Throwable) {
                withContext(Dispatchers.Main) { toast("Gagal bagikan: ${e.message}") }
            }
        }
    }

    private fun toast(m: String) = Toast.makeText(this, m, Toast.LENGTH_SHORT).show()

}

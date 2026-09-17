package com.example.aiupscaler.ui.main

import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.View
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.FileProvider
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.updateLayoutParams
import androidx.core.view.updatePadding
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.example.aiupscaler.R
import com.example.aiupscaler.databinding.ActivityMainBinding
import com.example.aiupscaler.ml.engine.Backend
import com.example.aiupscaler.util.ImageSaver
import com.example.aiupscaler.util.PermissionHelper
import com.example.aiupscaler.util.SystemMonitor
import com.google.android.material.snackbar.Snackbar
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private val vm: MainViewModel by viewModels()
    private val ui = Handler(Looper.getMainLooper())

    private val pick = registerForActivityResult(
        ActivityResultContracts.GetContent()
    ) { uri -> uri?.let { loadUri(it) } }

    private val requestPermission = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { result ->
        if (result.values.all { it }) toast("Izin diberikan")
        else toast("Izin ditolak")
    }

    private val systemTicker = object : Runnable {
        override fun run() {
            refreshSystem()
            ui.postDelayed(this, 1500)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        WindowCompat.setDecorFitsSystemWindows(window, false)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        applyWindowInsets()
        setupListeners()
        observeState()
        ensurePermissions()

        ui.post(systemTicker)
    }

    private fun applyWindowInsets() {
        ViewCompat.setOnApplyWindowInsetsListener(binding.root) { _, insets ->
            val sysBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            val cutout = insets.getInsets(WindowInsetsCompat.Type.displayCutout())

            val density = resources.displayMetrics.density
            val basePadBottom = (12 * density).toInt()
            val basePadLR = (12 * density).toInt()

            // FIX: padding TOP toolbar = status bar height + 8dp
            val extraTop = (8 * density).toInt()
            binding.toolbar.updatePadding(
                top = sysBars.top + extraTop,
                bottom = extraTop,
                left = cutout.left,
                right = cutout.right
            )

            // Bottom bar: padding bottom = nav bar height
            binding.bottomBar.updatePadding(
                bottom = basePadBottom + sysBars.bottom,
                left = basePadLR + cutout.left,
                right = basePadLR + cutout.right
            )

            insets
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
        binding.btnPick.setOnClickListener {
            if (!PermissionHelper.hasPermission(this)) {
                requestPermission.launch(PermissionHelper.getRequiredPermissions())
            } else {
                pick.launch("image/*")
            }
        }
        binding.btnUpscale.setOnClickListener {
            if (vm.state.value.source == null) toast("Pilih gambar dulu")
            else vm.upscale()
        }
        binding.btnSave.setOnClickListener { saveResult() }
        binding.btnShare.setOnClickListener { shareResult() }

        binding.toggleBackend.addOnButtonCheckedListener { _, id, checked ->
            if (checked) vm.setBackend(if (id == binding.btnGpu.id) Backend.GPU else Backend.CPU)
        }
        binding.toggleBackend.check(binding.btnCpu.id)

        // CPU threads slider
        val maxThreads = Runtime.getRuntime().availableProcessors().coerceAtLeast(1)
        binding.sliderThreads.valueTo = maxThreads.toFloat()
        binding.sliderThreads.value = vm.state.value.threadCount.toFloat().coerceAtLeast(1f)
        binding.tvThreadCount.text = vm.state.value.threadCount.toString()

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

    private fun observeState() {
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                vm.state.collect { s ->
                    binding.imagePreview.setImageBitmap(s.result ?: s.source)
                    binding.tvInfo.text = s.info
                    binding.tvStatus.text = s.statusText
                    binding.tvPercent.text = if (s.progressPercent > 0) "${s.progressPercent}%" else ""
                    binding.tvProgress.text = s.progressText
                    binding.statusDot.setBackgroundResource(
                        when (s.statusKind) {
                            StatusKind.IDLE -> R.drawable.dot_idle
                            StatusKind.RUNNING -> R.drawable.dot_running
                            StatusKind.DONE -> R.drawable.dot_done
                            StatusKind.ERROR -> R.drawable.dot_error
                        }
                    )
                    if (s.processing) {
                        binding.linearProgress.visibility = View.VISIBLE
                        binding.linearProgress.isIndeterminate = s.progressPercent == 0
                        binding.linearProgress.setProgressCompat(s.progressPercent, true)
                    } else if (s.progressPercent > 0) {
                        binding.linearProgress.visibility = View.VISIBLE
                        binding.linearProgress.isIndeterminate = false
                        binding.linearProgress.setProgressCompat(s.progressPercent, true)
                    } else {
                        binding.linearProgress.visibility = View.INVISIBLE
                    }
                    binding.btnUpscale.isEnabled = s.source != null && !s.processing
                    binding.btnPick.isEnabled = !s.processing
                    binding.btnSave.isEnabled = s.result != null && !s.processing
                    binding.btnShare.isEnabled = s.result != null && !s.processing
                    binding.toggleBackend.isEnabled = !s.processing
                    binding.sliderThreads.isEnabled = !s.processing
                    binding.chipBackend.text = s.backend.label
                    binding.tvLog.text = s.log.takeLast(14).joinToString("\n")
                    binding.tvThreadCount.text = s.threadCount.toString()

                    // GPU info display
                    s.gpuInfo?.let { gpu ->
                        binding.tvGpu.text = gpu.adrenoSeries.takeIf { it != "unknown" }
                            ?.let { "Adreno $it" } ?: "GPU"
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
                else withContext(Dispatchers.Main) { vm.setSource(bmp) }
            } catch (e: Throwable) {
                withContext(Dispatchers.Main) { toast("Error: ${e.message}") }
            }
        }
    }

    private fun saveResult() {
        val bmp = vm.state.value.result ?: return
        lifecycleScope.launch(Dispatchers.IO) {
            val uri = ImageSaver.saveToGallery(
                context = this@MainActivity,
                bitmap = bmp,
                displayName = "upscaled_${System.currentTimeMillis()}.png"
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

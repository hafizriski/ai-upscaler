package com.example.aiupscaler.ui.main

import android.content.ContentValues
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.provider.MediaStore
import android.view.View
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.FileProvider
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.example.aiupscaler.R
import com.example.aiupscaler.databinding.ActivityMainBinding
import com.example.aiupscaler.ml.engine.Backend
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
        observeState()
        ui.post(systemTicker)
    }

    override fun onDestroy() {
        super.onDestroy()
        ui.removeCallbacks(systemTicker)
    }

    private fun setupListeners() {
        binding.btnPick.setOnClickListener { pick.launch("image/*") }
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
                    binding.chipBackend.text = s.backend.label
                    binding.tvLog.text = s.log.takeLast(12).joinToString("\n")
                }
            }
        }
    }

    private fun refreshSystem() {
        try {
            val stats = SystemMonitor.snapshot(this, vm.state.value.backend.label)
            binding.tvCpu.text = "${stats.cpuCores}c · ${stats.cpuFreqMhz}MHz"
            binding.tvRam.text = "${stats.ramUsedMb}/${stats.ramTotalMb}M"
            binding.tvThermal.text = stats.thermal
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
            try {
                val values = ContentValues().apply {
                    put(MediaStore.Images.Media.DISPLAY_NAME, "upscaled_${System.currentTimeMillis()}.png")
                    put(MediaStore.Images.Media.MIME_TYPE, "image/png")
                    put(MediaStore.Images.Media.RELATIVE_PATH, "Pictures/AIUpscaler")
                }
                val uri = contentResolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values)
                uri?.let {
                    contentResolver.openOutputStream(it)?.use { os ->
                        bmp.compress(Bitmap.CompressFormat.PNG, 100, os)
                    }
                    withContext(Dispatchers.Main) {
                        Snackbar.make(binding.root, getString(R.string.saved_success), Snackbar.LENGTH_LONG).show()
                    }
                }
            } catch (e: Throwable) {
                withContext(Dispatchers.Main) { toast("Gagal simpan: ${e.message}") }
            }
        }
    }

    private fun shareResult() {
        val bmp = vm.state.value.result ?: return
        lifecycleScope.launch(Dispatchers.IO) {
            try {
                val path = File(cacheDir, "share_${System.currentTimeMillis()}.png")
                path.outputStream().use { bmp.compress(Bitmap.CompressFormat.PNG, 100, it) }
                val uri = FileProvider.getUriForFile(this@MainActivity, "$packageName.fileprovider", path)
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

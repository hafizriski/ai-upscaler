package com.example.aiupscaler.ui

import android.content.ContentValues
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Bundle
import android.provider.MediaStore
import android.view.View
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.example.aiupscaler.databinding.ActivityMainBinding
import com.example.aiupscaler.ml.Backend
import com.example.aiupscaler.ml.TileProcessor
import com.example.aiupscaler.ml.UpscalerInterpreter
import com.google.android.material.snackbar.Snackbar
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private var sourceBitmap: Bitmap? = null
    private var resultBitmap: Bitmap? = null
    private var backend: Backend = Backend.CPU

    private val pick = registerForActivityResult(
        ActivityResultContracts.GetContent()
    ) { uri -> uri?.let { loadImage(it) } }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        binding.toggleBackend.check(binding.btnCpu.id)
        binding.toggleBackend.addOnButtonCheckedListener { _, checkedId, isChecked ->
            if (isChecked) backend = if (checkedId == binding.btnGpu.id) Backend.GPU else Backend.CPU
        }
        binding.btnPick.setOnClickListener { pick.launch("image/*") }
        binding.btnUpscale.setOnClickListener {
            sourceBitmap?.let { runUpscale(it) } ?: toast(getString(com.example.aiupscaler.R.string.error_pick))
        }
        binding.btnSave.setOnClickListener { save() }
    }

    private fun loadImage(uri: Uri) {
        lifecycleScope.launch(Dispatchers.IO) {
            val stream = contentResolver.openInputStream(uri) ?: return@launch
            val bmp = BitmapFactory.decodeStream(stream)
            stream.close()
            withContext(Dispatchers.Main) {
                sourceBitmap = bmp; resultBitmap = null
                binding.imagePreview.setImageBitmap(bmp)
                binding.btnUpscale.isEnabled = true; binding.btnSave.isEnabled = false
                binding.tvInfo.text = getString(com.example.aiupscaler.R.string.info_source, bmp.width, bmp.height)
            }
        }
    }

    private fun runUpscale(src: Bitmap) {
        binding.linearProgress.visibility = View.VISIBLE
        binding.btnUpscale.isEnabled = false; binding.btnPick.isEnabled = false
        binding.tvInfo.text = getString(com.example.aiupscaler.R.string.info_processing)

        lifecycleScope.launch(Dispatchers.Default) {
            try {
                val engine = UpscalerInterpreter(this@MainActivity, "models/realesr_general_x4v3.tflite", backend)
                val out = try { TileProcessor(engine).process(src) } finally { engine.close() }
                withContext(Dispatchers.Main) {
                    resultBitmap = out
                    binding.imagePreview.setImageBitmap(out)
                    binding.linearProgress.visibility = View.INVISIBLE
                    binding.btnUpscale.isEnabled = true; binding.btnPick.isEnabled = true
                    binding.btnSave.isEnabled = true
                    binding.tvInfo.text = getString(com.example.aiupscaler.R.string.info_result, out.width, out.height)
                }
            } catch (e: Throwable) {
                withContext(Dispatchers.Main) {
                    binding.linearProgress.visibility = View.INVISIBLE
                    binding.btnUpscale.isEnabled = true; binding.btnPick.isEnabled = true
                    Snackbar.make(binding.root, getString(com.example.aiupscaler.R.string.error_process, e.message ?: "unknown"), Snackbar.LENGTH_LONG).show()
                }
            }
        }
    }

    private fun save() {
        val bmp = resultBitmap ?: return
        lifecycleScope.launch(Dispatchers.IO) {
            val values = ContentValues().apply {
                put(MediaStore.Images.Media.DISPLAY_NAME, "upscaled_${System.currentTimeMillis()}.png")
                put(MediaStore.Images.Media.MIME_TYPE, "image/png")
                put(MediaStore.Images.Media.RELATIVE_PATH, "Pictures/AIUpscaler")
            }
            val uri = contentResolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values)
            uri?.let {
                contentResolver.openOutputStream(it)?.use { os -> bmp.compress(Bitmap.CompressFormat.PNG, 100, os) }
                withContext(Dispatchers.Main) { toast(getString(com.example.aiupscaler.R.string.saved_ok)) }
            }
        }
    }

    private fun toast(msg: String) = Toast.makeText(this, msg, Toast.LENGTH_SHORT).show()
}

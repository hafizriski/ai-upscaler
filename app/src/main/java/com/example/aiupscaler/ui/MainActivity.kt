package com.example.aiupscaler.ui

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Bundle
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.example.aiupscaler.databinding.ActivityMainBinding
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private var sourceBitmap: Bitmap? = null

    private val pick = registerForActivityResult(
        ActivityResultContracts.GetContent()
    ) { uri -> uri?.let { loadImage(it) } }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        binding.btnPick.setOnClickListener { pick.launch("image/*") }

        binding.btnUpscale.setOnClickListener {
            Toast.makeText(
                this,
                "AI engine siap ditambahkan dengan model TFLite",
                Toast.LENGTH_LONG
            ).show()
        }

        binding.btnSave.setOnClickListener {
            Toast.makeText(this, "Belum ada hasil", Toast.LENGTH_SHORT).show()
        }
    }

    private fun loadImage(uri: Uri) {
        lifecycleScope.launch(Dispatchers.IO) {
            val stream = contentResolver.openInputStream(uri) ?: return@launch
            val bmp = BitmapFactory.decodeStream(stream)
            stream.close()
            withContext(Dispatchers.Main) {
                sourceBitmap = bmp
                binding.imagePreview.setImageBitmap(bmp)
                binding.btnUpscale.isEnabled = true
                binding.tvInfo.text = "Sumber: ${bmp.width}x${bmp.height}"
            }
        }
    }
}

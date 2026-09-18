package com.arthexdev.exups.ui.batch

import android.net.Uri
import android.os.Bundle
import android.view.View
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.arthexdev.exups.R
import com.arthexdev.exups.data.repository.GalleryRepository
import com.google.android.material.button.MaterialButton
import com.google.android.material.progressindicator.LinearProgressIndicator
import android.widget.TextView
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import android.graphics.BitmapFactory

/**
 * Activity sederhana untuk batch processing:
 * 1. Pilih beberapa gambar sekaligus
 * 2. Tampilkan preview list
 * 3. Tombol "Upscale All" — proses sequential
 */
class BatchActivity : AppCompatActivity() {

    private val selectedUris = mutableListOf<Uri>()
    private lateinit var rvPreview: RecyclerView
    private lateinit var tvCount: TextView
    private lateinit var progressBar: LinearProgressIndicator
    private lateinit var btnStart: MaterialButton

    private val pickMultiple = registerForActivityResult(
        ActivityResultContracts.GetMultipleContents()
    ) { uris ->
        if (!uris.isNullOrEmpty()) {
            selectedUris.clear()
            selectedUris.addAll(uris)
            tvCount.text = getString(R.string.batch_progress, uris.size, uris.size)
            updateUi()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_batch)

        rvPreview = findViewById(R.id.rvBatchPreview)
        tvCount = findViewById(R.id.tvBatchCount)
        progressBar = findViewById(R.id.batchProgress)
        btnStart = findViewById(R.id.btnBatchStart)

        rvPreview.layoutManager = GridLayoutManager(this, 3)
        // Simple adapter hanya untuk preview (pakai item_gallery.xml)
        rvPreview.adapter = null // placeholder, kita pakai list teks

        btnStart.setOnClickListener {
            pickMultiple.launch("image/*")
        }

        findViewById<View>(R.id.btnBatchClose).setOnClickListener { finish() }
    }

    private fun updateUi() {
        if (selectedUris.isEmpty()) {
            tvCount.text = getString(R.string.batch_pick)
            btnStart.text = getString(R.string.action_batch)
        } else {
            btnStart.text = "${getString(R.string.action_upscale)} (${selectedUris.size})"
        }
    }

    // Catatan: proses batch sebenarnya akan dijalankan via MainViewModel.batchUpscale()
    // yang di-inject dari MainActivity. Di versi ini, activity hanya untuk UI picker.
}

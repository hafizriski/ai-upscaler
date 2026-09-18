package com.arthexdev.exups.ui.batch

import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Bundle
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.arthexdev.exups.R
import com.google.android.material.button.MaterialButton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Batch picker — pilih multiple gambar, tampil count.
 * Proses sebenarnya akan dijalankan oleh MainViewModel.batchUpscale()
 * dari MainActivity (via long-press FAB).
 */
class BatchActivity : AppCompatActivity() {

    private val selectedUris = mutableListOf<Uri>()
    private lateinit var tvCount: TextView
    private lateinit var btnStart: MaterialButton

    private val pickMultiple = registerForActivityResult(
        ActivityResultContracts.GetMultipleContents()
    ) { uris ->
        if (!uris.isNullOrEmpty()) {
            selectedUris.clear()
            selectedUris.addAll(uris)
            tvCount.text = "Terpilih: ${uris.size} gambar"
            btnStart.text = "Upscale (${uris.size})"
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_batch)

        tvCount = findViewById(R.id.tvBatchCount)
        btnStart = findViewById(R.id.btnBatchStart)

        tvCount.text = getString(R.string.batch_pick)

        btnStart.setOnClickListener {
            pickMultiple.launch("image/*")
        }

        findViewById<android.view.View>(R.id.btnBatchClose)
            .setOnClickListener { finish() }
    }
}

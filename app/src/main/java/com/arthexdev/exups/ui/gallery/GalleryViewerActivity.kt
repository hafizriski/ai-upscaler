package com.arthexdev.exups.ui.gallery

import android.graphics.BitmapFactory
import android.os.Bundle
import android.view.MotionEvent
import android.view.ScaleGestureDetector
import android.view.View
import android.widget.ImageView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.FileProvider
import com.arthexdev.exups.R
import com.arthexdev.exups.data.repository.GalleryRepository
import android.content.Intent
import android.graphics.Matrix
import android.net.Uri
import java.io.File
import kotlin.math.abs

class GalleryViewerActivity : AppCompatActivity() {

    private lateinit var img: ImageView
    private var filePath: String? = null

    // Zoom & pan state
    private val matrix = Matrix()
    private var scaleFactor = 1f
    private var lastX = 0f
    private var lastY = 0f
    private var isDragging = false

    private val scaleDetector by lazy {
        ScaleGestureDetector(this, object : ScaleGestureDetector.SimpleOnScaleGestureListener() {
            override fun onScale(detector: ScaleGestureDetector): Boolean {
                scaleFactor = (scaleFactor * detector.scaleFactor).coerceIn(1f, 8f)
                matrix.setScale(scaleFactor, scaleFactor, detector.focusX, detector.focusY)
                img.imageMatrix = matrix
                return true
            }
        })
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_gallery_viewer)

        img = findViewById(R.id.ivViewer)
        filePath = intent.getStringExtra(EXTRA_PATH)

        if (filePath.isNullOrEmpty()) {
            finish(); return
        }

        try {
            val bmp = BitmapFactory.decodeFile(filePath)
            if (bmp != null) img.setImageBitmap(bmp)
            else Toast.makeText(this, "Gagal memuat", Toast.LENGTH_SHORT).show()
        } catch (_: Throwable) {
            Toast.makeText(this, "Gagal memuat", Toast.LENGTH_SHORT).show()
        }

        // Tap dua kali untuk reset zoom
        img.setOnClickListener {
            scaleFactor = 1f
            matrix.reset()
            img.imageMatrix = matrix
        }

        // Touch handling: pinch + pan
        img.setOnTouchListener { _, event ->
            scaleDetector.onTouchEvent(event)

            when (event.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    lastX = event.x; lastY = event.y; isDragging = true
                }
                MotionEvent.ACTION_MOVE -> {
                    if (!scaleDetector.isInProgress && isDragging && scaleFactor > 1.01f) {
                        val dx = event.x - lastX
                        val dy = event.y - lastY
                        matrix.postTranslate(dx, dy)
                        img.imageMatrix = matrix
                    }
                    lastX = event.x; lastY = event.y
                }
                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> isDragging = false
            }
            true
        }

        // Tombol share
        findViewById<View>(R.id.btnViewerShare).setOnClickListener { shareImage() }
        // Tombol close
        findViewById<View>(R.id.btnViewerClose).setOnClickListener { finish() }
    }

    private fun shareImage() {
        val path = filePath ?: return
        try {
            val f = File(path)
            if (!f.exists()) return
            val uri: Uri = FileProvider.getUriForFile(
                this, "$packageName.fileprovider", f
            )
            val intent = Intent(Intent.ACTION_SEND).apply {
                type = "image/png"
                putExtra(Intent.EXTRA_STREAM, uri)
                putExtra(Intent.EXTRA_TEXT, "Upscaled with Ex Upscaler")
                putExtra(Intent.EXTRA_TITLE, "Ex Upscaler")
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            startActivity(Intent.createChooser(intent, getString(R.string.action_share)))
        } catch (e: Throwable) {
            Toast.makeText(this, "Gagal share: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }

    companion object {
        const val EXTRA_PATH = "extra_path"
    }
}

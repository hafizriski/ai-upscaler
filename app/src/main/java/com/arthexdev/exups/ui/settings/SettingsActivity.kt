package com.arthexdev.exups.ui.settings

import android.os.Bundle
import android.view.View
import android.widget.TextView
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import com.arthexdev.exups.R
import com.google.android.material.materialswitch.MaterialSwitch
import com.google.android.material.slider.Slider

class SettingsActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_settings)

        findViewById<View>(R.id.btnSettingsBack).setOnClickListener { finish() }

        // ── Specify tilesize ──
        val swSpecifyTile = findViewById<MaterialSwitch>(R.id.swSpecifyTile)
        val sliderTile = findViewById<Slider>(R.id.sliderTilesize)
        val tvTileVal = findViewById<TextView>(R.id.tvTilesizeValue)

        swSpecifyTile.isChecked = SettingsPreferences.isSpecifyTilesize(this)
        sliderTile.value = SettingsPreferences.getTilesize(this).toFloat()
        tvTileVal.text = SettingsPreferences.getTilesize(this).toString()

        sliderTile.addOnChangeListener { _, value, fromUser ->
            if (fromUser) {
                val v = value.toInt()
                SettingsPreferences.setTilesize(this, v)
                tvTileVal.text = v.toString()
            }
        }

        swSpecifyTile.setOnCheckedChangeListener { _, checked ->
            SettingsPreferences.setSpecifyTilesize(this, checked)
            sliderTile.isEnabled = checked
            tvTileVal.alpha = if (checked) 1f else 0.4f
        }
        sliderTile.isEnabled = swSpecifyTile.isChecked
        tvTileVal.alpha = if (swSpecifyTile.isChecked) 1f else 0.4f

        // ── Limit GPU ──
        val swLimitGpu = findViewById<MaterialSwitch>(R.id.swLimitGpu)
        swLimitGpu.isChecked = SettingsPreferences.isLimitGpu(this)
        swLimitGpu.setOnCheckedChangeListener { _, checked ->
            SettingsPreferences.setLimitGpu(this, checked)
        }

        // ── Keep screen on ──
        val swKeepScreen = findViewById<MaterialSwitch>(R.id.swKeepScreen)
        swKeepScreen.isChecked = SettingsPreferences.isKeepScreenOn(this)
        swKeepScreen.setOnCheckedChangeListener { _, checked ->
            SettingsPreferences.setKeepScreenOn(this, checked)
        }

        // ── Pick from gallery ──
        val swPickGallery = findViewById<MaterialSwitch>(R.id.swPickGallery)
        swPickGallery.isChecked = SettingsPreferences.isPickFromGallery(this)
        swPickGallery.setOnCheckedChangeListener { _, checked ->
            SettingsPreferences.setPickFromGallery(this, checked)
        }

        // ── Save location ──
        val tvSaveLocation = findViewById<TextView>(R.id.tvSaveLocation)
        val current = SettingsPreferences.getSaveLocation(this)
        tvSaveLocation.text = if (current.isEmpty()) "Not set yet" else current

        findViewById<View>(R.id.rowSaveLocation).setOnClickListener {
            val options = arrayOf("Pictures/ExUpscaler", "Download/ExUpscaler", "DCIM/ExUpscaler")
            AlertDialog.Builder(this)
                .setTitle("Save location")
                .setItems(options) { _, which ->
                    val path = options[which]
                    SettingsPreferences.setSaveLocation(this, path)
                    tvSaveLocation.text = path
                }
                .show()
        }

        // ── Output format ──
        val tvFormat = findViewById<TextView>(R.id.tvOutputFormat)
        tvFormat.text = SettingsPreferences.getOutputFormat(this)

        findViewById<View>(R.id.rowOutputFormat).setOnClickListener {
            val options = arrayOf("auto", "png", "jpg", "webp")
            AlertDialog.Builder(this)
                .setTitle("Output image format")
                .setItems(options) { _, which ->
                    val fmt = options[which]
                    SettingsPreferences.setOutputFormat(this, fmt)
                    tvFormat.text = fmt
                }
                .show()
        }
    }
}

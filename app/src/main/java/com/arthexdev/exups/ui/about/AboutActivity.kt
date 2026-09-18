package com.arthexdev.exups.ui.about

import android.os.Bundle
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import com.arthexdev.exups.BuildConfig
import com.arthexdev.exups.R

class AboutActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_about)

        findViewById<TextView>(R.id.tvVersion).text =
            "Version ${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE})"

        findViewById<android.view.View>(R.id.btnAboutBack).setOnClickListener { finish() }
    }
}

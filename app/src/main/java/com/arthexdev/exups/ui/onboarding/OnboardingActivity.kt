package com.arthexdev.exups.ui.onboarding

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.arthexdev.exups.R

/**
 * Onboarding 3-slide — placeholder, diaktifkan nanti.
 */
class OnboardingActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // Placeholder — UI belum dibuat
        markOnboardingDone()
        startActivity(Intent(this, com.arthexdev.exups.ui.main.MainActivity::class.java))
        finish()
    }

    private fun markOnboardingDone() {
        getSharedPreferences("exups_onboarding", Context.MODE_PRIVATE)
            .edit().putBoolean("done", true).apply()
    }

    companion object {
        fun shouldShow(context: Context): Boolean =
            !context.getSharedPreferences("exups_onboarding", Context.MODE_PRIVATE)
                .getBoolean("done", false)
    }
}

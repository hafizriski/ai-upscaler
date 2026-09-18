package com.arthexdev.exups.service

import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import com.arthexdev.exups.R
import com.arthexdev.exups.util.NotificationHelper

class UpscaleNotificationService : Service() {

    companion object {
        const val ACTION_START = "com.arthexdev.exups.START_UPSCALE"
        const val ACTION_STOP = "com.arthexdev.exups.STOP_UPSCALE"
        const val ACTION_UPDATE = "com.arthexdev.exups.UPDATE_UPSCALE"
        const val EXTRA_PERCENT = "extra_percent"
        const val EXTRA_TEXT = "extra_text"

        fun update(context: Context, percent: Int, text: String) {
            val i = Intent(context, UpscaleNotificationService::class.java).apply {
                action = ACTION_UPDATE
                putExtra(EXTRA_PERCENT, percent)
                putExtra(EXTRA_TEXT, text)
            }
            try { context.startService(i) } catch (_: Throwable) {}
        }

        fun start(context: Context) {
            val i = Intent(context, UpscaleNotificationService::class.java).apply {
                action = ACTION_START
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(i)
            } else context.startService(i)
        }

        fun stop(context: Context) {
            val i = Intent(context, UpscaleNotificationService::class.java).apply {
                action = ACTION_STOP
            }
            context.startService(i)
        }
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_START -> {
                val notif = NotificationCompat.Builder(this, NotificationHelper.CHANNEL_UPSCALE)
                    .setSmallIcon(R.drawable.ic_notification)
                    .setContentTitle(getString(R.string.notif_upscale_title))
                    .setContentText(getString(R.string.notif_upscale_preparing))
                    .setOngoing(true).setSilent(true)
                    .setPriority(NotificationCompat.PRIORITY_LOW)
                    .setColor(getColor(R.color.ios_blue))
                    .build()
                startForeground(NotificationHelper.NOTIF_ID_UPSCALE, notif)
            }
            ACTION_UPDATE -> {
                val pct = intent.getIntExtra(EXTRA_PERCENT, 0)
                val txt = intent.getStringExtra(EXTRA_TEXT) ?: getString(R.string.notif_upscale_preparing)
                NotificationHelper.updateProgressFromService(this, pct, txt)
            }
            ACTION_STOP -> {
                stopForeground(STOP_FOREGROUND_REMOVE)
                stopSelf()
            }
        }
        return START_NOT_STICKY
    }
}

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
            ACTION_STOP -> {
                stopForeground(STOP_FOREGROUND_REMOVE)
                stopSelf()
            }
        }
        return START_NOT_STICKY
    }
}

package com.arthexdev.exups.util

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.arthexdev.exups.R
import com.arthexdev.exups.ui.main.MainActivity

object NotificationHelper {
    const val CHANNEL_UPSCALE = "exups_upscale_v1"
    const val CHANNEL_DOWNLOAD = "exups_download_v1"
    const val NOTIF_ID_UPSCALE = 1001
    const val NOTIF_ID_DOWNLOAD = 1002

    fun createChannels(context: Context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

        val upscale = NotificationChannel(
            CHANNEL_UPSCALE,
            context.getString(R.string.notif_channel_name),
            NotificationManager.IMPORTANCE_LOW
        ).apply {
            description = context.getString(R.string.notif_channel_desc)
            setShowBadge(false)
            enableLights(false)
            enableVibration(false)
        }

        val download = NotificationChannel(
            CHANNEL_DOWNLOAD,
            context.getString(R.string.notif_download_channel_name),
            NotificationManager.IMPORTANCE_LOW
        ).apply {
            description = context.getString(R.string.notif_download_channel_desc)
            setShowBadge(false)
            enableLights(false)
            enableVibration(false)
        }

        nm.createNotificationChannel(upscale)
        nm.createNotificationChannel(download)
    }

    private fun contentIntent(context: Context): PendingIntent {
        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        return PendingIntent.getActivity(
            context, 0, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }

    private fun base(context: Context, channelId: String, title: String, text: String) =
        NotificationCompat.Builder(context, channelId)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(title)
            .setContentText(text)
            .setContentIntent(contentIntent(context))
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setCategory(NotificationCompat.CATEGORY_PROGRESS)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setColor(context.getColor(R.color.ios_blue))
            .setSilent(true)

    fun showUpscaleProgress(context: Context, percent: Int, detail: String) {
        val text = if (percent <= 0) context.getString(R.string.notif_upscale_preparing)
        else context.getString(R.string.notif_upscale_processing, percent)

        val notif = base(context, CHANNEL_UPSCALE,
            context.getString(R.string.notif_upscale_title), text)
            .setProgress(100, percent.coerceIn(0, 100), percent <= 0)
            .setSubText(detail.take(40))
            .build()
        try { NotificationManagerCompat.from(context).notify(NOTIF_ID_UPSCALE, notif) } catch (_: Throwable) {}
    }

    fun showUpscaleDone(context: Context, w: Int, h: Int) {
        val notif = base(context, CHANNEL_UPSCALE,
            context.getString(R.string.notif_upscale_title),
            context.getString(R.string.notif_upscale_done, w, h))
            .setOngoing(false).setAutoCancel(true)
            .setProgress(0, 0, false)
            .build()
        try { NotificationManagerCompat.from(context).notify(NOTIF_ID_UPSCALE, notif) } catch (_: Throwable) {}
    }

    fun showDownloadProgress(context: Context, percent: Int, doneMb: Long, totalMb: Long) {
        val notif = base(context, CHANNEL_DOWNLOAD,
            context.getString(R.string.notif_download_title),
            context.getString(R.string.notif_download_progress, percent, doneMb, totalMb))
            .setProgress(100, percent.coerceIn(0, 100), percent <= 0)
            .build()
        try { NotificationManagerCompat.from(context).notify(NOTIF_ID_DOWNLOAD, notif) } catch (_: Throwable) {}
    }

    fun cancelUpscale(context: Context) {
        try { NotificationManagerCompat.from(context).cancel(NOTIF_ID_UPSCALE) } catch (_: Throwable) {}
    }
    fun cancelDownload(context: Context) {
        try { NotificationManagerCompat.from(context).cancel(NOTIF_ID_DOWNLOAD) } catch (_: Throwable) {}
    }

    /**
     * Update progress dari foreground service (dipanggil dari service).
     */
    fun updateProgressFromService(context: Context, percent: Int, text: String) {
        val notif = base(context, CHANNEL_UPSCALE,
            context.getString(R.string.notif_upscale_title), text)
            .setProgress(100, percent.coerceIn(0, 100), false)
            .build()
        try {
            NotificationManagerCompat.from(context).notify(NOTIF_ID_UPSCALE, notif)
        } catch (_: Throwable) {}
    }
}

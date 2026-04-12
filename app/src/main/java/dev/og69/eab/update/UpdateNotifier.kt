package dev.og69.eab.update

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import dev.og69.eab.MainActivity
import dev.og69.eab.R

object UpdateNotifier {

    const val CHANNEL_ID = "together_updates"
    private const val NOTIF_ID_AVAILABLE = 7101

    fun ensureChannel(context: Context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val mgr = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        val ch = NotificationChannel(
            CHANNEL_ID,
            context.getString(R.string.update_channel_name),
            NotificationManager.IMPORTANCE_DEFAULT,
        ).apply {
            description = context.getString(R.string.update_channel_desc)
        }
        mgr.createNotificationChannel(ch)
    }

    fun showUpdateAvailable(context: Context, info: ReleaseInfo) {
        if (!NotificationManagerCompat.from(context).areNotificationsEnabled()) return
        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
            putExtra(UpdateIntentExtras.OPEN_UPDATE, true)
            putExtra(UpdateIntentExtras.TAG_NAME, info.tagName)
            putExtra(UpdateIntentExtras.APK_URL, info.apkDownloadUrl)
            putExtra(UpdateIntentExtras.APK_SIZE, info.apkSizeBytes)
        }
        val pi = PendingIntent.getActivity(
            context,
            0,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val notif = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.stat_sys_download_done)
            .setContentTitle(context.getString(R.string.update_notif_title))
            .setContentText(
                context.getString(R.string.update_notif_text, info.normalizedVersion),
            )
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setContentIntent(pi)
            .setAutoCancel(true)
            .build()
        NotificationManagerCompat.from(context).notify(NOTIF_ID_AVAILABLE, notif)
    }

    fun showDownloadProgress(context: Context, progress: Int, max: Int) {
        val notif = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.stat_sys_download)
            .setContentTitle(context.getString(R.string.update_downloading_title))
            .setProgress(max, progress, max == 0)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .build()
        NotificationManagerCompat.from(context).notify(NOTIF_ID_AVAILABLE + 1, notif)
    }

    fun cancelDownloadProgress(context: Context) {
        NotificationManagerCompat.from(context).cancel(NOTIF_ID_AVAILABLE + 1)
    }
}

package com.icespiritai.offline.updater.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationCompat
import com.icespiritai.offline.IceSpiritVisionActivity
import com.icespiritai.offline.R
import com.icespiritai.offline.updater.DownloadRecord
import com.icespiritai.offline.updater.UpdateDownloadActions

class UpdateDownloadNotifier(private val context: Context) {

    init { ensureChannels() }

    private fun ensureChannels() {
        val nm = context.getSystemService(NotificationManager::class.java) ?: return
        listOf(
            NotificationChannel(UpdateDownloadActions.CHANNEL_ONGOING,
                context.getString(R.string.update_channel_ongoing), NotificationManager.IMPORTANCE_LOW),
            NotificationChannel(UpdateDownloadActions.CHANNEL_READY,
                context.getString(R.string.update_channel_ready), NotificationManager.IMPORTANCE_DEFAULT),
            NotificationChannel(UpdateDownloadActions.CHANNEL_FAILED,
                context.getString(R.string.update_channel_failed), NotificationManager.IMPORTANCE_DEFAULT),
        ).forEach { nm.createNotificationChannel(it) }
    }

    private fun notifId(record: DownloadRecord) = 0xF001 + record.downloadId.hashCode()

    fun buildProgressNotification(record: DownloadRecord, written: Long): Notification {
        val pct = if (record.totalBytes > 0) (written * 100 / record.totalBytes).toInt() else 0
        val cancelPi = PendingIntent.getService(
            context, notifId(record),
            Intent(UpdateDownloadActions.ACTION_CANCEL).setClass(context, UpdateDownloadService::class.java)
                .putExtra(UpdateDownloadActions.EXTRA_DOWNLOAD_ID, record.downloadId),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        return NotificationCompat.Builder(context, UpdateDownloadActions.CHANNEL_ONGOING)
            .setSmallIcon(R.drawable.ic_stat_download)
            .setContentTitle(context.getString(R.string.update_notif_title))
            .setContentText(context.getString(R.string.update_notif_progress,
                written / 1e6f, record.totalBytes / 1e6f, pct))
            .setProgress(record.totalBytes.toInt(), written.toInt(), false)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .addAction(R.drawable.ic_cancel, context.getString(R.string.update_cancel), cancelPi)
            .build()
    }

    fun buildReadyNotification(record: DownloadRecord, versionName: String): Notification {
        val installPi = PendingIntent.getActivity(
            context, notifId(record),
            Intent(UpdateDownloadActions.ACTION_INSTALL).setClass(context, IceSpiritVisionActivity::class.java)
                .putExtra(UpdateDownloadActions.EXTRA_DOWNLOAD_ID, record.downloadId),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val laterPi = PendingIntent.getActivity(
            context, notifId(record) + 1,
            Intent(UpdateDownloadActions.ACTION_LATER).setClass(context, IceSpiritVisionActivity::class.java)
                .putExtra(UpdateDownloadActions.EXTRA_DOWNLOAD_ID, record.downloadId),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        return NotificationCompat.Builder(context, UpdateDownloadActions.CHANNEL_READY)
            .setSmallIcon(R.drawable.ic_stat_download_ready)
            .setContentTitle(context.getString(R.string.update_notif_ready_title, versionName))
            .setContentText(context.getString(R.string.update_notif_ready_body, versionName))
            .setAutoCancel(true)
            .addAction(R.drawable.ic_install, context.getString(R.string.update_install), installPi)
            .addAction(R.drawable.ic_later, context.getString(R.string.update_later), laterPi)
            .build()
    }
}

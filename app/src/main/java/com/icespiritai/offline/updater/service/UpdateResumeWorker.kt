package com.icespiritai.offline.updater.service

import android.app.ForegroundServiceStartNotAllowedException
import android.content.Context
import android.content.Intent
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.icespiritai.offline.updater.UpdateDownloadActions

class UpdateResumeWorker(
    appContext: Context,
    params: WorkerParameters,
) : CoroutineWorker(appContext, params) {

    override suspend fun doWork(): Result {
        val downloadId = inputData.getString(KEY_DOWNLOAD_ID) ?: return Result.failure()
        val intent = Intent(UpdateDownloadActions.ACTION_DOWNLOAD).apply {
            setClass(applicationContext, UpdateDownloadService::class.java)
            putExtra(UpdateDownloadActions.EXTRA_DOWNLOAD_ID, downloadId)
            // Resume-only carry: the Service reconstructs URL / dest / cert
            // from the persisted DataStore record (see handleDownload).
            putExtra(UpdateDownloadActions.EXTRA_RESUME, true)
        }
        return try {
            applicationContext.startForegroundService(intent)
            Result.success()
        } catch (e: ForegroundServiceStartNotAllowedException) {
            // Android 12+ (API 31): a FGS cannot start while the app is in the
            // background. Most cold-start resumes happen when the user reopens
            // the app (foreground activity present, so this never fires). If the
            // process was woken by the system in the background, retry so
            // WorkManager re-runs once the app is next in the foreground.
            Result.retry()
        }
    }

    companion object {
        const val KEY_DOWNLOAD_ID = "downloadId"
    }
}

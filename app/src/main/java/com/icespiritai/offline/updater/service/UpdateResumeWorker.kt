package com.icespiritai.offline.updater.service

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
            putExtra(UpdateDownloadActions.EXTRA_RESUME, true)
        }
        applicationContext.startForegroundService(intent)
        return Result.success()
    }

    companion object {
        const val KEY_DOWNLOAD_ID = "downloadId"
    }
}

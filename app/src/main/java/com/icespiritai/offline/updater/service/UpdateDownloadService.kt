package com.icespiritai.offline.updater.service

import android.app.NotificationManager
import android.app.Service
import android.content.Intent
import android.os.IBinder
import com.icespiritai.offline.AppGraph
import com.icespiritai.offline.updater.ApkDownloader
import com.icespiritai.offline.updater.ApkSignatureVerifier
import com.icespiritai.offline.updater.DownloadRecord
import com.icespiritai.offline.updater.UpdateCheckResult.Failed.DownloadInterrupted
import com.icespiritai.offline.updater.DownloadStateStore
import com.icespiritai.offline.updater.FetchOutcome
import com.icespiritai.offline.updater.FetchResult
import com.icespiritai.offline.updater.UpdateDownloadActions
import com.icespiritai.offline.updater.UpdateRepository
import com.icespiritai.offline.updater.VerifierResult
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import java.io.File
import java.net.HttpURLConnection
import java.net.URL

class UpdateDownloadService : Service() {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private lateinit var notifier: UpdateDownloadNotifier
    private lateinit var stateStore: DownloadStateStore
    private val inFlight = mutableSetOf<String>()

    override fun onCreate() {
        super.onCreate()
        notifier = UpdateDownloadNotifier(this)
        stateStore = DownloadStateStore(AppGraph.dataStore(this))
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        intent ?: return START_NOT_STICKY
        when (intent.action) {
            UpdateDownloadActions.ACTION_DOWNLOAD -> handleDownload(intent)
            UpdateDownloadActions.ACTION_CANCEL -> handleCancel(intent)
        }
        return START_NOT_STICKY
    }

    private fun handleDownload(intent: Intent) {
        val id = intent.getStringExtra(UpdateDownloadActions.EXTRA_DOWNLOAD_ID) ?: return
        if (!inFlight.add(id)) return  // idempotent: same id already running

        val url = intent.getStringExtra(UpdateDownloadActions.EXTRA_URL) ?: return
        val destPath = intent.getStringExtra(UpdateDownloadActions.EXTRA_DEST_PATH) ?: return
        val certSha = intent.getStringExtra(UpdateDownloadActions.EXTRA_SIGNER_CERT_SHA256) ?: return
        val versionName = intent.getStringExtra(UpdateDownloadActions.EXTRA_VERSION_NAME) ?: ""
        val resume = intent.getBooleanExtra(UpdateDownloadActions.EXTRA_RESUME, false)

        scope.launch {
            val record = DownloadRecord(
                downloadId = id, url = url, destPath = destPath,
                bytesWritten = 0, totalBytes = 0, etag = null,
                signerCertSha256 = certSha,
                stage = DownloadRecord.DownloadStage.Downloading,
                versionName = versionName, startedAtEpochMs = System.currentTimeMillis(),
            )
            val existing = stateStore.get(id)
            val effective = if (existing != null) existing else record.also { stateStore.upsert(it) }

            // startForeground must be called within 5 seconds
            val initialNotif = notifier.buildProgressNotification(effective, written = effective.bytesWritten)
            startForeground(notifIdFor(effective), initialNotif)

            val resumeFrom = if (resume && effective.bytesWritten > 0 &&
                File(effective.destPath).length() == effective.bytesWritten) effective.bytesWritten else null

            if (resumeFrom == null && effective.bytesWritten > 0) {
                File(effective.destPath).delete()
                stateStore.upsert(effective.copy(bytesWritten = 0))
            }

            runDownload(effective, resumeFrom)
        }
    }

    private suspend fun runDownload(record: DownloadRecord, resumeFrom: Long?) {
        var attempt = 0
        var resumeOffset = resumeFrom
        var lastEtag = record.etag
        var lastNotifUpdate = 0L

        while (true) {
            val outcome = ApkDownloader.fetch(
                openConnection = { URL(record.url).openConnection() as HttpURLConnection },
                destFile = File(record.destPath),
                resumeFrom = resumeOffset,
                etag = lastEtag,
                onProgress = { written ->
                    val now = System.currentTimeMillis()
                    if (now - lastNotifUpdate >= 500) {
                        notifier.buildProgressNotification(record, written).also {
                            val nm = getSystemService(NotificationManager::class.java)
                            nm?.notify(notifIdFor(record), it)
                        }
                        lastNotifUpdate = now
                    }
                    scope.launch { stateStore.upsert(record.copy(bytesWritten = written)) }
                },
            )

            when (outcome) {
                is FetchOutcome.Success -> {
                    onDownloadComplete(record.copy(
                        bytesWritten = outcome.result.bytesWritten,
                        totalBytes = outcome.result.totalBytes,
                        etag = outcome.result.etag,
                    ), outcome.result)
                    return
                }
                is FetchOutcome.Retryable -> {
                    attempt += 1
                    if (attempt >= 3) {
                        UpdateRepository.onDownloadFailed(record, DownloadInterrupted.NetworkUnreachable(outcome.cause))
                        cleanup(record)
                        return
                    }
                    val backoffMs = 2000L * (1L shl (attempt - 1))  // 2/4/8 s
                    delay(backoffMs)
                    // reset resume offset if previous fetch deleted partial (200 instead of 206)
                    resumeOffset = null
                }
                is FetchOutcome.Fatal -> {
                    UpdateRepository.onDownloadFailed(record, DownloadInterrupted.Other(outcome.cause))
                    cleanup(record)
                    return
                }
            }
        }
    }

    private suspend fun onDownloadComplete(record: DownloadRecord, result: FetchResult) {
        stateStore.upsert(record.copy(stage = DownloadRecord.DownloadStage.VerifyingSignature))
        val verifierResult = ApkSignatureVerifier.verify(File(record.destPath), record.signerCertSha256)
        when (verifierResult) {
            is VerifierResult.Match -> {
                stateStore.upsert(record.copy(stage = DownloadRecord.DownloadStage.ReadyToInstall))
                notifier.buildReadyNotification(record, record.versionName).also {
                    getSystemService(NotificationManager::class.java)?.notify(notifIdFor(record), it)
                }
                stopForeground(STOP_FOREGROUND_REMOVE)
                stopSelf()
                UpdateRepository.onDownloadVerified(record, verifierResult, File(record.destPath))
                inFlight.remove(record.downloadId)
            }
            is VerifierResult.Mismatch -> {
                File(record.destPath).delete()
                stateStore.delete(record.downloadId)
                UpdateRepository.onDownloadVerified(record, verifierResult, File(record.destPath))
                stopForeground(STOP_FOREGROUND_REMOVE)
                stopSelf()
                inFlight.remove(record.downloadId)
            }
        }
    }

    private fun handleCancel(intent: Intent) {
        val id = intent.getStringExtra(UpdateDownloadActions.EXTRA_DOWNLOAD_ID) ?: return
        scope.launch {
            val record = stateStore.get(id) ?: return@launch
            cleanup(record)
            UpdateRepository.onDownloadCancelled(record)
            stopForeground(STOP_FOREGROUND_REMOVE)
            stopSelf()
        }
    }

    private fun cleanup(record: DownloadRecord) {
        runCatching { File(record.destPath).delete() }
        runCatching { runBlocking { stateStore.delete(record.downloadId) } }
        inFlight.remove(record.downloadId)
    }

    private fun notifIdFor(record: DownloadRecord) = 0xF001 + record.downloadId.hashCode()

    override fun onDestroy() {
        scope.cancel()
        super.onDestroy()
    }

    companion object { private const val TAG = "UpdateDownloadService" }
}
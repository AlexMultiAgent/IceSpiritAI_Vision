package com.icespiritai.offline.updater.service

import android.app.NotificationManager
import android.app.Service
import android.content.Intent
import android.os.IBinder
import com.icespiritai.offline.BuildConfig
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
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.util.concurrent.ConcurrentHashMap

class UpdateDownloadService : Service() {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private lateinit var notifier: UpdateDownloadNotifier
    private lateinit var stateStore: DownloadStateStore
    // ConcurrentHashMap-backed set: onStartCommand (main thread) and the IO
    // coroutines (onDownloadComplete / handleCancel / cleanup) mutate this from
    // different threads. A plain mutableSetOf is not thread-safe.
    private val inFlight: MutableSet<String> = ConcurrentHashMap.newKeySet()

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
        val url = intent.getStringExtra(UpdateDownloadActions.EXTRA_URL)
        val destPath = intent.getStringExtra(UpdateDownloadActions.EXTRA_DEST_PATH)
        val certSha = intent.getStringExtra(UpdateDownloadActions.EXTRA_SIGNER_CERT_SHA256)
        val versionName = intent.getStringExtra(UpdateDownloadActions.EXTRA_VERSION_NAME) ?: ""
        val resume = intent.getBooleanExtra(UpdateDownloadActions.EXTRA_RESUME, false)

        // The WorkManager resume path (UpdateResumeWorker) sends only
        // downloadId + resume=true. Reconstruct URL / dest / cert from the
        // persisted DataStore record so the download actually resumes —
        // otherwise the service would bail out here and resume silently fails.
        if (!inFlight.add(id)) return  // idempotent: same id already running

        scope.launch {
            val persisted = stateStore.get(id)
            val freshMetadataMissing = url == null || destPath == null || certSha == null
            if (persisted == null && freshMetadataMissing) {
                // Nothing to resume and no fresh metadata: drop the phantom id
                // so it is not permanently stuck for this service lifetime.
                inFlight.remove(id)
                return@launch
            }

            val effUrl = url ?: persisted?.url
            val effDest = destPath ?: persisted?.destPath
            // Client-side cert pin is authoritative; the JSON/extra value is
            // only a fallback for builds where no pin was compiled in.
            val effCert = BuildConfig.UPDATE_EXPECTED_CERT_SHA256.ifBlank {
                certSha ?: persisted?.signerCertSha256 ?: ""
            }
            if (effUrl == null || effDest == null) {
                inFlight.remove(id)
                return@launch
            }

            val record = DownloadRecord(
                downloadId = id, url = effUrl, destPath = effDest,
                bytesWritten = persisted?.bytesWritten ?: 0,
                totalBytes = persisted?.totalBytes ?: 0,
                etag = persisted?.etag,
                signerCertSha256 = effCert,
                stage = persisted?.stage ?: DownloadRecord.DownloadStage.Downloading,
                versionName = versionName.ifEmpty { persisted?.versionName ?: "" },
                startedAtEpochMs = persisted?.startedAtEpochMs ?: System.currentTimeMillis(),
            )
            val effective = persisted ?: record.also { stateStore.upsert(it) }

            // startForeground must be called within 5 seconds
            val initialNotif = notifier.buildProgressNotification(effective, written = effective.bytesWritten)
            startForeground(notifIdFor(effective), initialNotif)

            val resumeFrom = if (resume && effective.bytesWritten > 0 &&
                File(effective.destPath).length() == effective.bytesWritten) effective.bytesWritten else null

            if (resumeFrom == null && effective.bytesWritten > 0) {
                File(effective.destPath).delete()
                stateStore.upsert(effective.copy(bytesWritten = 0))
            }

            // Publish the live Downloading transition BEFORE the byte stream starts,
            // so any observing UI (or a freshly-resumed SettingsViewModel that has
            // no cached `lastDownloadInfo`) can extract the downloadId directly from
            // the StateFlow for its cancel path. totalBytes is best-effort at this
            // point — for a fresh download it's 0 until the first progress callback
            // lands; for a resume it's the persisted value, but we use the on-disk
            // resumeFrom offset which is the authoritative byte count. The same
            // callback is then invoked on every 500 ms tick from runDownload's
            // onProgress lambda so the UI progress bar advances in real time.
            UpdateRepository.onDownloadProgress(
                downloadId = effective.downloadId,
                written = resumeFrom ?: 0L,
                total = effective.totalBytes,
            )

            runDownload(effective, resumeFrom)
        }
    }

    private suspend fun runDownload(record: DownloadRecord, resumeFrom: Long?) {
        var attempt = 0
        var resumeOffset = resumeFrom
        var lastEtag = record.etag
        var lastNotifUpdate = 0L
        var lastStateUpdate = 0L

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
                    // Push live progress to the StateFlow at the same 500 ms cadence
                    // so UpdateSection's progress bar advances (the bar reads from
                    // UpdateRepository.state, not from DataStore). Decoupled from
                    // lastNotifUpdate: if either side lags the other won't stall.
                    if (now - lastStateUpdate >= 500) {
                        UpdateRepository.onDownloadProgress(
                            downloadId = record.downloadId,
                            written = written,
                            total = record.totalBytes,
                        )
                        lastStateUpdate = now
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
                UpdateRepository.onDownloadVerified(record, verifierResult)
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

    private suspend fun cleanup(record: DownloadRecord) {
        runCatching { File(record.destPath).delete() }
        try { stateStore.delete(record.downloadId) } catch (_: Exception) {}
        inFlight.remove(record.downloadId)
    }

    private fun notifIdFor(record: DownloadRecord) = 0xF001 + record.downloadId.hashCode()

    override fun onDestroy() {
        scope.cancel()
        super.onDestroy()
    }

    companion object { private const val TAG = "UpdateDownloadService" }
}

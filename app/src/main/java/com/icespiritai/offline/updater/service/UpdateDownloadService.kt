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
    // Tracks active downloads by id → destPath. `onStartCommand` (main thread)
    // and the IO coroutines (`onDownloadComplete` / `handleCancel` / `cleanup`)
    // mutate this from different threads — a plain mutableMapOf is not
    // thread-safe. The destPath denormalization lets [onDestroy] wipe
    // partially-downloaded APKs without a suspending DataStore read (see
    // KDoc there for why we don't go through stateStore on teardown).
    private val inFlight: MutableMap<String, String> = ConcurrentHashMap()

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
        // Atomic check-or-insert; second start with the same id is a no-op.
        val effectiveDest = destPath ?: ""
        if (inFlight.putIfAbsent(id, effectiveDest) != null) return

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

            // Update the destPath in the inFlight map now that we've resolved
            // any persisted fallback — [onDestroy] uses it to wipe partial
            // APKs without a suspending DataStore read.
            inFlight[id] = effDest

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
        // Live, mutable copy of [record] shared between onMetadata (fires once
        // per fetch, when Content-Length is known) and onProgress (every 500
        // ms). The initial [record] has totalBytes=0 for a fresh download and
        // only gets the real value from onMetadata — without a mutable copy,
        // onProgress would publish `total=0` for the entire download and the
        // UI's `progress = written/total` would stay at 0%.
        var liveRecord = record

        while (true) {
            val outcome = ApkDownloader.fetch(
                openConnection = { URL(record.url).openConnection() as HttpURLConnection },
                destFile = File(record.destPath),
                resumeFrom = resumeOffset,
                etag = lastEtag,
                onProgress = { written ->
                    liveRecord = liveRecord.copy(bytesWritten = written)
                    val now = System.currentTimeMillis()
                    if (now - lastNotifUpdate >= 500) {
                        notifier.buildProgressNotification(liveRecord, written).also {
                            val nm = getSystemService(NotificationManager::class.java)
                            nm?.notify(notifIdFor(liveRecord), it)
                        }
                        lastNotifUpdate = now
                    }
                    // Push live progress to the StateFlow + DataStore at the same
                    // 500 ms cadence. Both reads serve the same purpose — UI
                    // progress bar (StateFlow) + resume-after-process-death
                    // recovery (DataStore). The DataStore write previously ran
                    // on every onProgress tick (unbounded — ApkDownloader's
                    // chunk size is typically 16-64 KB, so a 70 MB APK produced
                    // 1000-4000+ DataStore.edit coroutines per second). That
                    // saturated DataStore's serializer mutex and stalled the
                    // subsequent onDownloadComplete `stateStore.upsert(Verifying)`
                    // write, sometimes by minutes. Throttling to the same 500 ms
                    // gate as the StateFlow keeps the two views in lockstep and
                    // caps outstanding writes at ~2 per second.
                    if (now - lastStateUpdate >= 500) {
                        UpdateRepository.onDownloadProgress(
                            downloadId = liveRecord.downloadId,
                            written = written,
                            total = liveRecord.totalBytes,
                        )
                        scope.launch { stateStore.upsert(liveRecord.copy(bytesWritten = written)) }
                        lastStateUpdate = now
                    }
                },
                // onMetadata fires once per fetch when Content-Length is known
                // (before the first body byte). Pushes the real total to both
                // the StateFlow (so the UI bar stops showing indeterminate) and
                // DataStore (so a subsequent process death + resume preserves
                // it for the on-disk progress mirror).
                onMetadata = { total ->
                    liveRecord = liveRecord.copy(totalBytes = total)
                    UpdateRepository.onDownloadProgress(
                        downloadId = liveRecord.downloadId,
                        written = liveRecord.bytesWritten,
                        total = total,
                    )
                    scope.launch { stateStore.upsert(liveRecord.copy(totalBytes = total)) }
                },
            )

            when (outcome) {
                is FetchOutcome.Success -> {
                    val finalRecord = liveRecord.copy(
                        bytesWritten = outcome.result.bytesWritten,
                        totalBytes = outcome.result.totalBytes,
                        etag = outcome.result.etag,
                    )
                    onDownloadComplete(finalRecord, outcome.result)
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
        // Cancel the in-flight scope first so any runDownload coroutines
        // suspended on the IO pool are torn down (the ApkDownloader
        // HttpURLConnection streams observe cancellation at the next read).
        // Then sweep the `inFlight` map: every id that didn't reach
        // `inFlight.remove(...)` in its completion / cancel path represents
        // a partially-downloaded APK on disk. We delete the partial file so
        // a later session's UpdateResumeWorker doesn't try to resume from
        // bytes that never made it to the file (DataStore says "60% written"
        // but the file is shorter — handleDownload() will detect the
        // mismatch and reset bytesWritten to 0 for a fresh start).
        //
        // We deliberately leave the DataStore record alone here:
        //   1. stateStore.delete is `suspend`, onDestroy can't suspend
        //      without blocking the main thread (no `runBlocking` policy on
        //      Service teardown).
        //   2. The orphan record is harmless — handleDownload's
        //      `resumeFrom = null` branch (mismatched file length) wipes
        //      bytesWritten + deletes the file again. So no double-spend,
        //      just an extra file system stat on next resume.
        //   3. `scope.cancel()` before the sweep ensures no in-flight
        //      upsert re-inserts after a delete.
        scope.cancel()
        for ((id, destPath) in inFlight.toMap()) {
            if (destPath.isNotBlank()) {
                runCatching { File(destPath).delete() }
            }
            inFlight.remove(id)
        }
        super.onDestroy()
    }

    companion object { private const val TAG = "UpdateDownloadService" }
}

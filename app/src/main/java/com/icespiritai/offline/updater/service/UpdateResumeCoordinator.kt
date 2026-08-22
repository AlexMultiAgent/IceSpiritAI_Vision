package com.icespiritai.offline.updater.service

import android.content.Context
import com.icespiritai.offline.updater.ApkSignatureVerifier
import com.icespiritai.offline.updater.DownloadRecord
import com.icespiritai.offline.updater.DownloadStateStore
import com.icespiritai.offline.updater.VerifierResult
import java.io.File

/**
 * Scans [DownloadStateStore] on cold start (typically invoked from
 * `Application.onCreate`) and dispatches every persisted record to the
 * appropriate sink based on its stage + on-disk file size.
 *
 * Dispatch table:
 *  - [DownloadRecord.DownloadStage.ReadyToInstall]    + `file.length() == totalBytes` → [readyToInstallSink]
 *  - [DownloadRecord.DownloadStage.VerifyingSignature] + `file.length() == totalBytes` → [verifier.verify] → [verifierResultSink]
 *  - [DownloadRecord.DownloadStage.Downloading]       + `bytesWritten > 0` + `file.length() == bytesWritten` → [resumeWorkerLauncher]
 *  - Otherwise (stage/size mismatch, missing file, zero-byte partial) → delete file + delete record (stale cleanup)
 *
 * The class is **stateless** beyond its constructor collaborators and is
 * safe to construct once per process. [scanAndDispatch] suspends (rather
 * than firing-and-forgetting on an internal scope) so callers can drive
 * it from any coroutine context — production callers should use
 * [kotlinx.coroutines.Dispatchers.IO]; tests call it inside
 * [kotlinx.coroutines.runBlocking] and naturally await completion.
 *
 * The [context] field is currently unused by the routing logic itself
 * (the coordinator only touches the file system + DataStore), but it is
 * kept in the constructor so that future implementations (e.g. posting a
 * notification on [failedSink]) can use it without changing call sites.
 */
class UpdateResumeCoordinator(
    @Suppress("unused") private val context: Context,
    private val stateStore: DownloadStateStore,
    private val verifier: ApkSignatureVerifierType,
    private val verifierResultSink: (VerifierResult) -> Unit,
    private val resumeWorkerLauncher: (downloadId: String) -> Unit,
    private val readyToInstallSink: (File, String) -> Unit,
    private val failedSink: (Throwable) -> Unit,
) {

    /**
     * Strategy hook for the v1 signer-cert fingerprint check. Production
     * wiring passes a SAM-converted reference to
     * [ApkSignatureVerifier.verify]; tests can pass a stub that returns
     * deterministic [VerifierResult] values.
     */
    fun interface ApkSignatureVerifierType {
        fun verify(file: File, expectedCertSha256: String): VerifierResult
    }

    /**
     * Reads every record in [stateStore] and routes each to the sink that
     * matches its current stage + on-disk size. Records that fail to
     * match any of the dispatch branches (stale: wrong stage, missing
     * file, partial size mismatch, zero-byte partial) have both their
     * on-disk file and their DataStore entry removed. [failedSink] is
     * invoked for any per-record exception so that a single corrupt
     * record cannot prevent subsequent records from being routed.
     */
    suspend fun scanAndDispatch() {
        val records = stateStore.all()
        records.forEach { record ->
            try {
                dispatchOne(record)
            } catch (t: Throwable) {
                failedSink(t)
            }
        }
    }

    /**
     * Routes a single record. Split into routing (sync) + cleanup (suspend)
     * so the routing decision itself does not need to suspend; the cleanup
     * path is only entered when stale and the suspending [stateStore.delete]
     * is then awaited in the calling coroutine.
     */
    private suspend fun dispatchOne(record: DownloadRecord) {
        val file = File(record.destPath)
        val routed = when {
            record.stage == DownloadRecord.DownloadStage.ReadyToInstall
                && file.length() == record.totalBytes -> {
                readyToInstallSink(file, record.versionName)
                true
            }
            record.stage == DownloadRecord.DownloadStage.VerifyingSignature
                && file.length() == record.totalBytes -> {
                val r = verifier.verify(file, record.signerCertSha256)
                verifierResultSink(r)
                true
            }
            record.stage == DownloadRecord.DownloadStage.Downloading
                && record.bytesWritten > 0
                && file.length() == record.bytesWritten -> {
                resumeWorkerLauncher(record.downloadId)
                true
            }
            else -> false
        }
        if (!routed) {
            // Stale: stage/size mismatch, missing file, or zero-byte partial.
            // delete() swallows ENOENT; we only need to guard against the
            // case where delete() throws for some other reason (e.g. EBUSY
            // on a held file), in which case the store delete below is
            // still worth attempting so the DataStore doesn't keep
            // re-routing the same broken record on every cold start.
            runCatching { file.delete() }
            stateStore.delete(record.downloadId)
        }
    }
}
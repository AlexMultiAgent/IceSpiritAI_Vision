package com.icespiritai.offline

import android.app.Application
import androidx.work.Constraints
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.workDataOf
import com.icespiritai.offline.updater.ApkSignatureVerifier
import com.icespiritai.offline.updater.UpdateRepository
import com.icespiritai.offline.updater.service.UpdateDownloadNotifier
import com.icespiritai.offline.updater.service.UpdateResumeCoordinator
import com.icespiritai.offline.updater.service.UpdateResumeWorker
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/**
 * Process-wide bootstrap. Runs once per process at cold start, before any
 * Activity / Service / Worker. Responsibilities:
 *
 *  1. Ensure the FGS notification channels exist before any user-launched
 *     notification has a chance to fire. [UpdateDownloadService.onCreate]
 *     also constructs the notifier, but the Service only runs after the
 *     Activity has dispatched a download Intent — by then a system-launched
 *     cold-start notification (e.g. WorkManager resume) might already have
 *     tried to land on a not-yet-created channel.
 *
 *  2. Scan [com.icespiritai.offline.updater.DownloadStateStore] for any
 *     records orphaned by an OOM-kill / force-stop / device reboot while
 *     a download was in flight. The [UpdateResumeCoordinator] routes each
 *     record to the appropriate sink:
 *
 *      - `Downloading` + partial file → enqueue [UpdateResumeWorker] which
 *        restarts [UpdateDownloadService] with `EXTRA_RESUME=true`.
 *      - `VerifyingSignature` + complete file → re-run cert-pin verifier;
 *        result is published by the Service when the live path runs (the
 *        Application sink is intentionally a no-op for now — the verifier
 *        path on cold start is rare and the next live check will surface
 *        anything stale).
 *      - `ReadyToInstall` + complete file → publish to [UpdateRepository]
 *        so the UI's UpdateSection snaps into ReadyToInstall state.
 *      - Otherwise (stage/size mismatch, zero-byte partial, missing file) →
 *        silently delete.
 */
class IceSpiritApplication : Application() {

    /**
     * Application-lifetime scope. [SupervisorJob] so a failure in the
     * scan-and-dispatch coroutine does not cancel anything else that
     * might be attached to this scope in future Tasks.
     */
    private val appScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onCreate() {
        super.onCreate()

        // 1. Notification channels first — any cold-launched notification
        //    (e.g. WorkManager firing the resume worker within seconds of
        //    Application.onCreate) needs the channel already registered.
        UpdateDownloadNotifier(this)

        // 2. Resume-coordinator cold-start scan. scanAndDispatch() is
        //    suspend; offload to appScope so Application.onCreate returns
        //    to the framework promptly.
        val coordinator = UpdateResumeCoordinator(
            stateStore = AppGraph.downloadStateStore(this),
            verifier = ApkSignatureVerifier::verify,
            // Verifier result on cold-start is intentionally not wired to a UI
            // sink: by the time the user reopens Settings the live-path
            // Service has either completed verification (publishing via
            // UpdateRepository.onDownloadVerified) or the user has navigated
            // away. The next explicit `refresh()` / `retry()` from
            // SettingsViewModel re-derives the state from scratch.
            verifierResultSink = { /* no-op: live Service path handles publishing */ },
            resumeWorkerLauncher = { id ->
                val req = OneTimeWorkRequestBuilder<UpdateResumeWorker>()
                    .setInputData(workDataOf(UpdateResumeWorker.KEY_DOWNLOAD_ID to id))
                    .setConstraints(
                        Constraints.Builder()
                            .setRequiredNetworkType(NetworkType.CONNECTED)
                            .build(),
                    )
                    .build()
                WorkManager.getInstance(this).enqueueUniqueWork(
                    "resume-$id",
                    ExistingWorkPolicy.KEEP,
                    req,
                )
            },
            readyToInstallSink = { file, versionName ->
                UpdateRepository.setReadyToInstall(file, versionName)
            },
            // Cold-start has no active UI to surface failures to; the
            // Coordinator's stale-cleanup branch (stage/size mismatch) is
            // silent by design. Real failures (network drop on resume) are
            // surfaced when the Service publishes Failed via the live path.
            failedSink = { /* silent: no active UI on cold-start */ },
        )
        appScope.launch { coordinator.scanAndDispatch() }
    }
}

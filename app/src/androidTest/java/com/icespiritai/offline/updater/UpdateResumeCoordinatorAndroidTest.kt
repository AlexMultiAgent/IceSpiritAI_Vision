package com.icespiritai.offline.updater

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.work.Constraints
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkInfo
import androidx.work.WorkManager
import androidx.work.workDataOf
import com.icespiritai.offline.AppGraph
import com.icespiritai.offline.updater.DownloadRecord.DownloadStage
import com.icespiritai.offline.updater.service.UpdateResumeCoordinator
import com.icespiritai.offline.updater.service.UpdateResumeWorker
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

/**
 * Real-device integration test for
 * [com.icespiritai.offline.updater.service.UpdateResumeCoordinator].
 *
 * This validates the **coordinator wiring** in isolation from the FGS.
 * Production wires the resume launcher from `Application.onCreate`; here
 * we pass the same lambda shape directly into the constructor so the test
 * can observe the WorkManager side-effect without standing up the whole
 * Application bootstrap.
 *
 * Scenario:
 *   1. Seed a `Downloading` record (bytesWritten=2048) + a real 2048-byte
 *      partial file. This matches the dispatch branch in
 *      `UpdateResumeCoordinator.dispatchOne`:
 *      ```
 *      stage == Downloading
 *          && bytesWritten > 0
 *          && file.length() == bytesWritten
 *          → resumeWorkerLauncher(record.downloadId)
 *      ```
 *   2. Run `coord.scanAndDispatch()` synchronously (it is `suspend`,
 *      so we wrap in `runBlocking`).
 *   3. After dispatch returns, query WorkManager for the unique work name
 *      `resume-<downloadId>` and assert at least one WorkInfo is in
 *      `ENQUEUED` or `RUNNING` state.
 *
 * Why a real device test (not Robolectric):
 *   - WorkManager auto-initializes via `androidx.startup`'s ContentProvider
 *     and uses a real JobScheduler / SystemJobService on the device.
 *     Robolectric's WorkManager shadowing does not faithfully reproduce
 *     these constraints gates.
 *   - The Coordinator itself is fully JVM-testable (no Android API surface
 *     in the dispatch path beyond `File`), so a separate unit test could
 *     cover the routing decisions. This androidTest specifically validates
 *     the WorkManager enqueue side-effect that the production wiring
 *     relies on.
 *
 * Note on the constructor: as of cleanup commit `58a9e9f` the
 * [UpdateResumeCoordinator] constructor takes SIX collaborators
 * (stateStore, verifier, verifierResultSink, resumeWorkerLauncher,
 * readyToInstallSink, failedSink). The earlier plan draft referenced an
 * older 7-param shape; do not reintroduce a `context` parameter here.
 */
@RunWith(AndroidJUnit4::class)
class UpdateResumeCoordinatorAndroidTest {

    @Test
    fun valid_downloading_partial_enqueues_resume_work() = runBlocking {
        val ctx = ApplicationProvider.getApplicationContext<Context>()
        val store = AppGraph.downloadStateStore(ctx)

        val downloadId = "d-resume-coord"
        // Match the path shape UpdateRepository.downloadApk uses:
        //   File(context.cacheDir, "update").apply { mkdirs() }
        val updateDir = File(ctx.cacheDir, "update").apply { mkdirs() }
        val partial = File(updateDir, "$downloadId.apk")
        partial.writeBytes(ByteArray(2048) { 0x33 })

        // Stash any unique-work reservations from earlier runs of this
        // class so the assertion is scoped to THIS invocation.
        val wm = WorkManager.getInstance(ctx)
        // ExistingWorkPolicy.KEEP in the launcher below would otherwise
        // short-circuit the new request if a stale ENQUEUED entry remains
        // from a previous run. Cancel first.
        runCatching { wm.cancelUniqueWork("resume-$downloadId") }
        // Allow cancelUniqueWork's enqueue to settle before we re-enqueue.
        Thread.sleep(200L)

        try {
            // 1. Seed DataStore record.
            store.upsert(
                DownloadRecord(
                    downloadId = downloadId,
                    url = "http://127.0.0.1:1/never-listens.apk",
                    destPath = partial.absolutePath,
                    bytesWritten = 2048L,
                    totalBytes = 0L,
                    etag = null,
                    signerCertSha256 = "deadbeef".repeat(8),
                    stage = DownloadStage.Downloading,
                    versionName = "0.0.0-test",
                    startedAtEpochMs = System.currentTimeMillis(),
                ),
            )
            assertNotNull(
                "seed: DataStore entry for $downloadId must be visible before dispatch",
                store.get(downloadId),
            )

            // 2. Construct the Coordinator with the same resume-worker
            //    launcher shape Application.onCreate uses. The other
            //    sinks are no-ops because we are not exercising the
            //    verifier / readyToInstall branches in this test.
            var launcherCalls = 0
            var lastLaunchedId: String? = null
            val coordinator = UpdateResumeCoordinator(
                stateStore = store,
                verifier = { _, _ ->
                    // Unreachable in this test — the record is `Downloading`,
                    // not `VerifyingSignature`. Returning a stub keeps the
                    // Coordinator's `Verifier` interface happy.
                    return@UpdateResumeCoordinator VerifierResult.Match("")
                },
                verifierResultSink = { /* no-op: not exercised */ },
                resumeWorkerLauncher = { id ->
                    launcherCalls += 1
                    lastLaunchedId = id
                    val req = OneTimeWorkRequestBuilder<UpdateResumeWorker>()
                        .setInputData(
                            workDataOf(UpdateResumeWorker.KEY_DOWNLOAD_ID to id),
                        )
                        .setConstraints(
                            Constraints.Builder()
                                .setRequiredNetworkType(NetworkType.CONNECTED)
                                .build(),
                        )
                        .build()
                    WorkManager.getInstance(ctx).enqueueUniqueWork(
                        "resume-$id",
                        ExistingWorkPolicy.KEEP,
                        req,
                    )
                },
                readyToInstallSink = { _, _ -> /* no-op */ },
                failedSink = { /* silent: per-record failure sink */ },
            )

            // 3. Synchronously run dispatch — `scanAndDispatch` is
            //    `suspend` and the per-record routing is non-suspending
            //    (the only suspend is in the stale-cleanup path).
            coordinator.scanAndDispatch()

            // 4. The launcher must have been called exactly once for
            //    our single record, with the expected downloadId.
            assertTrue(
                "resumeWorkerLauncher must be called once for the seeded " +
                    "Downloading record; got launcherCalls=$launcherCalls",
                launcherCalls == 1,
            )
            assertTrue(
                "resumeWorkerLauncher must receive the seeded downloadId; " +
                    "expected=$downloadId got=$lastLaunchedId",
                lastLaunchedId == downloadId,
            )

            // 5. WorkManager side-effect: query the unique work name and
            //    assert at least one WorkInfo was registered. Because
            //    [UpdateResumeWorker.doWork] is essentially
            //    `startForegroundService(...) ; return Result.success()`,
            //    the worker can run to completion well inside the
            //    500ms settle window on a quiet device — so we accept
            //    ENQUEUED, RUNNING, or SUCCEEDED. The relevant signal
            //    is "WorkManager accepted the unique-name reservation
            //    and the worker reached a terminal state", not which
            //    transient state we happened to observe.
            Thread.sleep(500L)
            val infos: List<WorkInfo> = wm
                .getWorkInfosForUniqueWork("resume-$downloadId")
                .get()
            assertTrue(
                "WorkManager must report at least one WorkInfo for " +
                    "resume-$downloadId; got=${infos.size} infos: $infos",
                infos.isNotEmpty(),
            )
            assertTrue(
                "at least one WorkInfo must be ENQUEUED, RUNNING, or " +
                    "SUCCEEDED after dispatch (worker is a single-step " +
                    "doWork that returns Result.success); got states=" +
                    "${infos.map { it.state }}",
                infos.any {
                    it.state == WorkInfo.State.ENQUEUED ||
                        it.state == WorkInfo.State.RUNNING ||
                        it.state == WorkInfo.State.SUCCEEDED
                },
            )
        } finally {
            // Tear down WorkManager state and seeded DataStore entry so
            // re-runs of this class start clean.
            runCatching { wm.cancelUniqueWork("resume-$downloadId") }
            runCatching { store.delete(downloadId) }
            runCatching { partial.delete() }
        }

        // Explicit Unit return: runBlocking { ... } block must terminate
        // in Unit for JUnit's "Method should be void" rule.
        Unit
    }
}
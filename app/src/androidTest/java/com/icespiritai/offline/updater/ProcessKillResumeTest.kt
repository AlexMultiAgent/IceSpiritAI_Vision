package com.icespiritai.offline.updater

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.work.WorkInfo
import androidx.work.WorkManager
import com.icespiritai.offline.AppGraph
import com.icespiritai.offline.IceSpiritApplication
import com.icespiritai.offline.updater.DownloadRecord.DownloadStage
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

/**
 * Real-device integration test for the **process-death resume** path.
 *
 * What we're testing:
 *   When the target app process is killed (force-stop, OOM, ANR, device
 *   reboot) while a download is in flight, the on-disk DataStore record
 *   + partial file survive. On the next cold start, the relaunched
 *   [com.icespiritai.offline.IceSpiritApplication.onCreate] calls
 *   [com.icespiritai.offline.updater.service.UpdateResumeCoordinator.scanAndDispatch],
 *   which scans DataStore, sees the partial Downloading record, and
 *   enqueues an [com.icespiritai.offline.updater.service.UpdateResumeWorker]
 *   under the unique work name `resume-<downloadId>`.
 *
 * Why this is structured around reflection-based onCreate invocation
 * instead of literal `am force-stop` + relaunch:
 *   - AndroidJUnitRunner co-locates with the target app's main process
 *     by design (the `android:process` attribute on `<instrumentation>`
 *     is NOT honored — verified via the merged manifest in build/
 *     intermediates/). So `am force-stop <target>` would SIGKILL the
 *     test runner too, surfacing as `Process crashed` from the test
 *     harness.
 *   - Reflection-invoking [IceSpiritApplication.onCreate] from the test
 *     simulates the "fresh cold start" code path identically: the
 *     production `Application.onCreate` registers the same channel,
 *     constructs the same Coordinator, and launches scanAndDispatch on
 *     the same IO scope. The only thing we skip is the OS's process
 *     bootstrap (which has no logic — Application.attach / LoadedApk
 *     just constructs the Application instance).
 *
 * Companion to:
 *   - [UpdateResumeCoordinatorAndroidTest] — unit-style coverage of
 *     the Coordinator's dispatchOne decision (does it pick the right
 *     sink for each stage + size combination). This test complements
 *     that by validating the **production wiring** in IceSpiritApplication
 *     calls the Coordinator with the right arguments and enqueues the
 *     resume worker through WorkManager.
 *
 * Test environment: a real device with WorkManager initialized (the
 * auto-init via androidx.startup's ContentProvider runs at process
 * start, before any test code).
 */
@RunWith(AndroidJUnit4::class)
class ProcessKillResumeTest {

    @Test
    fun cold_start_with_partial_downloading_record_enqueues_resume_work() = runBlocking {
        val ctx = ApplicationProvider.getApplicationContext<Context>()
        val store = AppGraph.downloadStateStore(ctx)
        val wm = WorkManager.getInstance(ctx)

        val downloadId = "d-process-kill"
        // Match the path shape UpdateRepository.downloadApk uses:
        //   File(context.cacheDir, "update").apply { mkdirs() }
        //   -> File(updateDir, "$downloadId.apk")
        val updateDir = File(ctx.cacheDir, "update").apply { mkdirs() }
        val partial = File(updateDir, "$downloadId.apk")
        partial.writeBytes(ByteArray(4096) { 0x55 })

        // Best-effort: cancel any leftover unique work from a prior run.
        runCatching { wm.cancelUniqueWork("resume-$downloadId") }
        Thread.sleep(200L)

        try {
            // 1. Seed DataStore record + partial file (the on-disk state
            //    that survives process death).
            store.upsert(
                DownloadRecord(
                    downloadId = downloadId,
                    url = "http://127.0.0.1:1/never-listens.apk",
                    destPath = partial.absolutePath,
                    bytesWritten = 4096L,
                    totalBytes = 0L,
                    etag = null,
                    signerCertSha256 = "deadbeef".repeat(8),
                    stage = DownloadStage.Downloading,
                    versionName = "0.0.0-test",
                    startedAtEpochMs = System.currentTimeMillis(),
                ),
            )
            assertNotNull(
                "seed: DataStore entry for $downloadId must be visible " +
                    "before the simulated cold start",
                store.get(downloadId),
            )

            // 2. Simulate the cold-start Application.onCreate. The
            //    AndroidJUnitRunner runs in the target's main process so
            //    Application.onCreate has already fired once during the
            //    test harness setup; invoking it via reflection simulates
            //    a fresh process hitting onCreate, which is the path
            //    that runs UpdateResumeCoordinator.scanAndDispatch().
            //
            //    Note: IceSpiritApplication registers the FGS notification
            //    channels and constructs the coordinator inline; calling
            //    onCreate again is idempotent for our purposes — the
            //    channels may re-register (AndroidX NotificationManagerCompat
            //    de-dupes by channel id) and the Coordinator is stateless.
            val app = ctx as IceSpiritApplication
            val onCreate = IceSpiritApplication::class.java
                .getDeclaredMethod("onCreate")
            onCreate.isAccessible = true
            onCreate.invoke(app)

            // 3. Give appScope (Dispatchers.IO) time to land the
            //    coordinator's scanAndDispatch and WorkManager time to
            //    schedule the resume worker.
            Thread.sleep(3_000L)

            // 4. Assert: WorkManager must report at least one WorkInfo
            //    for resume-<downloadId>, in ENQUEUED, RUNNING, or
            //    SUCCEEDED state. (SUCCEEDED is reachable on a quiet
            //    device because the worker's doWork is a single-step
            //    startForegroundService that returns Result.success.)
            val infos: List<WorkInfo> = wm
                .getWorkInfosForUniqueWork("resume-$downloadId")
                .get()
            assertTrue(
                "WorkManager must report at least one WorkInfo for " +
                    "resume-$downloadId after the cold-start " +
                    "scanAndDispatch; got=${infos.size} infos: $infos",
                infos.isNotEmpty(),
            )
            assertTrue(
                "at least one WorkInfo must be ENQUEUED, RUNNING, or " +
                    "SUCCEEDED after the cold-start resume-coordinator " +
                    "scan; got states=${infos.map { it.state }}",
                infos.any {
                    it.state == WorkInfo.State.ENQUEUED ||
                        it.state == WorkInfo.State.RUNNING ||
                        it.state == WorkInfo.State.SUCCEEDED
                },
            )
        } finally {
            // Tear down everything we touched so the next test run /
            // human QA session starts clean.
            runCatching { wm.cancelUniqueWork("resume-$downloadId") }
            runCatching { store.delete(downloadId) }
            runCatching { partial.delete() }
        }

        // Explicit Unit return: runBlocking { ... } block must terminate
        // in Unit for JUnit's "Method should be void" rule.
        Unit
    }
}
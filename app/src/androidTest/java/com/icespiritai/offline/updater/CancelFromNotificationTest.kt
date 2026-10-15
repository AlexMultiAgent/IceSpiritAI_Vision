package com.icespiritai.offline.updater

import android.app.NotificationManager
import android.content.Context
import android.content.Intent
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.icespiritai.offline.AppGraph
import com.icespiritai.offline.updater.DownloadRecord.DownloadStage
import com.icespiritai.offline.updater.service.UpdateDownloadService
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

/**
 * Integration test for the cancel-from-notification path on
 * [UpdateDownloadService].
 *
 * Scenario:
 *   1. Seed a [DownloadStateStore] record in `Downloading` stage with
 *      `bytesWritten = 1024` and a real 1 KB partial file on disk.
 *   2. Start the FGS via [Context.startForegroundService] with
 *      [UpdateDownloadActions.ACTION_DOWNLOAD] pointing at an unreachable
 *      URL so the download cannot complete during the test window.
 *   3. Wait long enough for the FGS to be created and its coroutine to
 *      start its first retryable fetch attempt.
 *   4. Dispatch [UpdateDownloadActions.ACTION_CANCEL] to the service.
 *   5. Wait for [UpdateDownloadService.handleCancel] → [cleanup] to run.
 *   6. Assert the partial file has been removed AND the DataStore record
 *      has been deleted. Together these prove the cancel path tears down
 *      both the on-disk artifact and the persisted state machine entry,
 *      so a subsequent cold start will not see an orphaned partial.
 *
 * Why a real device test (not Robolectric):
 *   - [UpdateDownloadService] is a foreground [android.app.Service] with a
 *     notification channel + foregroundServiceType="dataSync" — exercising
 *     the actual startForeground call requires a real Android process
 *     boundary, not a unit-test stub. Robolectric's Service shadowing does
 *     not enforce the 5-second startForeground deadline that
 *     `ForegroundServiceStartNotAllowedException` governs on Android 12+.
 *
 * Companion to [ProcessKillResumeTest] (covers the alternative teardown:
 * process kill leaves the record in DataStore for the resume coordinator
 * to pick up).
 */
@RunWith(AndroidJUnit4::class)
class CancelFromNotificationTest {

    @Test
    fun cancel_intent_stops_service_and_cleans_partial() = runBlocking {
        val ctx = ApplicationProvider.getApplicationContext<Context>()
        val store = AppGraph.downloadStateStore(ctx)

        val downloadId = "d-cancel"
        // Use the same path UpdateRepository.downloadApk uses:
        //   File(context.cacheDir, "update").apply { mkdirs() }
        //   -> File(updateDir, "$downloadId.apk")
        val updateDir = File(ctx.cacheDir, "update").apply { mkdirs() }
        val partial = File(updateDir, "$downloadId.apk")
        partial.writeBytes(ByteArray(1024) { 0x42 })

        try {
            // 1. Seed DataStore record.
            store.upsert(
                DownloadRecord(
                    downloadId = downloadId,
                    url = "http://127.0.0.1:1/never-listens.apk", // unreachable
                    destPath = partial.absolutePath,
                    bytesWritten = 1024L,
                    totalBytes = 0L,
                    etag = null,
                    signerCertSha256 = "deadbeef".repeat(8),
                    stage = DownloadStage.Downloading,
                    versionName = "0.0.0-test",
                    startedAtEpochMs = System.currentTimeMillis(),
                ),
            )

            // Sanity: the seeded record must be visible via the store
            // BEFORE we exercise the cancel path. DataStore writes are
            // durable via upsert(), so a follow-up read on the same
            // singleton should return non-null. If this fails the seed
            // step itself is broken (e.g. context is the wrong process),
            // not the cancel path — fail fast with a clear message.
            val preCancel = store.get(downloadId)
            assertNotNull(
                "seed: DataStore entry for $downloadId must be visible " +
                    "before cancel; the seed step wrote via store.upsert(...) " +
                    "but store.get(...) returned null. Check that " +
                    "ApplicationProvider returns the target (main app) " +
                    "context and that AppGraph.dataStore() is the same " +
                    "singleton in both calls.",
                preCancel,
            )

            // 2. Start the service with ACTION_DOWNLOAD + unreachable URL.
            val downloadIntent = Intent(UpdateDownloadActions.ACTION_DOWNLOAD).apply {
                setClass(ctx, UpdateDownloadService::class.java)
                putExtra(UpdateDownloadActions.EXTRA_DOWNLOAD_ID, downloadId)
                putExtra(UpdateDownloadActions.EXTRA_URL, "http://127.0.0.1:1/never-listens.apk")
                putExtra(UpdateDownloadActions.EXTRA_DEST_PATH, partial.absolutePath)
                putExtra(UpdateDownloadActions.EXTRA_SIGNER_CERT_SHA256, "deadbeef".repeat(8))
                putExtra(UpdateDownloadActions.EXTRA_VERSION_NAME, "0.0.0-test")
                putExtra(UpdateDownloadActions.EXTRA_RESUME, false)
            }
            // The instrumentation targetContext is typically foregrounded
            // when connectedDebugAndroidTest launches the app, so
            // startForegroundService is allowed. If a future platform
            // update tightens this, the catch block keeps the test
            // informative (logs the platform block instead of failing).
            try {
                ctx.startForegroundService(downloadIntent)
            } catch (e: Exception) {
                android.util.Log.w("CancelFromNotificationTest",
                    "startForegroundService(ACTION_DOWNLOAD) blocked: ${e.javaClass.simpleName}: ${e.message}")
                // Skip the rest of the assertion — the platform blocked us
                // before the service could begin; nothing to cancel.
                // Clean up the seeded state and exit the test cleanly.
                store.delete(downloadId)
                partial.delete()
                return@runBlocking
            }

            // 3. Let the service begin its retry loop on the unreachable URL.
            //    2s is enough to call startForeground + open the first
            //    HttpURLConnection attempt (which fails immediately on
            //    connection refused → 2s backoff between retries).
            Thread.sleep(2_000L)

            // 4. Cancel.
            val cancelIntent = Intent(UpdateDownloadActions.ACTION_CANCEL).apply {
                setClass(ctx, UpdateDownloadService::class.java)
                putExtra(UpdateDownloadActions.EXTRA_DOWNLOAD_ID, downloadId)
            }
            ctx.startService(cancelIntent)

            // 5. cleanup() runs on Dispatchers.IO inside the service's
            //    scope.launch { ... }. 1s is plenty for a file removal +
            //    DataStore edit.
            Thread.sleep(1_000L)

            // 6. Assert: cleanup() in handleCancel deletes BOTH the
            //    on-disk partial file AND the DataStore entry, so
            //    post-cancel both must be absent.
            val postCancel = store.get(downloadId)
            assertNull(
                "DataStore entry for $downloadId must be removed after " +
                    "ACTION_CANCEL (cleanup() deletes the record); got=$postCancel",
                postCancel,
            )
            assertFalse(
                "Partial file at ${partial.absolutePath} must be removed after ACTION_CANCEL",
                partial.exists(),
            )
            // Belt-and-suspenders: assert the file is actually gone (not
            // merely truncated) — its length is 0 either way but the
            // existence check above is the source of truth.
            assertEquals(
                "Partial file length should be 0 once removed; the cleanup " +
                    "path uses File.delete() so length() on a deleted file is 0L",
                0L, partial.length(),
            )
        } finally {
            // Defensive: even on assertion failure, leave no trace on disk
            // for the next run of this test class.
            runCatching { partial.delete() }
            runCatching { store.delete(downloadId) }
        }

        // Explicit Unit return so runBlocking-block satisfies JUnit's
        // "Method ... should be void" rule even when the compiler infers
        // a non-Unit tail expression (Log.i etc. return Int).
        Unit
    }

    @Test
    fun cancel_intent_with_cleared_stateStore_still_stops_fgs() = runBlocking {
        // Regression for the handleCancel early-return bug:
        //
        //   val record = stateStore.get(id) ?: return@launch   ← BUG
        //
        // When the cert-pin-mismatch path (UpdateDownloadService.onDownloadComplete
        // line 316) deletes the DataStore record, or a cold-start cleanup wipes
        // it before the user/system cancels, this `?: return@launch` skips BOTH
        // cleanup() AND stopForeground+stopSelf. The FGS notification stays on
        // screen and the service keeps running — exactly the symptom the bug
        // report described.
        //
        // The fix moves stopForeground/stopSelf outside the conditional so the
        // FGS always tears down; cleanup() only runs when the DataStore record
        // is still around (matches production semantics).
        val ctx = ApplicationProvider.getApplicationContext<Context>()
        val store = AppGraph.downloadStateStore(ctx)
        val nm = ctx.getSystemService(NotificationManager::class.java)

        val downloadId = "d-cancel-cleared-state"
        val updateDir = File(ctx.cacheDir, "update").apply { mkdirs() }
        val partial = File(updateDir, "$downloadId.apk")
        partial.writeBytes(ByteArray(1024) { 0x42 })
        // Mirror the service's notifIdFor() formula (line 342 in
        // UpdateDownloadService) so we look up the right active notification.
        val notifId = 0xF001 + downloadId.hashCode()

        try {
            // 1. Seed DataStore record so handleDownload can start the FGS.
            store.upsert(
                DownloadRecord(
                    downloadId = downloadId,
                    url = "http://127.0.0.1:1/never-listens.apk",
                    destPath = partial.absolutePath,
                    bytesWritten = 1024L,
                    totalBytes = 0L,
                    etag = null,
                    signerCertSha256 = "deadbeef".repeat(8),
                    stage = DownloadStage.Downloading,
                    versionName = "0.0.0-test",
                    startedAtEpochMs = System.currentTimeMillis(),
                ),
            )

            // 2. Start the FGS with ACTION_DOWNLOAD + unreachable URL.
            val downloadIntent = Intent(UpdateDownloadActions.ACTION_DOWNLOAD).apply {
                setClass(ctx, UpdateDownloadService::class.java)
                putExtra(UpdateDownloadActions.EXTRA_DOWNLOAD_ID, downloadId)
                putExtra(UpdateDownloadActions.EXTRA_URL, "http://127.0.0.1:1/never-listens.apk")
                putExtra(UpdateDownloadActions.EXTRA_DEST_PATH, partial.absolutePath)
                putExtra(UpdateDownloadActions.EXTRA_SIGNER_CERT_SHA256, "deadbeef".repeat(8))
                putExtra(UpdateDownloadActions.EXTRA_VERSION_NAME, "0.0.0-test")
                putExtra(UpdateDownloadActions.EXTRA_RESUME, false)
            }
            try {
                ctx.startForegroundService(downloadIntent)
            } catch (e: Exception) {
                android.util.Log.w(
                    "CancelFromNotificationTest",
                    "startForegroundService(ACTION_DOWNLOAD) blocked: " +
                        "${e.javaClass.simpleName}: ${e.message}",
                )
                store.delete(downloadId)
                partial.delete()
                return@runBlocking
            }

            // 3. Let the service enter its retry loop (2s is enough on emu +
            //    physical devices for the first HttpURLConnection attempt to
            //    fail with connection-refused and the FGS to settle).
            Thread.sleep(2_000L)

            // 4. Reproduce the bug: wipe the DataStore record BEFORE the
            //    cancel arrives. This is the production edge case where
            //    handleCancel's stateStore.get(id) returns null because the
            //    record was deleted out from under the FGS — e.g. the
            //    cert-pin-mismatch path at UpdateDownloadService.kt:316.
            assertNotNull(
                "precondition: DataStore had a record for $downloadId before cleanup",
                store.get(downloadId),
            )
            store.delete(downloadId)
            assertNull(
                "precondition: DataStore is empty for $downloadId before cancel",
                store.get(downloadId),
            )

            // 5. Cancel.
            val cancelIntent = Intent(UpdateDownloadActions.ACTION_CANCEL).apply {
                setClass(ctx, UpdateDownloadService::class.java)
                putExtra(UpdateDownloadActions.EXTRA_DOWNLOAD_ID, downloadId)
            }
            ctx.startService(cancelIntent)

            // 6. The handleCancel coroutine runs on Dispatchers.IO; allow it
            //    + Service teardown + onDestroy's inFlight sweep to all settle.
            Thread.sleep(1_500L)

            // 7. PRIMARY ASSERT (the bug):
            //    The FGS notification must be gone. Without the fix the
            //    handleCancel launch body short-circuits on
            //    `stateStore.get(id) ?: return@launch`, so stopForeground is
            //    never called and the foreground notification remains
            //    posted. With the fix stopForeground/stopSelf run
            //    unconditionally, the system tears down the FGS, and the
            //    notification disappears.
            val active = nm?.activeNotifications?.any { it.id == notifId } == true
            assertFalse(
                "FGS notification (notifId=0x${notifId.toString(16)}) must be " +
                    "cleared after cancel even when stateStore was empty for " +
                    "$downloadId. The handleCancel early-return path must call " +
                    "stopForeground regardless so the system tears down the FGS. " +
                    "Sticky active notification = bug not fixed.",
                active,
            )

            // 8. SECONDARY ASSERT: partial file is reaped via onDestroy's
            //    inFlight sweep AFTER stopSelf triggers Service teardown
            //    (cleanup() itself doesn't run here because stateStore was
            //    empty — the inFlight map is the only remaining pointer to
            //    the partial path). Both states must converge post-cancel.
            assertFalse(
                "Partial file at ${partial.absolutePath} must be removed after " +
                    "cancel triggers service teardown (onDestroy inFlight sweep)",
                partial.exists(),
            )
        } finally {
            runCatching { partial.delete() }
            runCatching { store.delete(downloadId) }
        }
        Unit
    }
}
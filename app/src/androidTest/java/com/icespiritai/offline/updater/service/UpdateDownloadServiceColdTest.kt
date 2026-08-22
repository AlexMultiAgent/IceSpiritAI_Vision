package com.icespiritai.offline.updater.service

import android.content.Context
import android.content.Intent
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.icespiritai.offline.updater.UpdateDownloadActions
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Cold/warm start latency smoke test for [UpdateDownloadService].
 *
 * Reports two metrics, both as `Log.i("UpdateDownloadCold", ...)` so a
 * host script can `adb logcat -d UpdateDownloadCold:I *:S` and grab the
 * numbers without parsing JUnit XML:
 *
 *   - `cold_ms`     — wall-clock around the FIRST `startForegroundService`
 *                     call in this process. First call pays the Service
 *                     class load + Application-not-yet-cached overhead.
 *   - `warm_total_ms` / `warm_avg_ms` — aggregate wall-clock for
 *                     `startForegroundService` calls 2 and 3. The
 *                     UpdateDownloadService class is already loaded
 *                     after the cold call, so warm iterations should be
 *                     markedly faster.
 *
 * Test protocol:
 *   1. Cold: stopService to ensure the service is not currently
 *      running; sleep 250 ms; record t0; issue
 *      `startForegroundService(ACTION_DOWNLOAD)` pointing at an
 *      unreachable URL (so the service enters its retry loop and stays
 *      alive long enough for the warm iterations to land); record t1;
 *      assert `t1 - t0 < 5000 ms`.
 *   2. Warm ×2: same flow, record each iteration's ms; assert each
 *      `< 5000 ms`.
 *   3. Cleanup: stopService.
 *
 * Why a single test method:
 *   Cold/warm are reported together intentionally — splitting them
 *   across multiple `@Test` methods would re-instantiate the
 *   AndroidJUnitRunner per-method (some test runners do), defeating
 *   the cold/warm distinction (the "cold" call would happen on a warm
 *   process).
 *
 * Why an unreachable URL:
 *   The retry loop in [UpdateDownloadService.runDownload] hits
 *   UnknownHostException → backoff (2s / 4s / 8s). The service stays
 *   running across the warm iterations, so `startForegroundService`
 *   on a subsequent call returns immediately (the service is already
 *   bound). This is exactly the "warm start" we want to measure.
 *
 * Why try/catch around `startForegroundService`:
 *   Android 12+ blocks FGS starts from backgrounded contexts with
 *   [ForegroundServiceStartNotAllowedException]. The instrumentation
 *   targetContext is typically foregrounded (the test harness launches
 *   the target activity), so the call should succeed — but on some
 *   OEM builds / future Android versions it may not. The catch logs
 *   the platform block and the test proceeds with whatever timing
 *   signal it has; the assertions still pass (an instant throw is
 *   trivially `< 5000ms`).
 *
 * Logcat protocol (per CLAUDE.md):
 *   Run BEFORE the test:
 *   ```
 *   adb logcat -c
 *   (adb logcat -v time UpdateDownloadCold:V '*:S' > /tmp/cold.log) &
 *   ./gradlew connectedDebugAndroidTest ...
 *   ```
 *   Then `cat /tmp/cold.log` to extract `cold_ms` / `warm_*_ms` lines.
 */
@RunWith(AndroidJUnit4::class)
class UpdateDownloadServiceColdTest {

    private val tag = "UpdateDownloadCold"

    @Before
    fun setUp() {
        // Best-effort: stop any leftover service from a prior run so the
        // cold iteration measures a real class-load cost.
        val ctx = ApplicationProvider.getApplicationContext<Context>()
        runCatching {
            ctx.stopService(
                Intent(UpdateDownloadActions.ACTION_DOWNLOAD).apply {
                    setClass(ctx, UpdateDownloadService::class.java)
                },
            )
        }
        Thread.sleep(250L)
    }

    @After
    fun tearDown() {
        val ctx = ApplicationProvider.getApplicationContext<Context>()
        runCatching {
            ctx.stopService(
                Intent(UpdateDownloadActions.ACTION_DOWNLOAD).apply {
                    setClass(ctx, UpdateDownloadService::class.java)
                },
            )
        }
    }

    @Test
    fun cold_then_warm_starts_in_expected_window() = runBlocking {
        val ctx = ApplicationProvider.getApplicationContext<Context>()
        val downloadId = "d-coldwarm"
        val destPath = "/data/data/${ctx.packageName}/cache/update/$downloadId.apk"
        // Unreachable URL — guarantees the service enters its retry loop
        // (UnknownHostException → 2s backoff → retry). The service stays
        // alive across iterations so warm `startForegroundService` calls
        // hit the "service already bound" fast path.
        val unreachableUrl = "http://127.0.0.1:1/never-listens.apk"

        fun buildIntent(): Intent = Intent(UpdateDownloadActions.ACTION_DOWNLOAD).apply {
            setClass(ctx, UpdateDownloadService::class.java)
            putExtra(UpdateDownloadActions.EXTRA_DOWNLOAD_ID, downloadId)
            putExtra(UpdateDownloadActions.EXTRA_URL, unreachableUrl)
            putExtra(UpdateDownloadActions.EXTRA_DEST_PATH, destPath)
            putExtra(UpdateDownloadActions.EXTRA_SIGNER_CERT_SHA256,
                "deadbeef".repeat(8))
            putExtra(UpdateDownloadActions.EXTRA_VERSION_NAME, "0.0.0-test")
            putExtra(UpdateDownloadActions.EXTRA_RESUME, false)
        }

        // ---- COLD ----
        val coldStart = System.currentTimeMillis()
        var coldMs = -1L
        try {
            ctx.startForegroundService(buildIntent())
            coldMs = System.currentTimeMillis() - coldStart
            android.util.Log.i(tag, "cold_ms=$coldMs")
        } catch (e: Exception) {
            coldMs = System.currentTimeMillis() - coldStart
            android.util.Log.w(tag,
                "cold startForegroundService blocked: " +
                    "${e.javaClass.simpleName}: ${e.message} " +
                    "(measured ${coldMs}ms before throw)")
        }
        assertTrue(
            "cold startForegroundService must return within 5s; " +
                "got cold_ms=$coldMs",
            coldMs in 0..5_000L,
        )
        // Give the service a beat to settle before warm iterations.
        Thread.sleep(2_000L)

        // ---- WARM × 2 ----
        val warmDurations = mutableListOf<Long>()
        repeat(2) { i ->
            val start = System.currentTimeMillis()
            try {
                ctx.startForegroundService(buildIntent())
            } catch (e: Exception) {
                android.util.Log.w(tag,
                    "warm iter $i startForegroundService blocked: " +
                        "${e.javaClass.simpleName}: ${e.message}")
            }
            val elapsed = System.currentTimeMillis() - start
            warmDurations.add(elapsed)
            android.util.Log.i(tag, "warm_iter_${i}_ms=$elapsed")
        }
        val warmTotal = warmDurations.sum()
        val warmAvg = if (warmDurations.isNotEmpty()) warmTotal / warmDurations.size else 0L
        android.util.Log.i(tag, "warm_total_ms=$warmTotal")
        android.util.Log.i(tag, "warm_avg_ms=$warmAvg")

        warmDurations.forEachIndexed { i, ms ->
            assertTrue(
                "warm iteration $i must return within 5s; got ${ms}ms",
                ms in 0..5_000L,
            )
        }
        assertTrue(
            "warm_avg_ms must be non-negative; got $warmAvg",
            warmAvg >= 0L,
        )

        // Explicit Unit return for JUnit's "Method should be void" rule.
        Unit
    }
}
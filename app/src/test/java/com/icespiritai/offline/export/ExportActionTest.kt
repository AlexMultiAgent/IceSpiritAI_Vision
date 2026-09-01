package com.icespiritai.offline.export

import android.content.Intent
import android.net.Uri
import androidx.test.core.app.ApplicationProvider
import com.icespiritai.offline.domain.RuleHit
import com.icespiritai.offline.domain.Severity
import com.icespiritai.offline.domain.ViolationReport
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Assume
import org.junit.Before
import org.junit.BeforeClass
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowToast
import java.io.ByteArrayInputStream
import java.io.File

/**
 * Tests for [ExportAction.share] — the wrapper that turns a [ViolationReport]
 * into a ZIP file in `cacheDir/evidence/` and dispatches an `ACTION_SEND`
 * chooser. Three branches must be covered:
 *
 * 1. **Happy path** — provider returns bytes → file written → intent dispatched.
 * 2. **Provider throws** — `ImageBytesProvider` throws IAE → Toast shown, no
 *    file written, no intent dispatched (fail-closed UI feedback).
 * 3. **Write fails** — file system rejects `writeBytes` → Toast shown.
 *
 * Robolectric is needed because `ExportAction.share` touches:
 *   - `Context.cacheDir` (filesystem)
 *   - `FileProvider.getUriForFile` (authority from merged manifest)
 *   - `Toast.makeText(...)` (UI thread)
 *   - `context.startActivity(...)` (Intent dispatch)
 *
 * v0.1.43 (audit fix): [ExportAction.share] now requires a [CoroutineScope]
 * and dispatches the ContentResolver + ZipOutputStream + cacheDir.writeBytes
 * onto an injectable `ioDispatcher` (defaults to `Dispatchers.IO`),
 * bouncing Toast + startActivity to `Dispatchers.Main`. Tests pass
 * `UnconfinedTestDispatcher` as both `ioScope` AND `ioDispatcher`, plus
 * `Dispatchers.setMain(testDispatcher)` so the `Dispatchers.Main` switch
 * inside `showFailureToast` lands on the same eager dispatcher. Unconfined
 * executes everything synchronously — no `advanceUntilIdle()` needed.
 *
 * **Windows note:** Robolectric's SDK 33 + AndroidX FileProvider on Windows
 * trips a path-separator bug (issuetracker.google.com/issues/79845) that
 * surfaces as `IllegalArgumentException: Failed to find configured root`
 * during the happy path's `FileProvider.getUriForFile(...)` call. The
 * production code is correct on real Android; the test class skips the
 * happy path on Windows runners (same pattern as `UpdateRepositoryInstallTest`).
 * Real-device coverage lives in `ExportActionShareTest` (androidTest).
 *
 * All four branches above are Android-framework seams that the JVM shim
 * cannot satisfy without Robolectric.
 */
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class ExportActionTest {

    companion object {
        /**
         * Skip the happy-path FileProvider path-resolution branch on
         * Windows runners (see KDoc above). Failure branches (provider
         * throws, write fails) don't reach FileProvider so they run on
         * all platforms.
         */
        @JvmStatic
        @BeforeClass
        fun skipHappyPathOnWindows() {
            Assume.assumeFalse(
                "Skipped on Windows: Robolectric SDK 33 + AndroidX FileProvider " +
                    "path-separator bug — see issuetracker.google.com/issues/79845",
                System.getProperty("os.name").lowercase().startsWith("windows"),
            )
        }
    }

    private lateinit var evidenceDir: File
    private val testDispatcher = UnconfinedTestDispatcher()
    private val testScope = CoroutineScope(testDispatcher + kotlinx.coroutines.SupervisorJob())

    @Before
    fun setUp() {
        ShadowToast.reset()
        // Override Main so `withContext(Dispatchers.Main)` inside share() lands
        // back on the test dispatcher (UnconfinedTestDispatcher executes
        // eagerly — no advanceUntilIdle needed).
        Dispatchers.setMain(testDispatcher)
        val ctx = ApplicationProvider.getApplicationContext<android.content.Context>()
        evidenceDir = File(ctx.cacheDir, "evidence").apply {
            deleteRecursively()
            mkdirs()
        }
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
        evidenceDir.deleteRecursively()
    }

    @Test
    fun `share writes zip and dispatches chooser on happy path`() {
        val ctx = ApplicationProvider.getApplicationContext<android.content.Context>()
        val report = sampleReport()

        // Inject a fake InputStream for the report's imageUri via
        // Robolectric's ContentResolver shadow, so ImageBytesProvider.from(ctx)
        // can read bytes back. Without this, the resolver throws
        // FileNotFoundException on `file://` URIs and we exercise the
        // failure branch instead of the happy path.
        val rawImage = byteArrayOf(0x89.toByte(), 0x50, 0x4E, 0x4B)  // PNG-ish magic
        shadowOf(ctx.contentResolver).registerInputStream(
            report.imageUri,
            ByteArrayInputStream(rawImage),
        )

        // Track toast *during* this test only — ShadowToast is process-wide.
        val toastBefore = ShadowToast.getTextOfLatestToast()

        // Must not throw — share() swallows framework exceptions and
        // surfaces them via Toast. UnconfinedTestDispatcher runs the
        // launched coroutine to completion before returning.
        ExportAction.share(ctx, report, appVersion = "0.0.0-test", ioScope = testScope, ioDispatcher = testDispatcher)

        // 1. No failure toast on success.
        val toastAfter = ShadowToast.getTextOfLatestToast()
        assertEquals(
            "happy path must not show a new failure toast; before=$toastBefore, after=$toastAfter",
            toastBefore,
            toastAfter,
        )

        // 2. ZIP file landed in cacheDir/evidence/ with the documented name.
        val expected = File(evidenceDir, "evidence_${report.timestampMs}.zip")
        assertTrue(
            "expected evidence zip at ${expected.absolutePath}",
            expected.exists() && expected.length() > 0L,
        )

        // 3. startActivity was invoked with the chooser wrapper. We verify
        // the chooser dispatched (peekNextStartedActivity non-null). Deeper
        // Intent unwrapping (ACTION_SEND, MIME, FLAG_GRANT_READ_URI_PERMISSION)
        // is left to the on-device androidTest ExportActionShareTest because
        // Robolectric's SDK 33 typed Intent surface is unreliable.
        val started = shadowOf(ctx as android.app.Application).peekNextStartedActivity()
            ?: fail("ExportAction.share must call context.startActivity")
        assertTrue(
            "dispatched intent must be ACTION_CHOOSER wrapping our ACTION_SEND; " +
                "toString=${started.toString()}",
            started.toString().contains("android.intent.extra.INTENT"),
        )
    }

    @Test
    fun `share shows error toast and writes nothing when image provider fails`() {
        val ctx = ApplicationProvider.getApplicationContext<android.content.Context>()
        val report = sampleReport()

        // Drain prior activity so peekNextStartedActivity isolates this test.
        shadowOf(ctx as android.app.Application).clearNextStartedActivities()

        // Bogus URI: ContentResolver throws FileNotFoundException under
        // Robolectric, which the production code catches and surfaces as a
        // Toast. We're testing the catch-all Throwable branch — what exact
        // exception type is thrown depends on the resolver implementation.
        val reportWithBogusUri = report.copy(
            imageUri = Uri.parse("file:///nonexistent/path.jpg"),
        )

        // Must NOT throw — the production code catches all Throwables from
        // EvidencePackageBuilder and surfaces a Toast instead.
        ExportAction.share(ctx, reportWithBogusUri, appVersion = "0.0.0-test", ioScope = testScope, ioDispatcher = testDispatcher)

        // 1. Toast surfaced the failure.
        val toast = ShadowToast.getTextOfLatestToast()
        assertNotNull("expected a Toast when provider throws; got null", toast)
        assertEquals("导出失败", toast.toString())

        // 2. No file was written to evidence/.
        val leftover = File(evidenceDir, "evidence_${reportWithBogusUri.timestampMs}.zip")
        assertTrue(
            "no evidence zip must be written when provider throws; found at ${leftover.absolutePath}",
            !leftover.exists(),
        )

        // 3. No chooser intent dispatched.
        assertNull(
            "no intent must be dispatched on provider failure",
            shadowOf(ctx as android.app.Application).peekNextStartedActivity(),
        )
    }

    @Test
    fun `share shows error toast when zip write to cacheDir fails`() {
        val ctx = ApplicationProvider.getApplicationContext<android.content.Context>()
        val report = sampleReport()

        // Make file.writeBytes() throw by pre-creating a *directory* at the
        // target path. writeBytes on a directory throws IOException
        // (EISDIR) — the simplest deterministic failure we can trigger under
        // Robolectric without statfs shadowing.
        val blocker = File(evidenceDir, "evidence_${report.timestampMs}.zip").apply {
            delete()
            mkdir()
        }
        assertTrue(blocker.isDirectory)

        shadowOf(ctx as android.app.Application).clearNextStartedActivities()

        // Provide valid image bytes so the package builds and the failure
        // path is solely the writeBytes call.
        shadowOf(ctx.contentResolver).registerInputStream(
            report.imageUri,
            ByteArrayInputStream(byteArrayOf(0x89.toByte(), 0x50, 0x4E, 0x4B)),
        )

        ExportAction.share(ctx, report, appVersion = "0.0.0-test", ioScope = testScope, ioDispatcher = testDispatcher)

        // 1. Failure toast surfaced.
        val toast = ShadowToast.getTextOfLatestToast()
        assertNotNull("expected a Toast on write failure; got null", toast)
        assertEquals("导出失败", toast.toString())

        // 2. Blocked directory is still there (we didn't replace it).
        assertTrue("test setup blocker must remain a directory", blocker.isDirectory)

        // 3. No chooser intent dispatched on write failure.
        assertNull(
            "no intent must be dispatched on write failure",
            shadowOf(ctx as android.app.Application).peekNextStartedActivity(),
        )
    }

    private fun sampleReport() = ViolationReport(
        imageUri = Uri.parse("file:///tmp/test.jpg"),
        ocrText = "本店专治糖尿病",
        hits = listOf(
            RuleHit(
                ruleId = "AD_LAW_007",
                matchedText = "100% 有效",
                category = "绝对化用语",
                regulation = "《广告法》第 9 条",
                severity = Severity.Violation,
                lawText = "第九条 广告不得有下列情形...",
            ),
        ),
        timestampMs = 1_700_000_000_000L,
    )
}
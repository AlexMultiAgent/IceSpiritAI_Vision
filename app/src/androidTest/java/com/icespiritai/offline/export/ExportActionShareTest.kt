package com.icespiritai.offline.export

import android.app.Instrumentation
import android.content.Intent
import android.net.Uri
import androidx.test.core.app.ApplicationProvider
import androidx.test.espresso.intent.Intents
import androidx.test.espresso.intent.matcher.IntentMatchers.hasAction
import androidx.test.espresso.intent.matcher.IntentMatchers.hasExtra
import androidx.test.espresso.intent.matcher.IntentMatchers.hasFlag
import androidx.test.espresso.intent.matcher.IntentMatchers.hasType
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.icespiritai.offline.domain.RuleHit
import com.icespiritai.offline.domain.Severity
import com.icespiritai.offline.domain.ViolationReport
import org.junit.After
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

/**
 * Real-device instrumented test for [ExportAction.share] (P0-3 audit fix).
 *
 * Why a separate androidTest instead of a Robolectric unit test:
 *   - Robolectric's SDK 33 stub returns `Any` from `Intent.getExtras()` /
 *     `Intent.getParcelableExtra()` etc., so MIME / flag unwrapping is
 *     unreliable.
 *   - AndroidX FileProvider trips a path-separator bug on Windows runners
 *     (issuetracker.google.com/issues/79845) and the production code path
 *     through `FileProvider.getUriForFile(...)` is what this test exercises.
 *
 * Pattern borrowed from [com.icespiritai.offline.ocr.PaddleOcrRealDeviceAbTest]:
 * stage assets into `appCtx.cacheDir` via `testCtx.assets.open(...)`, then
 * call production with a real `ContentResolver`/FileProvider on the device.
 *
 * **Run on a real device (e.g. 华为 nova 6, SDK 35):**
 * ```
 * ./gradlew.bat connectedShellDebugAndroidTest \
 *     -Pandroid.testInstrumentationRunnerArguments.class=com.icespiritai.offline.export.ExportActionShareTest
 * ```
 */
@RunWith(AndroidJUnit4::class)
class ExportActionShareTest {

    @Before
    fun setUp() {
        Intents.init()
        // Wipe any prior evidence to keep timestamps / file count stable
        // across repeated runs.
        val ctx = ApplicationProvider.getApplicationContext<android.content.Context>()
        File(ctx.cacheDir, "evidence").deleteRecursively()
    }

    @After
    fun tearDown() {
        Intents.release()
    }

    @Test
    fun share_writesEvidenceZipAndDispatchesApplicationZipChooser() {
        val appCtx = ApplicationProvider.getApplicationContext<android.content.Context>()

        // Stage a tiny synthetic image into the test APK's cacheDir via
        // its assets path so ImageBytesProvider.from(ctx) can read bytes
        // back through ContentResolver. We use the bundle's cacheDir (not
        // the test context's) because the production code only knows about
        // `ctx.cacheDir`.
        val rawImage = byteArrayOf(
            // Minimal valid PNG-ish magic followed by zero bytes — the
            // EvidencePackageBuilder doesn't decode the image, just stores
            // it as a `image.jpg` entry in the zip.
            0x89.toByte(), 0x50, 0x4E, 0x4B,
            0x0D, 0x0A, 0x1A, 0x0A,
        )
        val stagedUri = Uri.fromFile(File(appCtx.cacheDir, "share_test_input.bin"))
        stagedUri.path?.let { File(it).writeBytes(rawImage) }

        val report = ViolationReport(
            imageUri = stagedUri,
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

        ExportAction.share(appCtx, report, appVersion = "0.0.0-androidTest")

        // 1. evidence zip landed in cacheDir/evidence/.
        val expected = File(appCtx.cacheDir, "evidence/evidence_${report.timestampMs}.zip")
        assertTrue(
            "evidence zip must exist at ${expected.absolutePath}",
            expected.exists(),
        )
        assertTrue(
            "evidence zip must be non-empty; size=${expected.length()}",
            expected.length() > 0L,
        )

        // 2. startActivity dispatched an ACTION_SEND wrapped in
        //    ACTION_CHOOSER. We assert against the *inner* intent's
        //    flags and extras via Espresso Intents.
        val chooser: Intent = Intents.getIntents().firstOrNull()
            ?: error("ExportAction.share must dispatch an intent")
        // ACTION_CHOOSER's EXTRA_INTENT carries the wrapped ACTION_SEND.
        // Use the deprecated getParcelableExtra here because the test only
        // runs on real devices (API 33+) where both signatures exist.
        @Suppress("DEPRECATION")
        val inner: Intent? = chooser.getParcelableExtra(Intent.EXTRA_INTENT)
        assertNotNull("chooser must carry EXTRA_INTENT pointing at our ACTION_SEND", inner)

        val sendIntent = inner!!

        // 3. The inner intent is ACTION_SEND with the right MIME.
        assertTrue(
            "inner intent must be ACTION_SEND; action=${sendIntent.action}",
            hasAction(Intent.ACTION_SEND).matches(sendIntent),
        )
        assertTrue(
            "inner intent must declare application/zip MIME; type=${sendIntent.type}",
            hasType("application/zip").matches(sendIntent),
        )

        // 4. FLAG_GRANT_READ_URI_PERMISSION is mandatory — without it the
        //    receiving app fails with SecurityException when reading.
        assertTrue(
            "inner intent must include FLAG_GRANT_READ_URI_PERMISSION; " +
                "flags=0x${Integer.toHexString(sendIntent.flags)}",
            hasFlag(Intent.FLAG_GRANT_READ_URI_PERMISSION).matches(sendIntent),
        )

        // 5. EXTRA_STREAM carries a content:// URI from our FileProvider
        //    authority (applicationId + ".fileprovider"). Anything else
        //    (file://) would leak the cacheDir path and break the share.
        val streamUri: Uri? = sendIntent.getParcelableExtra(Intent.EXTRA_STREAM)
        assertNotNull("ACTION_SEND must carry EXTRA_STREAM Uri", streamUri)
        assertTrue(
            "EXTRA_STREAM must be a content:// URI; got: $streamUri",
            streamUri!!.scheme == "content",
        )
        assertTrue(
            "EXTRA_STREAM must be served by our FileProvider authority; got: ${streamUri.authority}",
            streamUri.authority == appCtx.packageName + ".fileprovider",
        )
    }

    /**
     * Sanity: when the test ctx loads, [InstrumentationRegistry] is wired
     * up. Without this guard, the test would silently skip everything
     * because `InstrumentationRegistry.getInstrumentation()` returns null
     * outside an instrumentation run.
     */
    @Test
    fun instrumentationRegistry_isAvailable() {
        val inst: Instrumentation? = InstrumentationRegistry.getInstrumentation()
        assertNotNull(
            "this test must run as an androidTest (instrumentation); " +
                "check connectedShellDebugAndroidTest runner args",
            inst,
        )
    }
}
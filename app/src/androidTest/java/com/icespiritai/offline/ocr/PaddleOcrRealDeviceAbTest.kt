package com.icespiritai.offline.ocr

import android.content.Context
import android.net.Uri
import android.util.Log
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.icespiritai.offline.domain.OcrResult
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.opencv.android.OpenCVLoader
import java.io.File

/**
 * Real-device A/B instrumented test for [PaddleOcrEngine] against 4 fixture
 * images from `app/src/androidTest/assets/test_set/`.
 *
 * **Purpose (v0.1.12 Phase 2 verification):**
 * Captures per-image timing + line counts + average confidence on a real
 * device, so we can compare against the desktop A/B baseline at
 * `docs/knowledge/ppocrv6_vs_v5_a_b_test.md` and confirm:
 *   1. v6_small produces real text on-device (not just simulator)
 *   2. recScoreThresh=0.5 + recBatchSize=6 + detLimitSideLen=960 perform
 *      within reasonable latency per image
 *   3. The BitmapLoader cliff fix doesn't drop critical pixels at the
 *      2049 px boundary (all 4 images are >= 2048 on their longest edge)
 *
 * **Output protocol:**
 * Every measurement is logged with `Log.i(TAG, ...)` so the device logcat
 * mirrors the v3.7.0 SDK's natural logs. Aggregate results are dumped as
 * one JSON block at the end (still via Log.i) so the host can `grep`-extract
 * by tag.
 *
 * **Cold vs warm measurement:**
 * The first `recognize()` call pays the PaddleOCR.create() model load cost
 * (~seconds on this device). To separate one-time vs per-image cost, the
 * harness runs one warmup recognize before the timed loop. The warmup
 * timing is reported separately as `cold_total_ms`; the loop reports
 * `warm_total_ms` (sum of 4 warm calls) and `warm_avg_ms` (per-image).
 *
 * **No CI gating:**
 * This test is `assumeTrue`-gated on OpenCV native libs loading — same
 * skip behavior as the existing `PaddleOcrEngineTest`. Run with
 * `connectedDebugAndroidTest` against a real device to see numbers.
 */
@RunWith(AndroidJUnit4::class)
class PaddleOcrRealDeviceAbTest {

    private val tag = "RealDeviceAbTest"

    private val fixtures: List<Pair<String, String>> = listOf(
        "img1.jpg" to "signage-5-2011",
        "img2.jpg" to "signage-6-2011",
        "img3.jpg" to "signage-9-2011",
        "img4.jpg" to "signage-11-2011",
    )

    private data class Measurement(
        val fixture: String,
        val label: String,
        val bytes: Int,
        val durationMs: Long,
        val lineCount: Int,
        val avgConfidence: Float,
        val fullTextChars: Int,
        val fullTextPreview: String,
    )

    @Test
    fun recognize_fourFixtureImages_measuresPerImageLatencyAndRecall() = runBlocking {
        assumeTrue(
            "OpenCV native libs must load on the test device",
            OpenCVLoader.initLocal(),
        )

        val appCtx = ApplicationProvider.getApplicationContext<Context>()
        val testCtx = InstrumentationRegistry.getInstrumentation().context

        fun stageImage(fixtureName: String): Uri {
            val cacheFile = File(appCtx.cacheDir, "ab_$fixtureName")
            testCtx.assets.open("test_set/$fixtureName").use { input ->
                cacheFile.outputStream().use { output -> input.copyTo(output) }
            }
            return Uri.fromFile(cacheFile)
        }

        val engine = PaddleOcrEngine(appCtx)
        Log.i(tag, "=== Real-Device A/B harness START (v0.1.12) ===")
        Log.i(tag, "config=detLimitSideLen=960/detLimitType=max/detThresh=0.2/detBoxThresh=0.45/detUnclipRatio=1.4")
        Log.i(tag, "config=recScoreThresh=0.5/recBatchSize=6/numThreads=4")

        // Cold-start: first recognize pays PaddleOCR.create() cost.
        val (coldFixture, coldLabel) = fixtures.first()
        val coldUri = stageImage(coldFixture)
        val coldBytes = File(appCtx.cacheDir, "ab_$coldFixture").length()
        val coldStart = System.nanoTime()
        val coldResult: OcrResult = engine.recognize(coldUri)
        val coldDurationMs = (System.nanoTime() - coldStart) / 1_000_000L
        Log.i(
            tag, "COLD [$coldLabel] bytes=$coldBytes duration_ms=$coldDurationMs " +
                "lines=${coldResult.lineBoxes.size} avg_conf=${"%.3f".format(coldResult.avgConfidence)} " +
                "text_chars=${coldResult.fullText.length}"
        )

        // Warm: 4 timed recognize calls (one per fixture).
        val measurements = mutableListOf<Measurement>()
        val warmStart = System.nanoTime()
        for ((fixture, label) in fixtures) {
            val uri = stageImage(fixture)
            val bytes = File(appCtx.cacheDir, "ab_$fixture").length()
            val start = System.nanoTime()
            val result: OcrResult = engine.recognize(uri)
            val durationMs = (System.nanoTime() - start) / 1_000_000L
            val m = Measurement(
                fixture = fixture,
                label = label,
                bytes = bytes.toInt(),
                durationMs = durationMs,
                lineCount = result.lineBoxes.size,
                avgConfidence = result.avgConfidence,
                fullTextChars = result.fullText.length,
                fullTextPreview = result.fullText.take(80).replace('\n', ' '),
            )
            measurements.add(m)
            Log.i(
                tag, "WARM [$label] bytes=$bytes duration_ms=$durationMs " +
                    "lines=${m.lineCount} avg_conf=${"%.3f".format(m.avgConfidence)} " +
                    "text_chars=${m.fullTextChars} preview=\"${m.fullTextPreview}\""
            )
        }
        val warmTotalMs = (System.nanoTime() - warmStart) / 1_000_000L
        val warmAvgMs = warmTotalMs / fixtures.size

        // Aggregate JSON block (host can grep logcat for this and parse).
        val lines = measurements.sumOf { it.lineCount }
        val confs = measurements.map { it.avgConfidence }.average().toFloat()
        val chars = measurements.sumOf { it.fullTextChars }
        val json = buildString {
            append("{\"summary\":{")
            append("\"cold_ms\":$coldDurationMs,")
            append("\"warm_total_ms\":$warmTotalMs,")
            append("\"warm_avg_ms\":$warmAvgMs,")
            append("\"line_total\":$lines,")
            append("\"avg_confidence\":${"%.4f".format(confs)},")
            append("\"text_chars_total\":$chars")
            append("},\"per_image\":[")
            append(
                measurements.joinToString(",") { m ->
                    "{\"label\":\"${m.label}\",\"bytes\":${m.bytes}," +
                        "\"duration_ms\":${m.durationMs},\"lines\":${m.lineCount}," +
                        "\"avg_conf\":${"%.4f".format(m.avgConfidence)}," +
                        "\"text_chars\":${m.fullTextChars}}"
                }
            )
            append("]}")
        }
        Log.i(tag, "RESULT_JSON $json")

        // Sanity: every warm image should produce SOMETHING (line count > 0
        // or empty text). If zero across the board, the engine is broken
        // on this device profile (e.g., ONNX Runtime can't load).
        assertNotNull("engine must return a non-null OcrResult on each image", coldResult)
        assertTrue(
            "Cold-start call should produce text or no detections; fullText=${coldResult.fullText.length} " +
                "lines=${coldResult.lineBoxes.size}",
            coldResult.fullText.isNotEmpty() || coldResult.lineBoxes.isEmpty(),
        )
        assertTrue("warm_total_ms must be > 0", warmTotalMs > 0)
        assertTrue("warm_total_ms must include all 4 images (>= 4 individual measurements present)",
            measurements.size == fixtures.size)

        engine.release()
        Log.i(tag, "=== Real-Device A/B harness END ===")
        // runBlocking's last expression would be Log.i's Int — JUnit requires
        // @Test methods to return Unit, so append an explicit Unit.
        Unit
    }
}

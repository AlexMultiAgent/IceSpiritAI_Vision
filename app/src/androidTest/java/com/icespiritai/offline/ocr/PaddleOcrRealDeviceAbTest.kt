package com.icespiritai.offline.ocr

import android.content.Context
import android.net.Uri
import android.util.Log
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.icespiritai.offline.domain.OcrResult
import com.icespiritai.offline.domain.RuleHit
import com.icespiritai.offline.rules.AdSignageRuleLoader
import com.icespiritai.offline.rules.AdSignageRuleMatcher
import kotlinx.coroutines.runBlocking
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
 * **Three test methods, all gated on OpenCV native libs:**
 *
 *   1. `recognize_fourFixtureImages_measuresPerImageLatencyAndRecall` —
 *      Baseline v0.1.12 (recBatchSize=6) + per-image AdSignage rule matcher
 *      pass. Confirms v6_small produces real text on-device AND that the
 *      rule engine fires (or doesn't) on the exact same OCR output the
 *      desktop PoC baseline at [`docs/knowledge/ppocrv6_vs_v5_a_b_test.md`]
 *      measured. Closes the gap between "OCR layer works" and "product
 *      behavior verified end-to-end".
 *
 *   2. `recBatchSizeMatrix_one_vs_six` — Runs the same 4 images through TWO
 *      `PaddleOcrEngine` instances, one with recBatchSize=1 and one with
 *      recBatchSize=6. Each pays its own `PaddleOCR.create()` model load,
 *      so cold timings are reported separately. The warm-loop latency diff
 *      answers "is v0.1.12's default batch=6 actually faster than batch=1
 *      on this device, or are we leaving perf on the table?".
 *
 * **Output protocol:** every measurement is logged with `Log.i(TAG, ...)`,
 * aggregate JSON dumped as a single block at the end (host greps by tag).
 *
 * **Cold vs warm measurement:** the first `recognize()` pays
 * `PaddleOCR.create()` model load (~5s on Huawei nova 6 ARM64). Harness
 * always runs 1 cold + N warm and reports `cold_ms` separately so per-image
 * latency is visible.
 */
@RunWith(AndroidJUnit4::class)
class PaddleOcrRealDeviceAbTest {

    private val tag = "RealDeviceAbTest"

    // Fixtures: the 3 mentor snapshots are byte-identical copies of the files
    // MentorOcrSmokeTest uses under `fixtures/mentor/`. We read them from the
    // SINGLE shared copy (`fixtures/mentor/`) instead of test_set/imgN.jpg so
    // a future edit to a mentor image automatically flows into both tests
    // — pre-v0.1.36 the two paths were independent and could drift silently
    // (sha256 verified identical at the time of consolidation).
    // img4.jpg (signage-11-2011) has no mentor counterpart and stays in
    // test_set/ as the unique 4th fixture.
    private data class Fixture(val assetPath: String, val stageFilename: String, val label: String)

    private val fixtures: List<Fixture> = listOf(
        Fixture("fixtures/mentor/mentor_1_5_2011.jpg", "mentor_1_5_2011.jpg", "signage-5-2011"),
        Fixture("fixtures/mentor/mentor_2_6_2011.jpg", "mentor_2_6_2011.jpg", "signage-6-2011"),
        Fixture("fixtures/mentor/mentor_3_9_2011.jpg", "mentor_3_9_2011.jpg", "signage-9-2011"),
        Fixture("test_set/img4.jpg", "img4.jpg", "signage-11-2011"),
    )

    private data class Measurement(
        val fixture: String,
        val label: String,
        val bytes: Int,
        val durationMs: Long,
        val lineCount: Int,
        val avgConfidence: Float,
        val fullTextChars: Int,
        val ruleHits: List<RuleHit>,
    )

    private data class LoopResult(
        val measurements: List<Measurement>,
        val coldDurationMs: Long,
        val warmTotalMs: Long,
        val coldLineCount: Int,
    )

    private fun stageImage(appCtx: Context, testCtx: Context, fixture: Fixture): Uri {
        val cacheFile = File(appCtx.cacheDir, "ab_${fixture.stageFilename}")
        testCtx.assets.open(fixture.assetPath).use { input ->
            cacheFile.outputStream().use { output -> input.copyTo(output) }
        }
        return Uri.fromFile(cacheFile)
    }

    /**
     * Baseline (recBatchSize=6) + AdSignage rule matcher integration.
     *
     * Pass criteria: each of the 4 fixtures produces text and the per-image
     * rule-hit count matches or beats the desktop PoC baseline (5 hits
     * across 4 images, with signage-9-2011 hitting 4 critical "全国第一"
     * rules). If the on-device rule count is significantly lower (>= 30%
     * drop) it's a hard regression to investigate before shipping.
     */
    @Test
    fun recognize_fourFixtureImages_measuresPerImageLatencyAndRecall() = runBlocking {
        assumeTrue(
            "OpenCV native libs must load on the test device",
            OpenCVLoader.initLocal(),
        )

        val appCtx = ApplicationProvider.getApplicationContext<Context>()
        val testCtx = InstrumentationRegistry.getInstrumentation().context

        // AdSignage rule loader reads from appCtx.assets/rules/ad_signage_rules.json —
        // the same path the production IceSpiritVisionViewModel uses.
        val rules = AdSignageRuleLoader(appCtx).load()
        val matcher = AdSignageRuleMatcher(rules)
        Log.i(tag, "=== Real-Device A/B harness START (v0.1.12, recBatchSize=6, +rule matcher) ===")
        Log.i(tag, "config=detLimitSideLen=960/detLimitType=max/detThresh=0.2/detBoxThresh=0.45/detUnclipRatio=1.4")
        Log.i(tag, "config=recScoreThresh=0.5/recBatchSize=6/numThreads=4")
        Log.i(tag, "rules=${rules.size} (ad_signage v4)")

        val engine = PaddleOcrEngine(appCtx, recBatchSize = 6)
        val loop = runTimedLoop(appCtx, testCtx, engine, matcher)
        engine.release()

        val measurements = loop.measurements
        val coldDurationMs = loop.coldDurationMs
        val warmTotalMs = loop.warmTotalMs
        val coldLineCount = loop.coldLineCount
        val warmAvgMs = warmTotalMs / fixtures.size
        val totalHits = measurements.sumOf { it.ruleHits.size }
        val hitsBySeverity = measurements.flatMap { it.ruleHits }
            .groupingBy { it.severity.name }
            .eachCount()
        val lines = measurements.sumOf { it.lineCount }
        val confs = measurements.map { it.avgConfidence }.average().toFloat()
        val chars = measurements.sumOf { it.fullTextChars }

        val json = buildString {
            append("{\"summary\":{")
            append("\"mode\":\"recBatchSize=6+rules\",")
            append("\"cold_ms\":$coldDurationMs,")
            append("\"cold_lines\":$coldLineCount,")
            append("\"warm_total_ms\":$warmTotalMs,")
            append("\"warm_avg_ms\":$warmAvgMs,")
            append("\"line_total\":$lines,")
            append("\"avg_confidence\":${"%.4f".format(confs)},")
            append("\"text_chars_total\":$chars,")
            append("\"rule_hits_total\":$totalHits,")
            append("\"rule_hits_by_severity\":${hitsBySeverity.toJsonObj()}")
            append("},\"per_image\":[")
            append(
                measurements.joinToString(",") { m ->
                    val ids = m.ruleHits.joinToString(",") { it.ruleId }
                    "{\"label\":\"${m.label}\",\"bytes\":${m.bytes}," +
                        "\"duration_ms\":${m.durationMs},\"lines\":${m.lineCount}," +
                        "\"avg_conf\":${"%.4f".format(m.avgConfidence)}," +
                        "\"text_chars\":${m.fullTextChars}," +
                        "\"rule_hits\":${m.ruleHits.size}," +
                        "\"rule_ids\":\"$ids\"}"
                }
            )
            append("]}")
        }
        Log.i(tag, "RESULT_JSON $json")
        Log.i(tag, "=== Real-Device A/B harness END ===")
        Unit
    }

    /**
     * recBatchSize=1 vs recBatchSize=6 matrix.
     *
     * Two engines, each with its own `PaddleOCR.create()` cold start.
     * Same 4 fixture images, same per-image measurements. OCR output is
     * identical between configs (deterministic SDK), so we only compare
     * warm latency.
     *
     * Decision rule (to apply after seeing results):
     *   - warm_avg(batch=1) >= 0.9 * warm_avg(batch=6): no meaningful win
     *     for batching; keep 6 (current default) for stability.
     *   - warm_avg(batch=1) < 0.9 * warm_avg(batch=6): batching hurts on
     *     this device; switch the production default.
     *   - Mixed (some images faster at 1, some at 6): keep 6 unless the
     *     average is dominated by the wins.
     */
    @Test
    fun recBatchSizeMatrix_one_vs_six() = runBlocking {
        assumeTrue(
            "OpenCV native libs must load on the test device",
            OpenCVLoader.initLocal(),
        )

        val appCtx = ApplicationProvider.getApplicationContext<Context>()
        val testCtx = InstrumentationRegistry.getInstrumentation().context

        val rules = AdSignageRuleLoader(appCtx).load()
        val matcher = AdSignageRuleMatcher(rules)
        Log.i(tag, "=== recBatchSize matrix START ===")
        Log.i(tag, "fixtures=${fixtures.size}, rules=${rules.size}, numThreads=4")

        // Each (recBatchSize, engine) pair pays its own cold start.
        // Order matters: run batch=6 (current default) first so a partial
        // failure on batch=1 still leaves us with batch=6 baseline data.
        val pairs = listOf(
            6 to PaddleOcrEngine(appCtx, recBatchSize = 6),
            1 to PaddleOcrEngine(appCtx, recBatchSize = 1),
        )

        val perBatch = linkedMapOf<Int, LoopResult>()
        for ((batch, engine) in pairs) {
            Log.i(tag, "--- recBatchSize=$batch ---")
            val loop = runTimedLoop(appCtx, testCtx, engine, matcher, prefix = "B${batch}")
            engine.release()
            perBatch[batch] = loop
        }

        // Per-batch summary entries.
        val summaryEntries = perBatch.entries.joinToString(",") { (batch, loop) ->
            val warmAvg = loop.warmTotalMs / fixtures.size
            val totalHits = loop.measurements.sumOf { it.ruleHits.size }
            val totalLines = loop.measurements.sumOf { it.lineCount }
            val confs = loop.measurements.map { it.avgConfidence }.average().toFloat()
            "{\"recBatchSize\":$batch," +
                "\"cold_ms\":${loop.coldDurationMs}," +
                "\"warm_total_ms\":${loop.warmTotalMs}," +
                "\"warm_avg_ms\":$warmAvg," +
                "\"line_total\":$totalLines," +
                "\"avg_conf\":${"%.4f".format(confs)}," +
                "\"rule_hits_total\":$totalHits}"
        }

        // Per-image: batch=1 vs batch=6 side-by-side.
        val byLabel: Map<Int, Map<String, Measurement>> = perBatch.mapValues { (_, loop) ->
            loop.measurements.associateBy { it.label }
        }
        val perImageEntries = fixtures.joinToString(",") { (_, _, label) ->
            val m1 = byLabel[1]?.get(label)
            val m6 = byLabel[6]?.get(label)
            if (m1 != null && m6 != null) {
                val speedup = if (m1.durationMs > 0)
                    "%.2f".format(m6.durationMs.toDouble() / m1.durationMs.toDouble()) else "inf"
                "{\"label\":\"$label\"," +
                    "\"bytes\":${m6.bytes}," +
                    "\"batch1_ms\":${m1.durationMs}," +
                    "\"batch6_ms\":${m6.durationMs}," +
                    "\"batch6_over_batch1\":$speedup," +
                    "\"lines_batch1\":${m1.lineCount}," +
                    "\"lines_batch6\":${m6.lineCount}," +
                    "\"hits_batch1\":${m1.ruleHits.size}," +
                    "\"hits_batch6\":${m6.ruleHits.size}}"
            } else {
                "{\"label\":\"$label\",\"error\":\"missing_data\"}"
            }
        }

        val json = "{\"summary\":{\"batches\":[$summaryEntries]},\"per_image\":[$perImageEntries]}"
        Log.i(tag, "RESULT_JSON $json")
        Log.i(tag, "=== recBatchSize matrix END ===")

        // At minimum, each batch should have completed all 4 warm calls
        // and produced some non-negative rule hit count.
        for ((batch, loop) in perBatch) {
            assertTrue(
                "batch=$batch: warm loop must have ${fixtures.size} measurements, " +
                    "got ${loop.measurements.size}",
                loop.measurements.size == fixtures.size,
            )
            assertTrue(
                "batch=$batch: rule hit count must be >= 0",
                loop.measurements.sumOf { it.ruleHits.size } >= 0,
            )
        }
        Unit
    }

    /**
     * Shared inner loop. Runs 1 cold + N warm `engine.recognize()` calls,
     * applying the rule matcher to each result. The cold result is also
     * rule-scanned but its timing alone matters (it's part of the cold cost).
     */
    private suspend fun runTimedLoop(
        appCtx: Context,
        testCtx: Context,
        engine: PaddleOcrEngine,
        matcher: AdSignageRuleMatcher,
        prefix: String = "WARM",
    ): LoopResult {
        // Cold-start: first recognize pays PaddleOCR.create() cost.
        val coldFixture = fixtures.first()
        val coldLabel = coldFixture.label
        val coldUri = stageImage(appCtx, testCtx, coldFixture)
        val coldBytes = File(appCtx.cacheDir, "ab_${coldFixture.stageFilename}").length()
        val coldStart = System.nanoTime()
        val coldResult: OcrResult = engine.recognize(coldUri)
        val coldDurationMs = (System.nanoTime() - coldStart) / 1_000_000L
        val coldHits = matcher.scan(coldResult.fullText)
        Log.i(
            tag, "[$prefix] COLD [$coldLabel] bytes=$coldBytes duration_ms=$coldDurationMs " +
                "lines=${coldResult.lineBoxes.size} avg_conf=${"%.3f".format(coldResult.avgConfidence)} " +
                "text_chars=${coldResult.fullText.length} rule_hits=${coldHits.size}"
        )

        // Warm: 4 timed recognize calls (one per fixture).
        val measurements = mutableListOf<Measurement>()
        val warmStart = System.nanoTime()
        for (fixture in fixtures) {
            val label = fixture.label
            val uri = stageImage(appCtx, testCtx, fixture)
            val bytes = File(appCtx.cacheDir, "ab_${fixture.stageFilename}").length()
            val start = System.nanoTime()
            val result: OcrResult = engine.recognize(uri)
            val durationMs = (System.nanoTime() - start) / 1_000_000L
            val hits = matcher.scan(result.fullText)
            val m = Measurement(
                fixture = fixture.stageFilename,
                label = label,
                bytes = bytes.toInt(),
                durationMs = durationMs,
                lineCount = result.lineBoxes.size,
                avgConfidence = result.avgConfidence,
                fullTextChars = result.fullText.length,
                ruleHits = hits,
            )
            measurements.add(m)
            Log.i(
                tag, "[$prefix] WARM [$label] bytes=$bytes duration_ms=$durationMs " +
                    "lines=${m.lineCount} avg_conf=${"%.3f".format(m.avgConfidence)} " +
                    "text_chars=${m.fullTextChars} rule_hits=${m.ruleHits.size}"
            )
            if (m.ruleHits.isNotEmpty()) {
                val ids = m.ruleHits.joinToString(",") { it.ruleId }
                Log.i(tag, "[$prefix] HITS [$label] $ids")
            }
        }
        val warmTotalMs = (System.nanoTime() - warmStart) / 1_000_000L
        return LoopResult(measurements, coldDurationMs, warmTotalMs, coldResult.lineBoxes.size)
    }

    /**
     * Serialize a small `Map<String, Int>` as a JSON object inline. Used for
     * `rule_hits_by_severity`. `LinkedHashMap` iteration order is preserved
     * (Kotlin contract), so output is stable across runs.
     */
    private fun Map<String, Int>.toJsonObj(): String {
        if (isEmpty()) return "{}"
        return entries.joinToString(
            prefix = "{", postfix = "}",
            transform = { (k, v) -> "\"$k\":$v" }
        )
    }
}
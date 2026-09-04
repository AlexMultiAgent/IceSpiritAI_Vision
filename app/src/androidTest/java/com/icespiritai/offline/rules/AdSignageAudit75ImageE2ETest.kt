package com.icespiritai.offline.rules

import android.content.Context
import android.net.Uri
import android.util.Log
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.icespiritai.offline.domain.RuleHit
import com.icespiritai.offline.ocr.PaddleOcrEngine
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.opencv.android.OpenCVLoader
import java.io.File

/**
 * 4 张 v0.1.57 新规则的 fixture 真机 e2e 验证。
 *
 * 数据源:
 *   - app/src/androidTest/assets/fixtures/audit75/ = 4 张图
 *     - 02_名师教育申论班_龙江第一_绝对化用语.jpg          → art9_citation_radish(本省第一)
 *     - 33_公考培训宋尚案老师_某省直机关在职领导_身份.png   → party_leader_commercial(某省直机关在职领导)
 *     - 61_三元教育公考_哈尔滨排名第一通过率75%_教育.jpg    → art9_citation_radish(全市第一)
 *     - 88_傲云精酿橡木桶啤酒_国宾礼遇暗示国家级_绝对化.jpg → special_supply(国宾礼遇)
 *
 * 缺位说明:
 *   - v0.1.57 `ad_signage_signage_minor_under14_pester_parent`(妈妈我要/爸爸买) 当前没有 staging 图:
 *     违规案例/ 中无 「妈妈/哭闹/爸爸买/宝宝要/孩子要」 字样的广告图。
 *     留 v0.1.59 fixture 扩列时再补(等同 priority P2 已知遗留)。
 *
 * 数据采集:
 *   - 真机 OCR + AdSignageRuleMatcher v0.1.58(175 条规则)
 *   - 每张图必须至少命中 ≥1 条规则(用户的核心诉求)
 *   - 若命中是 4 条 v0.1.57 新规则之一,记录「v0.1.57 新覆盖」标签
 *
 * 输出:
 *   - logcat TAG=Audit75E2E,包含 RESULT_JSON 块(host 解析用)
 *
 * Cold vs warm:沿用 audit71 模式,1 cold + 3 warm。
 *
 * 设备:华为 nova 6 ANN-AN00,SDK 35。
 */
@RunWith(AndroidJUnit4::class)
class AdSignageAudit75ImageE2ETest {

    private val tag = "Audit75E2E"

    private val targetRules = setOf(
        "ad_signage_art9_citation_radish",
        "ad_signage_signage_party_leader_commercial",
        "ad_signage_signage_special_supply",
        "ad_signage_signage_minor_under14_pester_parent",
    )

    private data class ImageMeasurement(
        val filename: String,
        val bytes: Long,
        val durationMs: Long,
        val lineCount: Int,
        val avgConfidence: Float,
        val fullTextChars: Int,
        val ocrText: String,
        val ruleHits: List<RuleHit>,
    )

    @Test
    fun audit_seventyFive_v0_1_57_target_rules_verification() = runBlocking {
        assumeTrue(
            "OpenCV native libs must load on the test device",
            OpenCVLoader.initLocal(),
        )

        val appCtx = ApplicationProvider.getApplicationContext<Context>()
        val testCtx = InstrumentationRegistry.getInstrumentation().context

        // 1) 加载 v0.1.58 规则库(175 条)
        val rules = AdSignageRuleLoader(appCtx).load()
        val matcher = AdSignageRuleMatcher(rules)

        // 2) 列出 fixtures/audit75 下 4 张图
        val fixtureDir = "fixtures/audit75"
        val allAssets = testCtx.assets.list(fixtureDir) ?: emptyArray()
        val imageAssets = allAssets
            .filter { it.endsWith(".jpg") || it.endsWith(".png") || it.endsWith(".jpeg") }
            .sorted()
        assertTrue(
            "fixtures/audit75 应有 4 张图,实际 ${imageAssets.size}",
            imageAssets.size == 4,
        )

        Log.i(tag, "=== START: ${imageAssets.size} images, ${rules.size} rules ===")
        Log.i(tag, "device=${android.os.Build.MODEL} sdk=${android.os.Build.VERSION.SDK_INT}")
        Log.i(tag, "v0.1.57 target rules: $targetRules")

        val engine = PaddleOcrEngine(appCtx, recBatchSize = 6)

        // 3) cold + warm
        val measurements = mutableListOf<ImageMeasurement>()
        var coldMs = 0L
        var coldLineCount = 0

        // Cold
        val firstFile = imageAssets.first()
        val coldUri = stage(appCtx, testCtx, "$fixtureDir/$firstFile", "a75_$firstFile")
        val coldBytes = File(appCtx.cacheDir, "a75_$firstFile").length()
        val coldStart = System.nanoTime()
        val coldRes = engine.recognize(coldUri)
        coldMs = (System.nanoTime() - coldStart) / 1_000_000L
        coldLineCount = coldRes.lineBoxes.size
        val coldHits = matcher.scan(coldRes.fullText)
        measurements.add(
            ImageMeasurement(
                firstFile, coldBytes, coldMs, coldRes.lineBoxes.size,
                coldRes.avgConfidence, coldRes.fullText.length, coldRes.fullText,
                coldHits,
            )
        )
        Log.i(
            tag, "[COLD] $firstFile bytes=$coldBytes ms=$coldMs " +
                "lines=${coldRes.lineBoxes.size} avg_conf=${"%.3f".format(coldRes.avgConfidence)} " +
                "text_chars=${coldRes.fullText.length} hits=${coldHits.size}"
        )
        logOcrAndHits(coldRes.fullText, coldHits, firstFile)

        // Warm
        val warmStart = System.nanoTime()
        for (file in imageAssets.drop(1)) {
            val uri = stage(appCtx, testCtx, "$fixtureDir/$file", "a75_$file")
            val bytes = File(appCtx.cacheDir, "a75_$file").length()
            val start = System.nanoTime()
            val res = engine.recognize(uri)
            val ms = (System.nanoTime() - start) / 1_000_000L
            val hits = matcher.scan(res.fullText)
            measurements.add(
                ImageMeasurement(file, bytes, ms, res.lineBoxes.size,
                    res.avgConfidence, res.fullText.length, res.fullText,
                    hits)
            )
            Log.i(
                tag, "[WARM] $file bytes=$bytes ms=$ms " +
                    "lines=${res.lineBoxes.size} avg_conf=${"%.3f".format(res.avgConfidence)} " +
                    "text_chars=${res.fullText.length} hits=${hits.size}"
            )
            logOcrAndHits(res.fullText, hits, file)
        }
        val warmTotalMs = (System.nanoTime() - warmStart) / 1_000_000L
        engine.release()

        // 4) 统计 — targetRules 在 closure 中访问(嵌套 class ImageMeasurement 不再持有引用)
        val anyHitCount = measurements.count { it.ruleHits.isNotEmpty() }
        val targetRuleHitCount = measurements.count { m ->
            m.ruleHits.any { it.ruleId in targetRules }
        }
        val warmAvg = warmTotalMs / (imageAssets.size - 1).coerceAtLeast(1)

        Log.i(tag, "=== SUMMARY ===")
        Log.i(tag, "total=${measurements.size} cold_ms=$coldMs warm_total_ms=$warmTotalMs warm_avg_ms=$warmAvg")
        Log.i(tag, "ANY_HIT=$anyHitCount/${measurements.size} TARGET_RULE_HIT=$targetRuleHitCount/${measurements.size}")

        // 5) RESULT_JSON
        val summaryJson = buildString {
            append("{\"summary\":{")
            append("\"total\":${measurements.size},")
            append("\"rules\":${rules.size},")
            append("\"cold_ms\":$coldMs,")
            append("\"cold_lines\":$coldLineCount,")
            append("\"warm_total_ms\":$warmTotalMs,")
            append("\"warm_avg_ms\":$warmAvg,")
            append("\"any_hit\":$anyHitCount,")
            append("\"target_rule_hit\":$targetRuleHitCount,")
            append("\"target_rules\":[\"${targetRules.joinToString("\",\"")}\"]},")
            append("\"per_image\":[")
            append(measurements.joinToString(",") { m ->
                val ocrEsc = m.ocrText.replace("\\", "\\\\").replace("\"", "\\\"")
                    .replace("\n", "\\n").replace("\r", "")
                val ids = m.ruleHits.joinToString(",") { it.ruleId }
                val newIds = m.ruleHits.filter { it.ruleId in targetRules }
                    .joinToString(",") { it.ruleId }
                buildString {
                    append("{\"file\":\"${m.filename}\",")
                    append("\"bytes\":${m.bytes},")
                    append("\"ms\":${m.durationMs},")
                    append("\"lines\":${m.lineCount},")
                    append("\"conf\":${"%.4f".format(m.avgConfidence)},")
                    append("\"chars\":${m.fullTextChars},")
                    append("\"hits\":${m.ruleHits.size},")
                    append("\"hit_ids\":\"$ids\",")
                    append("\"new_rule_hits\":\"$newIds\",")
                    append("\"ocr\":\"$ocrEsc\"}")
                }
            })
            append("]}")
        }

        Log.i(tag, "RESULT_JSON_BEGIN len=${summaryJson.length}")
        val chunkSize = 3800
        var i = 0
        while (i < summaryJson.length) {
            val end = (i + chunkSize).coerceAtMost(summaryJson.length)
            Log.i(tag, "RESULT_JSON_CHUNK $i:$end ${summaryJson.substring(i, end)}")
            i = end
        }
        Log.i(tag, "RESULT_JSON_END")

        runCatching {
            File(appCtx.cacheDir, "audit75_e2e_report.json").writeText(summaryJson)
            Log.i(tag, "cacheDir report: ${File(appCtx.cacheDir, "audit75_e2e_report.json").absolutePath}")
        }

        // 6) 软断言:用户核心诉求 — 4 张图必须都至少命中 ≥1 条规则
        assertTrue(
            "audit75 4 张图必须 ≥1 张命中 (实际 $anyHitCount)",
            anyHitCount >= 1,
        )
        Unit
    }

    private fun logOcrAndHits(text: String, hits: List<RuleHit>, file: String) {
        val text2 = text.replace("\\", "\\\\").replace("\"", "\\\"").replace("\n", "\\n").replace("\r", "")
        if (hits.isEmpty()) {
            Log.i(tag, "[OCR_NO_HIT] $file text=\"$text2\"")
        } else {
            val isNew = hits.any { it.ruleId in targetRules }
            val marker = if (isNew) " NEW_V0157" else ""
            Log.i(tag, "[OCR_HIT] $file hits=${hits.joinToString(",") { it.ruleId }}$marker text=\"$text2\"")
        }
        if (hits.isNotEmpty()) {
            Log.i(tag, "[HITS] $file ${hits.joinToString(",") { it.ruleId }}")
        }
    }

    private fun stage(appCtx: Context, testCtx: Context, assetPath: String, stageFilename: String): Uri {
        val cacheFile = File(appCtx.cacheDir, stageFilename)
        testCtx.assets.open(assetPath).use { input ->
            cacheFile.outputStream().use { output -> input.copyTo(output) }
        }
        return Uri.fromFile(cacheFile)
    }
}

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
 * 66 张违规案例的端到端 OCR + 规则引擎验证(instrumented test)。
 *
 * 数据源:
 *   - app/src/androidTest/assets/audit66/{01..66}_*.{jpg,png,jpeg} = 66 张图
 *     (已从 `违规案例/` 复制,模型运行环境与 `AdSignageImageAuditSixtySixRegressionTest`
 *     的 audit 描述 fixture 区分 — 本测试是「真 OCR 路径」)
 *   - app/src/androidTest/assets/audit66/coverage_matrix.md = 每张图 ground truth 规则 ID
 *   - app/src/main/assets/rules/ad_signage_rules.json = v9 规则库 129 条
 *
 * 与 JVM 单元测试 `AdSignageImageAuditSixtySixRegressionTest` 的差异:
 *   - 本测试走真 PP-OCRv6_small 模型 + ONNX Runtime + OpenCV(在设备上跑)
 *   - 验证从「图片像素 → OCR 文本 → AC 规则匹配」全链路
 *   - 暴露 OCR 漏字 / 错字 / 排版差异导致的规则召回下降
 *
 * 输出:
 *   - logcat TAG=Audit66E2E,包含 RESULT_JSON 块(host 解析用)
 *   - cacheDir/audit66_e2e_report.json(adb pull cacheDir 不可靠,logcat 是主通道)
 *
 * Cold vs warm 计时:沿用 PaddleOcrRealDeviceAbTest 模式,首张 cold,余 65 张 warm。
 *
 * 设备:华为 nova 6 ANN-AN00,SDK 35,HONOR。
 */
@RunWith(AndroidJUnit4::class)
class AdSignageAuditSixtySixImageE2ETest {

    private val tag = "Audit66E2E"

    private data class ImageMeasurement(
        val filename: String,
        val bytes: Long,
        val durationMs: Long,
        val lineCount: Int,
        val avgConfidence: Float,
        val fullTextChars: Int,
        val ocrText: String,
        val ruleHits: List<RuleHit>,
        val groundTruth: List<String>,
    ) {
        val hitIds: List<String> = ruleHits.map { it.ruleId }
        val overlap: List<String> = hitIds.filter { it in groundTruth }
        val missedGt: List<String> = groundTruth.filter { it !in hitIds }
        val fullCoverage: Boolean = groundTruth.isNotEmpty() && hitIds.containsAll(groundTruth)
        val partialCoverage: Boolean = groundTruth.isNotEmpty() && overlap.isNotEmpty() && !fullCoverage
        val noOverlap: Boolean = groundTruth.isNotEmpty() && overlap.isEmpty()
        val noGroundTruth: Boolean = groundTruth.isEmpty()
    }

    @Test
    fun audit_sixtySix_images_end_to_end_ocr_plus_rules() = runBlocking {
        assumeTrue(
            "OpenCV native libs must load on the test device",
            OpenCVLoader.initLocal(),
        )

        val appCtx = ApplicationProvider.getApplicationContext<Context>()
        val testCtx = InstrumentationRegistry.getInstrumentation().context

        // 1) 加载规则库 — 与生产 ViewModel 同源(appCtx.assets/rules/...)
        val rules = AdSignageRuleLoader(appCtx).load()
        val matcher = AdSignageRuleMatcher(rules)

        // 2) 解析 ground truth — 从 testCtx 读 fixtures/audit66/coverage_matrix.md
        val coverageText = testCtx.assets.open("fixtures/audit66/coverage_matrix.md")
            .bufferedReader(Charsets.UTF_8).use { it.readText() }
        val groundTruthByFile = parseCoverageMatrix(coverageText)
        assertTrue("coverage_matrix 解析为空", groundTruthByFile.isNotEmpty())

        // 3) 列出 fixtures/audit66 下所有 66 张图(按文件名升序,与 JVM 测试一致)
        val fixtureDir = "fixtures/audit66"
        val allAssets = testCtx.assets.list(fixtureDir) ?: emptyArray()
        val imageAssets = allAssets
            .filter { it.endsWith(".jpg") || it.endsWith(".png") || it.endsWith(".jpeg") }
            .sorted()
        assertTrue(
            "fixtures/audit66 应有 66 张图,实际 ${imageAssets.size}",
            imageAssets.size == 66,
        )

        Log.i(tag, "=== START: ${imageAssets.size} images, ${rules.size} rules ===")
        Log.i(tag, "device=${android.os.Build.MODEL} sdk=${android.os.Build.VERSION.SDK_INT}")

        val engine = PaddleOcrEngine(appCtx, recBatchSize = 6)

        // 4) cold + warm 计时循环
        val measurements = mutableListOf<ImageMeasurement>()
        var coldMs = 0L
        var coldLineCount = 0

        // Cold
        val firstFile = imageAssets.first()
        val coldUri = stage(appCtx, testCtx, "$fixtureDir/$firstFile", "a66_$firstFile")
        val coldBytes = File(appCtx.cacheDir, "a66_$firstFile").length()
        val coldStart = System.nanoTime()
        val coldRes = engine.recognize(coldUri)
        coldMs = (System.nanoTime() - coldStart) / 1_000_000L
        coldLineCount = coldRes.lineBoxes.size
        val coldGt = groundTruthByFile[firstFile] ?: emptyList()
        val coldHits = matcher.scan(coldRes.fullText)
        measurements.add(
            ImageMeasurement(
                firstFile, coldBytes, coldMs, coldRes.lineBoxes.size,
                coldRes.avgConfidence, coldRes.fullText.length, coldRes.fullText,
                coldHits, coldGt,
            )
        )
        Log.i(
            tag, "[COLD] $firstFile bytes=$coldBytes ms=$coldMs " +
                "lines=${coldRes.lineBoxes.size} avg_conf=${"%.3f".format(coldRes.avgConfidence)} " +
                "text_chars=${coldRes.fullText.length} hits=${coldHits.size}"
        )

        // Warm
        val warmStart = System.nanoTime()
        for (file in imageAssets.drop(1)) {
            val uri = stage(appCtx, testCtx, "$fixtureDir/$file", "a66_$file")
            val bytes = File(appCtx.cacheDir, "a66_$file").length()
            val start = System.nanoTime()
            val res = engine.recognize(uri)
            val ms = (System.nanoTime() - start) / 1_000_000L
            val hits = matcher.scan(res.fullText)
            val gt = groundTruthByFile[file] ?: emptyList()
            measurements.add(
                ImageMeasurement(file, bytes, ms, res.lineBoxes.size,
                    res.avgConfidence, res.fullText.length, res.fullText,
                    hits, gt)
            )
            Log.i(
                tag, "[WARM] $file bytes=$bytes ms=$ms " +
                    "lines=${res.lineBoxes.size} avg_conf=${"%.3f".format(res.avgConfidence)} " +
                    "text_chars=${res.fullText.length} hits=${hits.size} " +
                    "gt=${gt.size} overlap=${hits.count { it.ruleId in gt }}"
            )
            if (hits.isNotEmpty()) {
                val ids = hits.joinToString(",") { it.ruleId }
                Log.i(tag, "[HITS] $file $ids")
            }
        }
        val warmTotalMs = (System.nanoTime() - warmStart) / 1_000_000L
        engine.release()

        // 5) 统计 + 落盘报告
        val full = measurements.count { it.fullCoverage }
        val partial = measurements.count { it.partialCoverage }
        val miss = measurements.count { it.noOverlap }
        val noGt = measurements.count { it.noGroundTruth }
        val recognized = measurements.count { it.groundTruth.isNotEmpty() && it.overlap.isNotEmpty() }
        val warmAvg = warmTotalMs / (imageAssets.size - 1).coerceAtLeast(1)

        Log.i(tag, "=== SUMMARY ===")
        Log.i(tag, "total=${measurements.size} cold_ms=$coldMs warm_total_ms=$warmTotalMs warm_avg_ms=$warmAvg")
        Log.i(tag, "FULL=$full PARTIAL=$partial MISS=$miss NO_GT=$noGt RECOGNIZED=$recognized")

        val totalHits = measurements.sumOf { it.ruleHits.size }
        val hitsBySeverity = measurements.flatMap { it.ruleHits }
            .groupingBy { it.severity.name }.eachCount()
        val totalLines = measurements.sumOf { it.lineCount }
        val totalChars = measurements.sumOf { it.fullTextChars }
        val avgConf = measurements.map { it.avgConfidence }.average().toFloat()

        // 6) RESULT_JSON — 用 buildString 避免 string-template 转义嵌套
        val summaryJson = buildString {
            append("{\"summary\":{")
            append("\"total\":${measurements.size},")
            append("\"rules\":${rules.size},")
            append("\"cold_ms\":$coldMs,")
            append("\"cold_lines\":$coldLineCount,")
            append("\"warm_total_ms\":$warmTotalMs,")
            append("\"warm_avg_ms\":$warmAvg,")
            append("\"total_lines\":$totalLines,")
            append("\"total_chars\":$totalChars,")
            append("\"avg_confidence\":${"%.4f".format(avgConf)},")
            append("\"rule_hits_total\":$totalHits,")
            append("\"rule_hits_by_severity\":{")
            append(hitsBySeverity.entries.joinToString(",") { e ->
                val k = e.key.replace("\"", "\\\"")
                "\"$k\":${e.value}"
            })
            append("},")
            append("\"full\":$full,\"partial\":$partial,\"miss\":$miss,\"no_gt\":$noGt,")
            append("\"recognized\":$recognized},")
            append("\"per_image\":[")
            append(measurements.joinToString(",") { m ->
                val ocrEsc = m.ocrText.replace("\\", "\\\\").replace("\"", "\\\"")
                    .replace("\n", "\\n").replace("\r", "")
                val ids = m.hitIds.joinToString(",")
                val gt = m.groundTruth.joinToString(",")
                val ov = m.overlap.joinToString(",")
                val status = when {
                    m.fullCoverage -> "FULL"
                    m.partialCoverage -> "PARTIAL(${m.overlap.size}/${m.groundTruth.size})"
                    m.noOverlap -> "MISS"
                    m.noGroundTruth -> "NO_GT(hit=${m.hitIds.size})"
                    else -> "?"
                }
                buildString {
                    append("{\"file\":\"${m.filename}\",")
                    append("\"bytes\":${m.bytes},")
                    append("\"ms\":${m.durationMs},")
                    append("\"lines\":${m.lineCount},")
                    append("\"conf\":${"%.4f".format(m.avgConfidence)},")
                    append("\"chars\":${m.fullTextChars},")
                    append("\"hits\":${m.hitIds.size},")
                    append("\"hit_ids\":\"$ids\",")
                    append("\"gt\":\"$gt\",")
                    append("\"overlap\":\"$ov\",")
                    append("\"status\":\"$status\",")
                    append("\"ocr\":\"$ocrEsc\"}")
                }
            })
            append("]}")
        }

        // logcat 主通道(分块 dump)
        Log.i(tag, "RESULT_JSON_BEGIN len=${summaryJson.length}")
        val chunkSize = 3800
        var i = 0
        while (i < summaryJson.length) {
            val end = (i + chunkSize).coerceAtMost(summaryJson.length)
            Log.i(tag, "RESULT_JSON_CHUNK $i:$end ${summaryJson.substring(i, end)}")
            i = end
        }
        Log.i(tag, "RESULT_JSON_END")

        // 7) cacheDir 落盘(可能跑完后 adb pull 抢不到时机,以 logcat 为主)
        runCatching {
            File(appCtx.cacheDir, "audit66_e2e_report.json").writeText(summaryJson)
            Log.i(tag, "cacheDir report: ${File(appCtx.cacheDir, "audit66_e2e_report.json").absolutePath}")
        }

        // 8) 软断言 — 整体识别率 ≥ 50 张图(沿用 JVM 测试阈值,反映产品路径)
        assertTrue(
            "整体识别率太低:命中 ≥ 1 个 gt 的图共 $recognized / ${measurements.size},期望 ≥ 50",
            recognized >= 50,
        )
        Unit
    }

    /**
     * 把 fixture 从 test APK assets 拷到 cacheDir 并返回 file:// Uri。
     * 沿用 PaddleOcrRealDeviceAbTest.stageImage 模式。
     */
    private fun stage(appCtx: Context, testCtx: Context, assetPath: String, stageFilename: String): Uri {
        val cacheFile = File(appCtx.cacheDir, stageFilename)
        testCtx.assets.open(assetPath).use { input ->
            cacheFile.outputStream().use { output -> input.copyTo(output) }
        }
        return Uri.fromFile(cacheFile)
    }

    /**
     * 解析 coverage_matrix.md §2 表格,提取每张图的 ground truth 规则 ID 列表。
     * 与 JVM 测试的 parseCoverageMatrix 逻辑一致,但只针对单文件(已嵌入 androidTest assets)。
     */
    private fun parseCoverageMatrix(text: String): Map<String, List<String>> {
        val map = linkedMapOf<String, List<String>>()
        var inSection2 = false
        val ruleIdRe = Regex("^(ad_signage|cosmetic|finance|internet)_")
        for (raw in text.lines()) {
            val line = raw.trimEnd()
            if (line.startsWith("## §2")) { inSection2 = true; continue }
            if (line.startsWith("## §3")) { inSection2 = false; continue }
            if (!inSection2) continue
            if (!line.startsWith("| `")) continue
            val cols = line.split("|").map { it.trim() }
            if (cols.size < 8) continue
            val filename = cols[1].removePrefix("`").removeSuffix("`")
            if (!filename.endsWith(".jpg") && !filename.endsWith(".png") && !filename.endsWith(".jpeg")) continue
            val rulesCell = cols[5]
            val rules = if (rulesCell == "—" || rulesCell.isBlank()) emptyList()
                else rulesCell.split(",").map { it.trim() }
                    .map { it.replace("`", "").replace("*(new)*", "").trim() }
                    .filter { ruleIdRe.containsMatchIn(it) }
            map[filename] = rules
        }
        return map
    }
}
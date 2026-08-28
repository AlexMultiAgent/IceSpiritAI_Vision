package com.icespiritai.offline.rules

import com.icespiritai.offline.domain.TextNormalizer
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

/**
 * 66 张违规案例的**真 OCR** 规则识别实测(全量报告,无硬断言)。
 *
 * 与 [AdSignageImageAuditSixtySixRegressionTest] 的差异 —— 两个测试并行,各管一摊:
 *   - 那个测试的 fixture 文本是从 audit 文档(文件名提示词 + 违规描述 + 关联法条)拼出来的,
 *     衡量的是「规则关键词 vs 人工审查结论」的对齐度,带硬断言做回归 pin。
 *   - 本测试的 fixture 文本是 **真 OCR 输出**(`tools/ocr-audit66-fixtures.py` 用
 *     ONNX Runtime 1.20.1 + PP-OCRv6_small det/rec 跑 66 张原图录下来的,与 Android
 *     `ice_ocr_rules` profile 同模型同栈),衡量的是「端侧真实可读到的字 → 规则命中」的
 *     真实识别率。因为受 OCR 还原度影响,本测试**不加硬断言**,只出全量报告。
 *
 * 数据源:
 *   - `违规案例/_coverage_matrix.md` §2 = 每张图的 ground truth 规则 ID 集合 + audit 状态
 *   - `违规案例/_audit_gaps.md` 每节 = 每张图的违规描述 + 关联法条(本测试只用于张数一致性校验)
 *   - `app/src/test/resources/fixtures/audit66_ocr/<NN>_<slug>.txt` = 真 OCR 文本
 *
 * 输出:
 *   - stdout 实时打每张图状态(FULL / PARTIAL / MISS / no-gt)
 *   - `build/reports/audit66_ocr_<timestamp>.md` 落盘报告(§1 覆盖统计 + §2 全量命中清单)
 *
 * fixture 目录缺失时打 warning 后直接返回(不 fail),提示先跑
 * `python tools/ocr-audit66-fixtures.py`。
 */
class AdSignageOcrImageAudit66Test {

    private fun projectRoot(): File {
        val candidates = listOf(
            File("."),
            File(".."),
            File("../.."),
            File("../../.."),
        )
        return candidates.firstOrNull { File(it, "违规案例").exists() && File(it, "违规案例").isDirectory }
            ?: error("project root with 违规案例/ not found (cwd=${System.getProperty("user.dir")})")
    }

    private fun rulesFile(root: File): File {
        return listOf(
            File(root, "app/src/main/assets/rules/ad_signage_rules.json"),
            File(root, "src/main/assets/rules/ad_signage_rules.json"),
            File(root, "app/build/generated/assets/rules/ad_signage_rules.json"),
        ).firstOrNull { it.exists() && it.length() > 100 }
            ?: error("ad_signage_rules.json not found under ${root.absolutePath}")
    }

    private fun loadRules(root: File): List<AdSignageRule> {
        val raw = rulesFile(root).readText(Charsets.UTF_8)
        return Json { ignoreUnknownKeys = true; isLenient = true }
            .decodeFromString(AdSignageRuleSet.serializer(), raw).rules
    }

    /**
     * 解析 `_coverage_matrix.md` §2 表格:
     *   | `<文件名>` | `<bucket>` | `<severity>` | `<n>` | `<rules>` | `<status>` |
     * 仅收集 §2 段(`## §2` 起到 `## §3` 止);rules cell 为 "—" 时 ground truth 为空。
     */
    private fun parseCoverageMatrix(file: File): Map<String, Pair<List<String>, String>> {
        val text = file.readText(Charsets.UTF_8)
        val map = linkedMapOf<String, Pair<List<String>, String>>()
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
            val status = cols[6].removePrefix("`").removeSuffix("`").trim()
            map[filename] = rules to status
        }
        return map
    }

    /**
     * 解析 `_audit_gaps.md` 66 节,提取每节的「文件名」「违规描述」「关联法条」。
     * 节标题形如 `## 01_xxx_yyy.ext`;非图节(§桶汇总 / §新规则候选清单 等)标题不以
     * .jpg/.png/.jpeg 结尾,会被跳过。
     */
    private fun parseAuditGaps(file: File): Map<String, Pair<String, String>> {
        val text = file.readText(Charsets.UTF_8)
        val map = linkedMapOf<String, Pair<String, String>>()
        var currentFile: String? = null
        var currentDesc: String? = null
        var currentLegal: String? = null
        fun flush() {
            if (currentFile != null) {
                map[currentFile!!] = (currentDesc ?: "") to (currentLegal ?: "")
            }
        }
        for (line in text.lines()) {
            if (line.startsWith("## ")) {
                flush()
                currentFile = null
                currentDesc = null
                currentLegal = null
                val heading = line.removePrefix("## ").trim()
                if (heading.endsWith(".jpg") || heading.endsWith(".png") || heading.endsWith(".jpeg")) {
                    currentFile = heading
                }
                continue
            }
            val m = Regex("^\\|\\s*(文件名|违规描述|关联法条)\\s*\\|\\s*(.+?)\\s*\\|\\s*$").matchEntire(line)
            if (m != null) {
                val key = m.groupValues[1]
                val value = m.groupValues[2]
                when (key) {
                    "文件名" -> if (currentFile == null) currentFile = value
                    "违规描述" -> currentDesc = value
                    "关联法条" -> currentLegal = value
                }
            }
        }
        flush()
        return map
    }

    /**
     * 原图文件名 → OCR fixture 文件名:去扩展名,序号前缀后的中段 `_` 改 `-`,补 `.txt`。
     * 例:`01_碧桂园华美天樾_中国地产三强_绝对化与数据引用.jpg`
     *   → `01_碧桂园华美天樾-中国地产三强-绝对化与数据引用.txt`
     * 与 `tools/ocr-audit66-fixtures.py` 落盘时的命名规则一致(manifest.json 66/66 对得上)。
     */
    private fun fixtureNameFor(imageFilename: String): String {
        val stem = imageFilename.substringBeforeLast(".")
        val i = stem.indexOfFirst { !it.isDigit() }
        val prefix = if (i > 0) stem.substring(0, i) else ""
        val rest = if (i >= 0) stem.substring(i).trimStart('_') else stem
        val slug = rest.replace("_", "-")
        return if (prefix.isNotEmpty()) "${prefix}_$slug.txt" else "$slug.txt"
    }

    private data class ImageResult(
        val filename: String,
        val auditStatus: String,
        val groundTruth: List<String>,
        val actualHits: List<String>,
        val ocrText: String,
        val hasFixture: Boolean,
    ) {
        val overlap: List<String> get() = actualHits.filter { it in groundTruth }
        val fullCoverage: Boolean get() = groundTruth.isNotEmpty() && actualHits.containsAll(groundTruth)
        val partialCoverage: Boolean get() = groundTruth.isNotEmpty() && overlap.isNotEmpty() && !fullCoverage
        val noOverlap: Boolean get() = groundTruth.isNotEmpty() && overlap.isEmpty()
        val noGroundTruth: Boolean get() = groundTruth.isEmpty()

        /** OCR 原文压成单行的前 [n] 字,给报告表格用。 */
        fun ocrPreview(n: Int): String =
            if (!hasFixture) "(无 fixture)"
            else ocrText.replace("\n", " ").replace(Regex("\\s+"), " ").trim().take(n)
    }

    @Test
    fun ocrAudit66ImageRuleEngine() {
        val root = projectRoot()
        val coverageMap = parseCoverageMatrix(File(root, "违规案例/_coverage_matrix.md"))
        val auditMap = parseAuditGaps(File(root, "违规案例/_audit_gaps.md"))

        assertTrue("coverage matrix 解析为空", coverageMap.isNotEmpty())
        assertTrue("audit gaps 解析为空", auditMap.isNotEmpty())
        assertEquals(
            "coverage_matrix 与 audit_gaps 张数应一致(coverage=${coverageMap.size}, audit=${auditMap.size})",
            coverageMap.size, auditMap.size,
        )

        val rules = loadRules(root)
        val ruleIds = rules.map { it.id }.toSet()
        val matcher = AdSignageRuleMatcher(rules)

        val fixturesDir = File(root, "app/src/test/resources/fixtures/audit66_ocr")
        val fixtureTxts = fixturesDir.listFiles { _, n -> n.endsWith(".txt") } ?: emptyArray()
        if (!fixturesDir.isDirectory || fixtureTxts.isEmpty()) {
            println("⚠️ OCR fixtures not found at ${fixturesDir.absolutePath}")
            println("   请先跑: python tools/ocr-audit66-fixtures.py")
            return // skip,不 fail
        }

        println("===== 66 image OCR-based rule-engine audit START =====")
        val results = mutableListOf<ImageResult>()

        for ((filename, gtAndStatus) in coverageMap.toSortedMap()) {
            val (groundTruth, auditStatus) = gtAndStatus

            // ground truth 中规则 ID 必须都在白名单(防止 coverage matrix 引用已废弃规则)
            groundTruth.forEach { rid ->
                assertTrue("[$filename] 引用未知规则 $rid", rid in ruleIds)
            }

            val fixtureFile = File(fixturesDir, fixtureNameFor(filename))
            val hasFixture = fixtureFile.exists() && fixtureFile.length() > 0
            val rawText = if (hasFixture) fixtureFile.readText(Charsets.UTF_8) else ""
            // scan() 内部也会跑 TextNormalizer.forMatching(幂等),这里显式做一次是为了
            // 让「与 Android 端同一套归一化」这件事在测试里可见。
            val normalized = TextNormalizer.forMatching(rawText)
            val hits = matcher.scan(normalized).map { it.ruleId }.distinct()

            val r = ImageResult(filename, auditStatus, groundTruth, hits, rawText, hasFixture)
            results.add(r)

            val status = when {
                r.fullCoverage -> "FULL"
                r.partialCoverage -> "PARTIAL(${r.overlap.size}/${r.groundTruth.size})"
                r.noOverlap -> "MISS"
                r.noGroundTruth && hits.isEmpty() -> "no-gt/no-hit"
                r.noGroundTruth -> "no-gt/hit=${hits.size}"
                else -> "?"
            }
            val sample = hits.take(6).joinToString(",") + if (hits.size > 6) ",…" else ""
            val fixtureStatus = if (hasFixture) "" else " [no-fixture]"
            println("[${status.padEnd(20)}] $filename  gt=${groundTruth.size} actual=${hits.size} [$sample]$fixtureStatus")
        }

        // 总结
        val full = results.count { it.fullCoverage }
        val partial = results.count { it.partialCoverage }
        val miss = results.count { it.noOverlap }
        val noGt = results.count { it.noGroundTruth }
        val noFixture = results.count { !it.hasFixture }

        println("===== 66 image OCR-based rule-engine audit SUMMARY =====")
        println("总数: ${results.size}")
        println("完全覆盖(actual ⊇ ground truth):        $full")
        println("部分覆盖(actual ∩ gt ≠ ∅ 但不全):       $partial")
        println("未覆盖(actual ∩ gt = ∅):                $miss")
        println("无 ground truth 规则:                    $noGt")
        println("无 fixture / fixture 为空:               $noFixture")

        // 落盘报告
        val ts = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss"))
        val reportDir = File(root, "build/reports")
        reportDir.mkdirs()
        val report = File(reportDir, "audit66_ocr_$ts.md")
        report.writeText(
            buildOcrReport(
                results = results,
                full = full,
                partial = partial,
                miss = miss,
                noGt = noGt,
                noFixture = noFixture,
                rulesTotal = rules.size,
                rulesPath = rel(root, rulesFile(root)),
                fixturesPath = rel(root, fixturesDir),
            ),
            Charsets.UTF_8,
        )
        println("报告: ${report.absolutePath}")
    }

    /** 相对项目根的展示路径(Windows 反斜杠统一成 `/`);无法相对化时退回绝对路径。 */
    private fun rel(root: File, f: File): String =
        runCatching { f.relativeTo(root).path }.getOrElse { f.path }.replace('\\', '/')

    private fun buildOcrReport(
        results: List<ImageResult>,
        full: Int,
        partial: Int,
        miss: Int,
        noGt: Int,
        noFixture: Int,
        rulesTotal: Int,
        rulesPath: String,
        fixturesPath: String,
    ): String = buildString {
        val total = results.size
        fun pct(n: Int) = "%.1f".format(n * 100.0 / total)

        appendLine("# 66 张违规案例 · 真 OCR 规则识别实测报告")
        appendLine()
        appendLine("- 生成时间: ${LocalDateTime.now().format(DateTimeFormatter.ISO_LOCAL_DATE_TIME)}")
        appendLine("- 规则 JSON: `$rulesPath`")
        appendLine("- 规则总数: $rulesTotal")
        appendLine("- OCR 引擎: paddleocr 路径已废弃;**现用 ONNX Runtime 1.20.1 + PP-OCRv6_small det/rec,与 Android ice_ocr_rules profile 完全同模型同栈**")
        appendLine("- OCR fixture: `$fixturesPath/`")
        appendLine("- 示例图数: $total")
        appendLine()
        appendLine("## §1 覆盖统计")
        appendLine()
        appendLine("| 类别 | 张数 | 占比 |")
        appendLine("|---|---:|---:|")
        appendLine("| 完全覆盖(actual ⊇ ground truth) | $full | ${pct(full)}% |")
        appendLine("| 部分覆盖(actual ∩ gt ≠ ∅ 但不全) | $partial | ${pct(partial)}% |")
        appendLine("| 未覆盖(actual ∩ gt = ∅) | $miss | ${pct(miss)}% |")
        appendLine("| 无 ground truth | $noGt | ${pct(noGt)}% |")
        appendLine("| 无 fixture / fixture 为空 | $noFixture | ${pct(noFixture)}% |")
        appendLine()
        appendLine("> 与 audit 文本 fixture 的对比:`AdSignageImageAuditSixtySixRegressionTest` 最近一次报告在 `build/reports/66image_audit_*.md`。")
        appendLine("> 本报告的差距(OCR 实测 vs audit 文本)= 端侧 OCR 还原度 + 规则关键词覆盖两项之和。")
        appendLine()

        appendLine("## §2 全量命中清单(按文件名升序)")
        appendLine()
        appendLine("| # | 文件名 | audit 状态 | ground truth | 实际命中 | OCR 前 60 字 | 状态 |")
        appendLine("|---:|---|---|---|---|---|---|")
        for ((i, r) in results.withIndex()) {
            val status = when {
                r.fullCoverage -> "✅ 完全覆盖"
                r.partialCoverage -> "⚠️ 部分覆盖(${r.overlap.size}/${r.groundTruth.size})"
                r.noOverlap -> "❌ 未覆盖"
                r.noGroundTruth && r.actualHits.isEmpty() -> "— 无规则无命中"
                r.noGroundTruth -> "— 无规则 hit=${r.actualHits.size}"
                else -> "?"
            }
            // OCR 原文可能含 `|`,会撑破 markdown 表格 — 转义掉
            val ocrPreview = r.ocrPreview(60).replace("|", "\\|")
            appendLine(
                "| ${i + 1} | `${r.filename}` | ${r.auditStatus} | ${r.groundTruth.joinToString(", ")} | " +
                    "${r.actualHits.joinToString(", ")} | $ocrPreview | $status |",
            )
        }
        appendLine()
    }
}

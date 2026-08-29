package com.icespiritai.offline.rules

import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

/**
 * 66 张违规案例的规则层回归 pin(端到端 OCR 之外的「识别能力」维度)。
 *
 * 数据源:
 *   - 违规案例/_coverage_matrix.md §2 = 每张图的 ground truth 规则 ID 集合 + audit 标记的状态
 *   - 违规案例/_audit_gaps.md §每节 = 每张图的违规描述 + 关联法条
 *
 * 跑真实 v9 规则(ad_signage_rules.json,129 条),对每张图:
 *   fixture 文本 = 文件名提示词 + 违规描述 + 关联法条
 *   实际命中 = AdSignageRuleMatcher.scan(fixture)
 *   比对 actual vs ground truth,按 audit 标的状态分类:
 *     - 完全覆盖:actual ⊇ ground truth
 *     - 部分覆盖:actual ∩ ground truth ≠ ∅ 但不全(关键词薄)
 *     - 未覆盖:  actual ∩ ground truth = ∅
 *     - 无规则:  ground truth 为空(audit 标「未覆盖」且无关联规则)
 *
 * 与 AdSignageTextFixtureRegressionTest 差异:后者跑 text_*.md(政府站处罚通报),
 * 本测试跑 66 张图 fixture。
 * 与 AdSignageMentorFiveImageRegressionTest 差异:后者用 mentor 5 张图手写 OCR 文本,
 * 本测试用 audit 文档自动生成 fixture(全量 66 张图)。
 *
 * 输出:
 *   - stdout 实时打每张图状态
 *   - build/reports/66image_audit_<timestamp>.md 落盘报告
 */
class AdSignageImageAuditSixtySixRegressionTest {

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
     * Fixture 文本 = 文件名提示词(去序号去扩展名,中段连字符改空格) +
     *               违规描述 + 关联法条
     *
     * 文件名提示词是 audit 中没显式列出但 OCR/合规审查员读图时必然看到的关键词,
     * 加进 fixture 让 AC trie 有更多命中机会(对应 audit 标「弱覆盖(关键词薄)」的图,
     * 提示词本身可能就是 ground truth 规则的关键词)。
     */
    private fun buildFixture(filename: String, violationDesc: String, legalBasis: String): String {
        val stemHint = filename.substringAfter("_")
            .substringBeforeLast(".")
            .replace("_", " ")
        return listOf(stemHint, violationDesc, legalBasis)
            .filter { it.isNotBlank() }
            .joinToString("\n")
    }

    /**
     * 解析 _coverage_matrix.md §2 表格:
     *   | `` | `<bucket>` | `<severity>` | `<n>` | `<rules>` | `<status>` |
     * 仅收集 §2 段(## §2 起到 ## §3 止);rules cell 为 "—" 时 ground truth 为空。
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
     * 解析 _audit_gaps.md 66 节,提取每节的「文件名」「违规描述」「关联法条」。
     * 节标题形如 `## 01_xxx_yyy.ext`(§桶汇总 / §新规则候选清单 / §强化规则清单 等
     * 非图节会被跳过:这些节标题不以 .jpg/.png/.jpeg 结尾)。
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
            // 字段行: | <key> | <value> |,允许 value 含括号 / 加号 / 引号(不含未转义 |)
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

    private data class ImageResult(
        val filename: String,
        val auditStatus: String,
        val groundTruth: List<String>,
        val actualHits: List<String>,
    ) {
        val overlap: List<String> get() = actualHits.filter { it in groundTruth }
        val missedGt: List<String> get() = groundTruth.filter { it !in actualHits }
        val extraHits: List<String> get() = actualHits.filter { it !in groundTruth }
        val fullCoverage: Boolean get() = groundTruth.isNotEmpty() && actualHits.containsAll(groundTruth)
        val partialCoverage: Boolean get() = groundTruth.isNotEmpty() && overlap.isNotEmpty() && !fullCoverage
        val noOverlap: Boolean get() = groundTruth.isNotEmpty() && overlap.isEmpty()
        val noGroundTruth: Boolean get() = groundTruth.isEmpty()
    }

    @Test
    fun sixtySixImageRuleEngineAudit() {
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

        println("===== 66 image rule-engine audit START =====")
        val results = mutableListOf<ImageResult>()

        for ((filename, gtAndStatus) in coverageMap.toSortedMap()) {
            val (groundTruth, auditStatus) = gtAndStatus
            val (violationDesc, legalBasis) = auditMap[filename] ?: ("" to "")

            // ground truth 中规则 ID 必须都在白名单(防止 fixture 引用已废弃规则)
            groundTruth.forEach { rid ->
                assertTrue("[$filename] 引用未知规则 $rid", rid in ruleIds)
            }

            val fixture = buildFixture(filename, violationDesc, legalBasis)
            val hits = matcher.scan(fixture).map { it.ruleId }.distinct()
            val r = ImageResult(filename, auditStatus, groundTruth, hits)
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
            println("[${status.padEnd(20)}] $filename  gt=${groundTruth.size} actual=${hits.size} [$sample]")
        }

        // 总结
        val full = results.count { it.fullCoverage }
        val partial = results.count { it.partialCoverage }
        val miss = results.count { it.noOverlap }
        val noGt = results.count { it.noGroundTruth }

        println("===== 66 image rule-engine audit SUMMARY =====")
        println("总数: ${results.size}")
        println("完全覆盖(actual ⊇ ground truth):        $full")
        println("部分覆盖(actual ∩ gt ≠ ∅ 但不全):       $partial")
        println("未覆盖(actual ∩ gt = ∅):                $miss")
        println("无 ground truth 规则(audit 标'未覆盖'且无关联规则): $noGt")

        // 落盘报告
        val ts = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss"))
        val reportDir = File(root, "build/reports")
        reportDir.mkdirs()
        val report = File(reportDir, "66image_audit_$ts.md")
        report.writeText(buildMarkdownReport(results, full, partial, miss, noGt, ruleIds.size, rulesFile(root)))
        println("报告: ${report.absolutePath}")

        // 硬断言 1:v9 规则(共 8 条新增)的关键词必须已生效 — 至少有 4 张 audit 标"未覆盖"
        // 但 ground truth 指向 v9 新规则的图(#06 八一, #20 送领导, #32 公务员代言, #41 清肺)
        // 必须被识别。这是验证 v9 规则扩规则 + 加关键词动作是否真生效的 pin。
        val v9RuleIds = setOf(
            "ad_signage_signage_military_political_marketing",
            "ad_signage_signage_gift_to_leader",
            "ad_signage_edu_art24_public_servant_endorsement",
            "ad_signage_signage_food_lung_health_claim",
        )
        for (rid in v9RuleIds) {
            val hitAny = results.any { rid in it.actualHits }
            assertTrue("v9 新规则 $rid 应至少命中 1 张图(关键词未生效?)", hitAny)
        }

        // 硬断言 2:整体覆盖率 — 命中 ≥ 1 个 ground truth 规则的图应 ≥ 50 张(≈75%)。
        // 反映 app 整体"识别能力",而非 audit §2 标"已覆盖"的精确度(audit 的"已覆盖"基于
        // 规则存在 + 桶分类相符的人工判断,不等于 fixture 文本能精确命中所有关键词)。
        val recognized = results.count { it.groundTruth.isNotEmpty() && it.overlap.isNotEmpty() }
        assertTrue(
            "整体识别率太低:命中 ≥ 1 个 gt 的图共 $recognized / ${results.size},期望 ≥ 50",
            recognized >= 50,
        )
    }

    private fun buildMarkdownReport(
        results: List<ImageResult>,
        full: Int,
        partial: Int,
        miss: Int,
        noGt: Int,
        rulesTotal: Int,
        rulesFile: File,
    ): String = buildString {
        appendLine("# 66 张违规案例 · 规则引擎识别实测报告")
        appendLine()
        appendLine("- 生成时间: ${LocalDateTime.now().format(DateTimeFormatter.ISO_LOCAL_DATE_TIME)}")
        appendLine("- 规则 JSON: `${rulesFile.relativeTo(rulesFile.parentFile.parentFile.parentFile)}`")
        appendLine("- 规则总数: $rulesTotal")
        appendLine("- 示例图数: ${results.size}")
        appendLine()
        appendLine("## §1 覆盖统计")
        appendLine()
        appendLine("| 类别 | 张数 | 占比 |")
        appendLine("|---|---:|---:|")
        val total = results.size
        appendLine("| 完全覆盖(actual ⊇ ground truth) | $full | ${"%.1f".format(full * 100.0 / total)}% |")
        appendLine("| 部分覆盖(关键词薄,actual ∩ gt ≠ ∅ 但不全) | $partial | ${"%.1f".format(partial * 100.0 / total)}% |")
        appendLine("| 未覆盖(actual ∩ gt = ∅,audit 标'未覆盖'有关联规则) | $miss | ${"%.1f".format(miss * 100.0 / total)}% |")
        appendLine("| 无 ground truth 规则(audit 标'未覆盖'且无关联规则) | $noGt | ${"%.1f".format(noGt * 100.0 / total)}% |")
        appendLine()
        appendLine("> 与 `_coverage_matrix.md` §3 对照:")
        appendLine("> - audit 标「已覆盖」:43 / 66 → 实测「完全覆盖」:$full")
        appendLine("> - audit 标「弱覆盖(关键词薄)」:17 / 66 → 实测「部分覆盖」:$partial")
        appendLine("> - audit 标「未覆盖」:6 / 66 → 含「未覆盖」$miss + 「无规则」$noGt")
        appendLine()

        appendLine("## §2 audit 标「已覆盖」实测失败列表(回归 pin)")
        appendLine()
        val auditCoveredFailing = results.filter { it.auditStatus == "已覆盖" && !it.fullCoverage }
        if (auditCoveredFailing.isEmpty()) {
            appendLine("无(audit 标「已覆盖」的图实测全部命中 ground truth)。")
        } else {
            appendLine("| 文件名 | ground truth | 实际命中 | 漏命中 |")
            appendLine("|---|---|---|---|")
            for (r in auditCoveredFailing) {
                appendLine("| `${r.filename}` | ${r.groundTruth.joinToString(", ")} | ${r.actualHits.joinToString(", ")} | ${r.missedGt.joinToString(", ")} |")
            }
        }
        appendLine()

        appendLine("## §3 实测未命中 ground truth 的图(audit 标「弱覆盖」+「未覆盖」)")
        appendLine()
        val gaps = results.filter { !it.fullCoverage && it.groundTruth.isNotEmpty() }
        if (gaps.isEmpty()) {
            appendLine("无(66 张图全部覆盖 ground truth)。")
        } else {
            appendLine("| 文件名 | audit 状态 | ground truth | 实际命中 | 漏命中 | 状态 |")
            appendLine("|---|---|---|---|---|---|")
            for (r in gaps.sortedBy { it.filename }) {
                val status = when {
                    r.partialCoverage -> "部分覆盖(${r.overlap.size}/${r.groundTruth.size})"
                    r.noOverlap -> "未覆盖"
                    else -> "?"
                }
                appendLine("| `${r.filename}` | ${r.auditStatus} | ${r.groundTruth.joinToString(", ")} | ${r.actualHits.joinToString(", ")} | ${r.missedGt.joinToString(", ")} | $status |")
            }
        }
        appendLine()

        appendLine("## §4 全量命中清单(按文件名升序)")
        appendLine()
        appendLine("| # | 文件名 | audit 状态 | ground truth | 实际命中 | 状态 |")
        appendLine("|---:|---|---|---|---|---|")
        for ((i, r) in results.withIndex()) {
            val status = when {
                r.fullCoverage -> "✅ 完全覆盖"
                r.partialCoverage -> "⚠️ 部分覆盖(${r.overlap.size}/${r.groundTruth.size})"
                r.noOverlap -> "❌ 未覆盖"
                r.noGroundTruth && r.actualHits.isEmpty() -> "— 无规则无命中"
                r.noGroundTruth -> "— 无规则 hit=${r.actualHits.size}"
                else -> "?"
            }
            appendLine("| ${i + 1} | `${r.filename}` | ${r.auditStatus} | ${r.groundTruth.joinToString(", ")} | ${r.actualHits.joinToString(", ")} | $status |")
        }
        appendLine()
    }
}

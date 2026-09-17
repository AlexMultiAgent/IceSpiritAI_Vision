package com.icespiritai.offline.tts

import com.icespiritai.offline.domain.AnalysisState
import com.icespiritai.offline.domain.RuleHit
import com.icespiritai.offline.domain.Severity
import com.icespiritai.offline.domain.ViolationReport
import com.icespiritai.offline.domain.severityRank

/**
 * 把 [ViolationReport] 转成多段朗读片段,每段是独立可朗读的短文。
 *
 * 顺序:
 *   1. prefix: "共 X 条违规, Y 条警告, Z 条信息"
 *   2. 严重度桶(违规 → 警告 → 信息 → 合规;Positive 单独 bucket)
 *   3. 截断 suffix("其余 X 项详见屏幕")— 仅当 topN 触发
 *   4. 末尾免责声明 "AI识别仅供参考..."
 *
 * 纯函数、无副作用、不依赖 Android Context,可在 JVM 单测全覆盖。
 */
object SegmentedScript {

    private const val DISCLAIMER = "AI识别仅供参考,合规判断以现场检查为准"

    fun build(report: ViolationReport, options: BuildOptions = BuildOptions.Default): List<HitSegment> {
        // v0.3.3 (post v0.3.2 correction): 0 hits -> silent by default.
        // User 原话 "如果为0就不播" — no fallback, no disclaimer, nothing.
        // `trailingDisclaimer` is a no-op for empty inputs.
        //
        // 2026-09-17: that default is wrong for the *glasses* path, where the
        // wearer has no screen to read. There the caller opts in with
        // [BuildOptions.emptyResultText] and gets a spoken verdict either
        // way; the phone path (null) stays exactly as it was.
        if (report.hits.isEmpty()) {
            val conclusion = options.emptyResultText?.takeIf { it.isNotBlank() }
                ?: return emptyList()
            // 用户 2026-09-17:「如果全为0,需要播一下"未发现违规用语"+兜底」
            // — 结论句 + 免责声明,和错误路径(buildError)同一形状。
            val text = if (options.trailingDisclaimer) "$conclusion。$DISCLAIMER" else conclusion
            return listOf(metaSegment(text))
        }

        val sorted = report.hits.sortedByDescending { severityRank(it.severity) }
        val truncated = options.topN?.let { sorted.take(it) } ?: sorted
        val omitted = sorted.size - truncated.size

        val out = mutableListOf<HitSegment>()
        out.add(metaSegment(buildCountPrefix(report.hits, options.domainPrefix)))
        val hitsWithIdx = truncated.mapIndexed { i, h -> i to h }
        val buckets = hitsWithIdx.groupBy { it.second.severity }.toSortedMap(
            compareByDescending { severityRank(it) }
        )
        buckets.forEach { (sev, indexed) ->
            val firstIdx = indexed.first().first
            out.add(buildBucketSegment(sev, indexed.map { it.second }, options, firstIdx))
        }
        if (omitted > 0) {
            out.add(metaSegment("其余 $omitted 项详见屏幕"))
        }
        if (options.trailingDisclaimer) out.add(metaSegment(DISCLAIMER))

        return out
    }

    fun buildError(error: AnalysisState.Error): List<HitSegment> {
        val text = "${error.message}。${DISCLAIMER}"
        return listOf(metaSegment(text))
    }

    private fun metaSegment(text: String) = HitSegment(
        text = text, severity = null, isMeta = true,
    )

    private fun buildCountPrefix(hits: List<RuleHit>, domainPrefix: String?): String {
        val v = hits.count { it.severity == Severity.Violation }
        val w = hits.count { it.severity == Severity.Warning }
        val i = hits.count { it.severity == Severity.Info }
        val p = hits.count { it.severity == Severity.Positive }
        val prefix = domainPrefix?.let { "$it。" } ?: ""
        // 用户 2026-09-17:只念**非零**的分类 —— 「违规 1、警告 0、信息 0」
        // 时不该念「0 条警告,0 条信息」;有结果就说明结果,没结果的类别闭嘴。
        val parts = buildList {
            if (v > 0) add("$v 条违规")
            if (w > 0) add("$w 条警告")
            if (i > 0) add("$i 条信息")
            if (p > 0) add("$p 条合规")
        }
        if (parts.isEmpty()) return prefix
        return prefix + "共 " + parts.joinToString(",")
    }

    private fun buildBucketSegment(
        sev: Severity,
        hits: List<RuleHit>,
        options: BuildOptions,
        firstIndexInTruncated: Int,
    ): HitSegment {
        val label = when (sev) {
            Severity.Violation -> "违规"
            Severity.Warning -> "警告"
            Severity.Info -> "信息"
            Severity.Positive -> "合规"
        }
        val body = hits.joinToString("、") { hit ->
            val text = hit.matchedText.trim()
            // v0.3.3 (post v0.3.2 correction): include the regulation
            // citation "依据 <regulation>" (用户原话 "没说命中内容及依据
            // 不播" — 依据是 evidence,跟 matchedText 同属内容), but
            // NOT the truncated `lawText` ("<20 chars>等" 听起来断章取
            // 义、支离破碎,用户原话 "条文不全,误导")。 屏 UI 和证据包
            // 导出走 `hit.lawText` 全文字段,不受 TTS 这一刀影响。
            val citation = if (options.includeLawCitation && hit.regulation.isNotBlank()) {
                ",依据 ${hit.regulation}"
            } else ""
            "$text$citation"
        }
        return HitSegment(text = "$label:$body", severity = sev, hitIndex = firstIndexInTruncated)
    }
}

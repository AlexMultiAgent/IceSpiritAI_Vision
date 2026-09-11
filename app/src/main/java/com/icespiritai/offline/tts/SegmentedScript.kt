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

    private const val FALLBACK_TEXT = "未筛查出违规事项"
    private const val DISCLAIMER = "AI识别仅供参考,合规判断以现场检查为准"

    fun build(report: ViolationReport, options: BuildOptions = BuildOptions.Default): List<HitSegment> {
        if (report.hits.isEmpty()) {
            val out = mutableListOf<HitSegment>(metaSegment(FALLBACK_TEXT))
            if (options.trailingDisclaimer) out.add(metaSegment(DISCLAIMER))
            return out
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
        return "${prefix}共 ${v} 条违规,${w} 条警告,${i} 条信息${if (p > 0) ",${p} 条合规" else ""}"
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
            val citation = if (options.includeLawCitation && hit.regulation.isNotBlank()) {
                val truncated = if (hit.lawText.length > 20) hit.lawText.take(20) + "等" else hit.lawText
                ",依据 ${hit.regulation}${if (truncated.isNotBlank()) " $truncated" else ""}"
            } else ""
            "$text$citation"
        }
        return HitSegment(text = "$label:$body", severity = sev, hitIndex = firstIndexInTruncated)
    }
}

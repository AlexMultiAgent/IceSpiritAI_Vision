package com.icespiritai.offline.tts

import com.icespiritai.offline.domain.ViolationReport
import com.icespiritai.offline.domain.severityRank

/**
 * 把 [ViolationReport] 转成单段朗读脚本。
 *
 * - 空 hits:返 fallback 文案(显式传递"AI 仅供参考"不确定性,见 spec §3.4)
 * - 有 hits:按 [severityRank] 降序(违规→警告→信息)拼接 matchedText,前后包裹
 *   「命中违规:」前缀 + 句号分隔,便于听者快速识别严重度梯度。
 *
 * 纯函数、无副作用、不依赖 Android Context,可在 JVM 单测里全覆盖。
 */
object ScriptBuilder {

    private const val FALLBACK_TEXT = "未筛查出违规事项,AI识别仅供参考"
    private const val PREFIX = "命中违规:"
    private const val SEPARATOR = "。"
    private const val END_PUNCT = "。"

    fun build(report: ViolationReport): String =
        if (report.hits.isEmpty()) {
            FALLBACK_TEXT
        } else {
            report.hits
                .sortedByDescending { severityRank(it.severity) }
                .joinToString(separator = SEPARATOR, prefix = PREFIX) {
                    it.matchedText.trim()
                }
                .plus(END_PUNCT)
        }
}
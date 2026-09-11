package com.icespiritai.offline.tts

import com.icespiritai.offline.domain.ViolationReport

/**
 * 单段朗读脚本(向后兼容 shim)— 新代码请用 [buildSegments]。
 *
 * @deprecated since v0.3.0 — 脚本已升级为多段结构(严重度分组 + 计数 + 法条 +
 *     免责声明),`build()` 折叠为单字符串会丢失 segment 边界,无法驱动 UI
 *     scroll-to-hit。保留本函数仅为不在 Phase A 引入 TtsController / UI
 *     改动(Phase B 单独落)。将在 v0.3.1 删除。
 */
@Deprecated("use buildSegments() — see SegmentedScript for the new contract")
object ScriptBuilder {
    fun build(report: ViolationReport): String =
        buildSegments(report, BuildOptions.Default).joinToString("") { it.text }

    fun buildSegments(report: ViolationReport, options: BuildOptions = BuildOptions.Default): List<HitSegment> =
        SegmentedScript.build(report, options)
}

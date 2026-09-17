package com.icespiritai.offline.tts

/**
 * 朗读脚本构建选项。所有选项都是非必填的,`Default` 即"适合大多数用户"。
 */
data class BuildOptions(
    /** 命中总数 > topN 时,只朗读最严重的 N 条,后接"其余 X 项详见屏幕"。null = 不截断。 */
    val topN: Int? = null,
    /** 是否在脚本末尾追加 "AI识别仅供参考" 免责声明。 */
    val trailingDisclaimer: Boolean = true,
    /** 每条命中是否朗读 regulation 字段(如 "依据 GB 7718-2025 §5.1")。
     *  v0.3.3: 默认 true。v0.3.2 误改成 false 之后用户反馈"没说命中内容
     *  及依据不播" — 依据是命中的 evidence,跟 matchedText 一样属于
     *  "内容"的一部分,只是不念 truncated 的 `lawText` 全文摘要
     *  (由 buildBucketSegment 决定:truncated lawText 永远不 emit,
     *  citation 永远 emit when flag on)。 */
    val includeLawCitation: Boolean = true,
    /** 域前缀("广告招牌" / "食品标签")— null = 不前缀。 */
    val domainPrefix: String? = null,
    /**
     * 0 命中时朗读的文案;null = 不播(仅测试/特殊场景会传 null)。
     *
     * v0.3.3 的旧决定是「如果为0就不播」(手机端视角:屏幕上有卡片,再念一遍
     * 是噪音)。v0.4.x 用户改为要求**全为零也播报结论**:眼镜佩戴者看不到屏幕,
     * 「什么都没说」与「App 根本没工作」无法区分(2026-09-17 反馈:眼镜上没声音)。
     * 默认值即 [NO_VIOLATION_SPOKEN_TEXT]。
     */
    val emptyResultText: String? = null,
) {
    companion object {
        val Default = BuildOptions(emptyResultText = NO_VIOLATION_SPOKEN_TEXT)
    }
}

/**
 * 0 命中时朗读的结论句。与 UI 卡片 `status_no_violation_card`
 * (「未发现违规用语」)保持同一措辞,便于佩戴者把听到的和看到的对上。
 */
const val NO_VIOLATION_SPOKEN_TEXT = "未发现违规用语"

/**
 * 单段朗读内容 + metadata。`hitIndex` 用于 UI 端 scroll-to-item 同步;
 * `severity` 让 UI 可独立着色;`isMeta` = true 表示这条不是 hit(而是 prefix / suffix / disclaimer)。
 */
data class HitSegment(
    val text: String,
    val severity: com.icespiritai.offline.domain.Severity?,
    val hitIndex: Int = -1,
    val isMeta: Boolean = false,
    val utteranceId: String = text.hashCode().toString(),
)

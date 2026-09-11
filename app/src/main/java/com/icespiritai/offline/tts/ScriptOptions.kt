package com.icespiritai.offline.tts

/**
 * 朗读脚本构建选项。所有选项都是非必填的,`Default` 即"适合大多数用户"。
 */
data class BuildOptions(
    /** 命中总数 > topN 时,只朗读最严重的 N 条,后接"其余 X 项详见屏幕"。null = 不截断。 */
    val topN: Int? = null,
    /** 是否在脚本末尾追加 "AI识别仅供参考" 免责声明。 */
    val trailingDisclaimer: Boolean = true,
    /** 每条命中是否朗读 regulation 字段(如 "GB 7718-2025 §5.1 致敏原强制")。 */
    val includeLawCitation: Boolean = true,
    /** 域前缀("广告招牌" / "食品标签")— null = 不前缀。 */
    val domainPrefix: String? = null,
) {
    companion object {
        val Default = BuildOptions()
    }
}

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

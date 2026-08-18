package com.icespiritai.offline.domain

/**
 * Maps the stable machine-readable keys in `ad_law_rules.json` to their
 * user-facing Chinese display names. Unknown keys pass through unchanged so a
 * future rule category can never crash the UI or silently blank a label.
 */
object AdCategory {
    fun displayName(category: String): String = when (category) {
        "absolute" -> "绝对化用语"
        "education" -> "教育培训"
        "medical" -> "医疗医药"
        "finance" -> "金融投资"
        "realestate" -> "房地产"
        "restricted" -> "烟酒类"
        "minor" -> "未成年人"
        else -> category
    }
}

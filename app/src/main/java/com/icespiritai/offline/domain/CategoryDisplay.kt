package com.icespiritai.offline.domain

/**
 * Domain-aware Chinese display-name lookup for [RuleHit.category]. The
 * [RuleHit.domain] tag — `"ad"` for 广告招牌 or `"food"` for 食品标识 —
 * picks which sub-table to consult. Unknown combinations fall through to
 * the raw `category` string so a future rule never blanks its label.
 *
 * The two parallel category tables ([AdSignageCategory],
 * [FoodLabelCategory]) stay independent. Each rule matcher populates
 * `domain` and `category` together; this object is the single chokepoint
 * that turns `(domain, category)` into the human-readable label the
 * result card and ZIP manifest both display.
 */
object CategoryDisplay {

    /** 广告招牌 domain tag (set by [AdSignageRuleMatcher]). */
    const val DOMAIN_AD = "ad"

    /** 食品标识 domain tag (set by [FoodLabelRuleMatcher]). */
    const val DOMAIN_FOOD = "food"

    fun displayName(domain: String, category: String): String = when (domain) {
        DOMAIN_AD -> AdSignageCategory.displayName(category)
        DOMAIN_FOOD -> FoodLabelCategory.displayName(category)
        else -> category
    }
}

/**
 * Maps the stable machine-readable category keys in `ad_signage_rules.json`
 * to their user-facing Chinese display names. Mirrors the original
 * `AdCategory` table and adds signage-specific keys introduced when the
 * bundling domain widened from "广告法" to "广告招牌".
 */
object AdSignageCategory {
    fun displayName(category: String): String = when (category) {
        "absolute" -> "绝对化用语"
        "education" -> "教育培训"
        "medical" -> "医疗医药"
        "finance" -> "金融投资"
        "realestate" -> "房地产"
        "restricted" -> "烟酒类"
        "minor" -> "未成年人"
        "outdoor" -> "户外广告"
        "signage" -> "门店招牌"
        "pesticide" -> "农药类广告"
        "veterinary" -> "兽药类广告"
        "cosmetic" -> "化妆品广告"
        "internet_ad" -> "互联网广告"
        else -> category
    }
}

/**
 * Maps the stable machine-readable category keys in `food_label_rules.json`
 * to their user-facing Chinese display names. Keys reflect the section
 * taxonomy of GB 7718 / GB 28050 / 食品标识监督管理办法 (2025).
 */
object FoodLabelCategory {
    fun displayName(category: String): String = when (category) {
        "label_form" -> "标签形式"
        "product_name" -> "产品名称"
        "ingredient" -> "配料表"
        "allergen" -> "过敏原"
        "production_date" -> "生产日期与保质期"
        "net_weight" -> "净含量"
        "nutrition" -> "营养成分表"
        "additive" -> "食品添加剂"
        "functional_claim" -> "功能声称"
        "specific_food" -> "特殊食品"
        else -> category
    }
}

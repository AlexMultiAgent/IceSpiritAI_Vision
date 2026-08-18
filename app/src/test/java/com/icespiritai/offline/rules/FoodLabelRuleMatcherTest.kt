package com.icespiritai.offline.rules

import com.icespiritai.offline.domain.CategoryDisplay
import com.icespiritai.offline.domain.Severity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class FoodLabelRuleMatcherTest {

    private val rule1 = FoodLabelRule(
        id = "food_nozero_add",
        category = "functional_claim",
        regulation = "食品标识监督管理办法（市场监管总局令第 100 号，2027-03-16 施行）第七条第（二）项 / GB 7718-2011 §3.4 / 广告法 第二十八条",
        keywords = listOf("零添加", "不添加"),
        severity = Severity.Violation,
    )
    private val rule2 = FoodLabelRule(
        id = "food_gb7718_disease_cancer_explicit",
        category = "functional_claim",
        regulation = "GB 7718-2011 §3.2",
        keywords = listOf("抗癌", "抗肿瘤"),
        severity = Severity.Violation,
    )
    private val matcher = FoodLabelRuleMatcher(listOf(rule1, rule2))

    @Test
    fun scan_emptyText_returnsEmpty() {
        assertTrue(matcher.scan("").isEmpty())
    }

    @Test
    fun scan_textWithNoHits_returnsEmpty() {
        assertTrue(matcher.scan("配料：水、白砂糖、食用盐").isEmpty())
    }

    @Test
    fun scan_findsKeywordFromMultipleRules() {
        val hits = matcher.scan("本品零添加，抗癌养生")
        assertEquals(2, hits.size)
        assertTrue(hits.any { it.ruleId == "food_nozero_add" && it.matchedText == "零添加" })
        assertTrue(hits.any { it.ruleId == "food_gb7718_disease_cancer_explicit" && it.matchedText == "抗癌" })
    }

    @Test
    fun scan_deduplicatesSameRuleAndText() {
        val hits = matcher.scan("零添加零添加零添加")
        assertEquals(1, hits.size)
        assertEquals("food_nozero_add", hits[0].ruleId)
        assertEquals("零添加", hits[0].matchedText)
    }

    @Test
    fun scan_preservesCategoryAndRegulationFromRule() {
        val hits = matcher.scan("不添加")
        assertEquals(1, hits.size)
        assertEquals("functional_claim", hits[0].category)
        assertEquals("食品标识监督管理办法（市场监管总局令第 100 号，2027-03-16 施行）第七条第（二）项 / GB 7718-2011 §3.4 / 广告法 第二十八条", hits[0].regulation)
        assertEquals(Severity.Violation, hits[0].severity)
    }

    @Test
    fun scan_stampsDomainAsFoodOnEveryHit() {
        val hits = matcher.scan("零添加")
        assertEquals(1, hits.size)
        assertEquals(CategoryDisplay.DOMAIN_FOOD, hits[0].domain)
    }

    @Test
    fun scan_doesNotMatchWhenOnlyWhitespaceRemains() {
        val matcher = FoodLabelRuleMatcher(
            listOf(FoodLabelRule("x", "c", "r", listOf("零添加"), Severity.Warning))
        )
        assertTrue(matcher.scan("  \n\t ").isEmpty())
    }

    @Test
    fun scan_matchesAcrossWhitespaceAndFullWidthVariants() {
        val spaced = FoodLabelRule("pct", "functional_claim", "GB 7718 §4.3", listOf("100% 纯天然"), Severity.Warning)
        val hits = FoodLabelRuleMatcher(listOf(spaced)).scan("本品 １００％纯天然 健康")
        assertEquals(1, hits.size)
        assertEquals("100%纯天然", hits[0].matchedText)
    }

    // --- 食品标识监督管理办法 增量规则 触发测试(2026-08-19 落地,共 30 条新增) ---

    @Test
    fun scan_art7SpecialSupply_firesOn特供() {
        val r = FoodLabelRule(
            "food_art7_special_supply",
            "label_form",
            "食品标识监督管理办法 第七条第（四）项 + 第四十二条",
            listOf("特供", "专供", "内供"),
            Severity.Violation,
        )
        val hits = FoodLabelRuleMatcher(listOf(r)).scan("本品为中南海特供，限量供应")
        assertEquals(1, hits.size)
        assertEquals("特供", hits[0].matchedText)
        assertEquals(Severity.Violation, hits[0].severity)
    }

    @Test
    fun scan_art7Superstition_firesOn祖传秘方() {
        val r = FoodLabelRule(
            "food_art7_superstition",
            "functional_claim",
            "食品标识监督管理办法 第七条第（三）项",
            listOf("祖传秘方", "包治百病"),
            Severity.Violation,
        )
        val hits = FoodLabelRuleMatcher(listOf(r)).scan("祖传秘方，包治百病")
        assertEquals(2, hits.size)
        assertTrue(hits.any { it.matchedText == "祖传秘方" })
        assertTrue(hits.any { it.matchedText == "包治百病" })
    }

    @Test
    fun scan_art7HealthFunction_firesOn抗氧化() {
        val r = FoodLabelRule(
            "food_art7_health_function",
            "functional_claim",
            "食品标识监督管理办法 第七条第二款",
            listOf("抗氧化", "改善睡眠", "排毒"),
            Severity.Violation,
        )
        val hits = FoodLabelRuleMatcher(listOf(r)).scan("本品富含花青素，抗氧化，改善睡眠，排毒养颜")
        assertEquals(3, hits.size)
        assertTrue(hits.all { it.ruleId == "food_art7_health_function" })
    }

    @Test
    fun scan_art8MinorUnapproved_firesOn儿童专用() {
        val r = FoodLabelRule(
            "food_art8_minor_unapproved",
            "specific_food",
            "食品标识监督管理办法 第八条",
            listOf("儿童专用", "宝宝专属"),
            Severity.Warning,
        )
        val hits = FoodLabelRuleMatcher(listOf(r)).scan("宝宝专属配方，儿童专用营养")
        assertEquals(2, hits.size)
    }

    @Test
    fun scan_art16PlantImitation_firesOn素肉() {
        val r = FoodLabelRule(
            "food_art16_plant_imitation_name",
            "product_name",
            "食品标识监督管理办法 第十六条第（二）项",
            listOf("素肉", "素鸡"),
            Severity.Warning,
        )
        // 无前缀"仿/素/某植物"的疑似植物源模拟品——仅作触发器示例
        val hits = FoodLabelRuleMatcher(listOf(r)).scan("素肉汉堡，素鸡块")
        assertEquals(2, hits.size)
        assertTrue(hits.any { it.matchedText == "素肉" })
        assertTrue(hits.any { it.matchedText == "素鸡" })
    }

    @Test
    fun scan_art18Repack_firesOn分装() {
        val r = FoodLabelRule(
            "food_art18_repack_mark",
            "ingredient",
            "食品标识监督管理办法 第十八条第二款",
            listOf("分装"),
            Severity.Warning,
        )
        val hits = FoodLabelRuleMatcher(listOf(r)).scan("本品为分装产品，原包装配料表如下")
        assertEquals(1, hits.size)
        assertEquals("分装", hits[0].matchedText)
    }

    @Test
    fun scan_art26HealthWarning_firesOn保健食品() {
        val r = FoodLabelRule(
            "food_art26_health_warning",
            "specific_food",
            "食品标识监督管理办法 第二十六条第二款",
            listOf("保健食品"),
            Severity.Violation,
        )
        val hits = FoodLabelRuleMatcher(listOf(r)).scan("本品为保健食品，请按推荐量食用")
        assertEquals(1, hits.size)
        assertEquals("保健食品", hits[0].matchedText)
    }

    @Test
    fun scan_art30InfantClaim_firesOn婴儿配方() {
        val r = FoodLabelRule(
            "food_art30_infant_claim",
            "specific_food",
            "食品标识监督管理办法 第三十条",
            listOf("婴儿配方", "0—6 月龄"),
            Severity.Violation,
        )
        val hits = FoodLabelRuleMatcher(listOf(r)).scan("婴儿配方乳粉（0—6 月龄）")
        assertEquals(2, hits.size)
    }

    @Test
    fun scan_art33BulkLabel_firesOn散装() {
        val r = FoodLabelRule(
            "food_art33_bulk_label",
            "label_form",
            "食品标识监督管理办法 第三十三条第一款",
            listOf("散装", "散装食品"),
            Severity.Warning,
        )
        val hits = FoodLabelRuleMatcher(listOf(r)).scan("散装食品，按斤销售")
        assertTrue(hits.any { it.matchedText == "散装食品" })
        assertTrue(hits.any { it.matchedText == "散装" })
    }

    @Test
    fun scan_art34OnlineLabel_firesOn网售() {
        val r = FoodLabelRule(
            "food_art34_online_label",
            "label_form",
            "食品标识监督管理办法 第三十四条第一款",
            listOf("网售", "网络销售", "电商"),
            Severity.Warning,
        )
        val hits = FoodLabelRuleMatcher(listOf(r)).scan("本品通过电商渠道网络销售")
        assertTrue(hits.any { it.matchedText == "电商" })
        assertTrue(hits.any { it.matchedText == "网络销售" })
    }

    @Test
    fun scan_art15FakeDate_firesOn涂改() {
        val r = FoodLabelRule(
            "food_art15_fake_date",
            "production_date",
            "食品标识监督管理办法 第十五条 + 第四十五条",
            listOf("涂改", "二次打印", "擦改"),
            Severity.Violation,
        )
        val hits = FoodLabelRuleMatcher(listOf(r)).scan("生产日期被涂改，二次打印后重新上市")
        assertTrue(hits.any { it.matchedText == "涂改" })
        assertTrue(hits.any { it.matchedText == "二次打印" })
    }

    @Test
    fun scan_multipleNewRules_fireIndependentlyOnCombinedText() {
        // 同一段文本上,来自不同条款的多条增量规则应各自触发(每条 ruleId 各一条 hit)
        val rules = listOf(
            FoodLabelRule("r_disease", "functional_claim", "第七条第（一）项", listOf("治疗"), Severity.Violation),
            FoodLabelRule("r_super", "functional_claim", "第七条第（三）项", listOf("祖传秘方"), Severity.Violation),
            FoodLabelRule("r_supply", "label_form", "第七条第（四）项", listOf("特供"), Severity.Violation),
            FoodLabelRule("r_minor", "specific_food", "第八条", listOf("儿童专用"), Severity.Warning),
        )
        val hits = FoodLabelRuleMatcher(rules).scan("祖传秘方，特供，儿童专用，治疗百病")
        assertEquals(4, hits.size)
        assertEquals(setOf("r_disease", "r_super", "r_supply", "r_minor"), hits.map { it.ruleId }.toSet())
    }
}
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
        regulation = "食品标识监督管理办法 §9 (七)",
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
        assertEquals("食品标识监督管理办法 §9 (七)", hits[0].regulation)
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
}
package com.icespiritai.offline.rules

import com.icespiritai.offline.domain.CategoryDisplay
import com.icespiritai.offline.domain.Severity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class AdSignageRuleMatcherTest {

    private val rule1 = AdSignageRule(
        id = "extreme-001",
        category = "absolute",
        regulation = "广告法 §9",
        keywords = listOf("最佳", "第一"),
        severity = Severity.Violation
    )
    private val rule2 = AdSignageRule(
        id = "guarantee-002",
        category = "absolute",
        regulation = "广告法 §28",
        keywords = listOf("保证"),
        severity = Severity.Warning
    )
    private val matcher = AdSignageRuleMatcher(listOf(rule1, rule2))

    @Test
    fun scan_emptyText_returnsEmpty() {
        assertTrue(matcher.scan("").isEmpty())
    }

    @Test
    fun scan_textWithNoHits_returnsEmpty() {
        assertTrue(matcher.scan("这是一个普通的产品说明").isEmpty())
    }

    @Test
    fun scan_findsKeywordFromMultipleRules() {
        val hits = matcher.scan("我们是最佳品牌，保证质量")
        assertEquals(2, hits.size)
        assertTrue(hits.any { it.ruleId == "extreme-001" && it.matchedText == "最佳" })
        assertTrue(hits.any { it.ruleId == "guarantee-002" && it.matchedText == "保证" })
    }

    @Test
    fun scan_deduplicatesSameRuleAndText() {
        val hits = matcher.scan("最佳最佳最佳")
        assertEquals(1, hits.size)
        assertEquals("extreme-001", hits[0].ruleId)
        assertEquals("最佳", hits[0].matchedText)
    }

    @Test
    fun scan_preservesCategoryAndRegulationFromRule() {
        val hits = matcher.scan("最佳")
        assertEquals(1, hits.size)
        assertEquals("absolute", hits[0].category)
        assertEquals("广告法 §9", hits[0].regulation)
        assertEquals(Severity.Violation, hits[0].severity)
    }

    @Test
    fun scan_matchesAcrossWhitespaceAndFullWidthVariants() {
        val spaced = AdSignageRule("pct", "absolute", "广告法 §9", listOf("100% 有效"), Severity.Warning)
        val normalizedMatcher = AdSignageRuleMatcher(listOf(spaced))

        val hits = normalizedMatcher.scan("本品 １００％有效,请放心")
        assertEquals(1, hits.size)
        assertEquals("100%有效", hits[0].matchedText)
    }

    @Test
    fun scan_matchesPhraseSplitAcrossLineBreak() {
        val guarantee = AdSignageRule("edu", "education", "广告法 §24", listOf("不过退款"), Severity.Violation)
        val hits = AdSignageRuleMatcher(listOf(guarantee)).scan("考不过\n退款,全额退")
        assertEquals(1, hits.size)
        assertEquals("不过退款", hits[0].matchedText)
    }

    @Test
    fun scan_attributesSharedKeywordToEveryMatchingRule() {
        val education = AdSignageRule(
            id = "education-absolute",
            category = "education",
            regulation = "广告法 §24",
            keywords = listOf("第一"),
            severity = Severity.Warning,
        )
        val general = AdSignageRule(
            id = "absolute-top",
            category = "absolute",
            regulation = "广告法 §9",
            keywords = listOf("第一"),
            severity = Severity.Warning,
        )
        val hits = AdSignageRuleMatcher(listOf(education, general)).scan("全国销量第一")

        assertEquals(2, hits.size)
        assertTrue(hits.any { it.ruleId == "education-absolute" })
        assertTrue(hits.any { it.ruleId == "absolute-top" })
    }

    @Test
    fun scan_doesNotMatchWhenOnlyWhitespaceRemains() {
        val matcher = AdSignageRuleMatcher(
            listOf(AdSignageRule("x", "c", "r", listOf("最好"), Severity.Warning))
        )
        assertTrue(matcher.scan("  \n\t ").isEmpty())
    }

    @Test
    fun scan_stampsDomainAsAdOnEveryHit() {
        val hits = matcher.scan("最佳")
        assertEquals(1, hits.size)
        assertEquals(CategoryDisplay.DOMAIN_AD, hits[0].domain)
    }
}
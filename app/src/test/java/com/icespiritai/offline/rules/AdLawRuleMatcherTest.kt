package com.icespiritai.offline.rules

import com.icespiritai.offline.domain.RuleHit
import com.icespiritai.offline.domain.Severity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class AdLawRuleMatcherTest {

    private val rule1 = AdLawRule(
        id = "extreme-001",
        category = "extreme-claim",
        regulation = "广告法 §9",
        keywords = listOf("最佳", "第一"),
        severity = Severity.Violation
    )
    private val rule2 = AdLawRule(
        id = "guarantee-002",
        category = "absolute-claim",
        regulation = "广告法 §28",
        keywords = listOf("保证"),
        severity = Severity.Warning
    )
    private val matcher = AdLawRuleMatcher(listOf(rule1, rule2))

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
        assertEquals("extreme-claim", hits[0].category)
        assertEquals("广告法 §9", hits[0].regulation)
        assertEquals(Severity.Violation, hits[0].severity)
    }
}

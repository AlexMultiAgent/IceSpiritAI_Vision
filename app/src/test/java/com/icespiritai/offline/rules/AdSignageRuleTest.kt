package com.icespiritai.offline.rules

import com.icespiritai.offline.domain.Severity
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AdSignageRuleTest {

    private val json = Json { ignoreUnknownKeys = true }

    @Test
    fun roundtrip_serializesAndDeserializesAllFields() {
        val rule = AdSignageRule(
            id = "extreme-001",
            category = "extreme-claim",
            regulation = "广告法 §9",
            keywords = listOf("最佳", "第一"),
            severity = Severity.Violation,
            lawText = "第九条 广告不得有下列情形：（三）使用“国家级”、“最高级”、“最佳”等用语。",
        )
        val encoded = json.encodeToString(AdSignageRule.serializer(), rule)
        val decoded = json.decodeFromString(AdSignageRule.serializer(), encoded)
        assertEquals(rule, decoded)
    }

    @Test
    fun deserializesFromJsonString() {
        val src = """
            {
              "id": "extreme-001",
              "category": "extreme-claim",
              "regulation": "广告法 §9",
              "keywords": ["最佳", "第一"],
              "severity": "Violation",
              "lawText": "第九条 广告不得有下列情形：（三）使用“国家级”、“最高级”、“最佳”等用语。"
            }
        """.trimIndent()
        val rule = json.decodeFromString(AdSignageRule.serializer(), src)
        assertEquals("extreme-001", rule.id)
        assertEquals(Severity.Violation, rule.severity)
        assertEquals(listOf("最佳", "第一"), rule.keywords)
        assertEquals("第九条 广告不得有下列情形：（三）使用“国家级”、“最高级”、“最佳”等用语。", rule.lawText)
    }

    @Test
    fun severityStringValuesAreInfoWarningViolation() {
        for (s in Severity.entries) {
            val rule = AdSignageRule("x", "c", "r", emptyList(), s)
            val encoded = json.encodeToString(AdSignageRule.serializer(), rule)
            assertEquals(true, encoded.contains("\"${s.name}\""))
        }
    }

    @Test
    fun serializesWithSourceMarkers() {
        val rule = AdSignageRule(
            id = "ad_signage_art11_test",
            category = "signage",
            regulation = "《广告法》第十一条第二款",
            keywords = listOf("万"),
            severity = Severity.Warning,
            sourceMarkers = listOf("据", "报告")
        )
        val json = Json.encodeToString(AdSignageRule.serializer(), rule)
        assertTrue(json.contains("\"sourceMarkers\":[\"据\",\"报告\"]"))
    }

    @Test
    fun omitsEmptySourceMarkersInJson() {
        val rule = AdSignageRule(
            id = "x", category = "signage", regulation = "r",
            keywords = listOf("k"), severity = Severity.Warning
        )
        val json = Json.encodeToString(AdSignageRule.serializer(), rule)
        // 默认值不写进 JSON — 旧 JSON 反序列化得到 emptyList()
        assertFalse(json.contains("sourceMarkers"))
    }

    @Test
    fun deserializesLegacyJsonIgnoringSourceMarkers() {
        val legacyJson = """{"id":"x","category":"signage","regulation":"r","keywords":["k"],"severity":"Warning"}"""
        val rule = Json.decodeFromString(AdSignageRule.serializer(), legacyJson)
        assertEquals(emptyList<String>(), rule.sourceMarkers)
    }
}
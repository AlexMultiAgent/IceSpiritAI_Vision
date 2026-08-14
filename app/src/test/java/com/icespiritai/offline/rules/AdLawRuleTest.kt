package com.icespiritai.offline.rules

import com.icespiritai.offline.domain.Severity
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Test

class AdLawRuleTest {

    private val json = Json { ignoreUnknownKeys = true }

    @Test
    fun roundtrip_serializesAndDeserializesAllFields() {
        val rule = AdLawRule(
            id = "extreme-001",
            category = "extreme-claim",
            regulation = "广告法 §9",
            keywords = listOf("最佳", "第一"),
            severity = Severity.Violation
        )
        val encoded = json.encodeToString(AdLawRule.serializer(), rule)
        val decoded = json.decodeFromString(AdLawRule.serializer(), encoded)
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
              "severity": "Violation"
            }
        """.trimIndent()
        val rule = json.decodeFromString(AdLawRule.serializer(), src)
        assertEquals("extreme-001", rule.id)
        assertEquals(Severity.Violation, rule.severity)
        assertEquals(listOf("最佳", "第一"), rule.keywords)
    }

    @Test
    fun severityStringValuesAreInfoWarningViolation() {
        for (s in Severity.entries) {
            // ensure each enum constant serializes to its name string
            val rule = AdLawRule("x", "c", "r", emptyList(), s)
            val encoded = json.encodeToString(AdLawRule.serializer(), rule)
            assertEquals(true, encoded.contains("\"${s.name}\""))
        }
    }
}
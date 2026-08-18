package com.icespiritai.offline.rules

import com.icespiritai.offline.domain.Severity
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Test

class FoodLabelRuleTest {

    private val json = Json { ignoreUnknownKeys = true }

    @Test
    fun roundtrip_serializesAndDeserializesAllFields() {
        val rule = FoodLabelRule(
            id = "food_nozero_add",
            category = "functional_claim",
            regulation = "食品标识监督管理办法 §9 (七)",
            keywords = listOf("零添加", "不添加"),
            severity = Severity.Violation,
            lawText = "第九条 食品标识不得标注下列内容：（七）'零添加''不添加'等类似声称。",
        )
        val encoded = json.encodeToString(FoodLabelRule.serializer(), rule)
        val decoded = json.decodeFromString(FoodLabelRule.serializer(), encoded)
        assertEquals(rule, decoded)
    }

    @Test
    fun deserializesFromJsonString() {
        val src = """
            {
              "id": "food_nozero_add",
              "category": "functional_claim",
              "regulation": "食品标识监督管理办法 §9 (七)",
              "keywords": ["零添加", "不添加"],
              "severity": "Violation",
              "lawText": "第九条 食品标识不得标注下列内容：（七）'零添加''不添加'等类似声称。"
            }
        """.trimIndent()
        val rule = json.decodeFromString(FoodLabelRule.serializer(), src)
        assertEquals("food_nozero_add", rule.id)
        assertEquals(Severity.Violation, rule.severity)
        assertEquals(listOf("零添加", "不添加"), rule.keywords)
        assertEquals("第九条 食品标识不得标注下列内容：（七）'零添加''不添加'等类似声称。", rule.lawText)
    }

    @Test
    fun severityStringValuesAreInfoWarningViolation() {
        for (s in Severity.entries) {
            val rule = FoodLabelRule("x", "c", "r", emptyList(), s)
            val encoded = json.encodeToString(FoodLabelRule.serializer(), rule)
            assertEquals(true, encoded.contains("\"${s.name}\""))
        }
    }
}
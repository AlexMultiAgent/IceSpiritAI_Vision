package com.icespiritai.offline.rules

import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

class AssetRuleLoaderTest {

    @Test
    fun load_parsesValidJsonIntoAdRules() {
        // Mirror the actual on-disk shape: `{ "version": N, "rules": [...] }`.
        // The previous test used a bare top-level array and so missed the
        // wrapper mismatch that surfaced on-device as ErrorCode.RULES_FAILED.
        val json = kotlinx.serialization.json.Json {
            ignoreUnknownKeys = true
            isLenient = true
        }
        val src = """
            {
                  "version": 1,
                  "rules": [
                    {"id":"a","category":"x","regulation":"r","keywords":["k1","k2"],"severity":"Violation"},
                    {"id":"b","category":"y","regulation":"r","keywords":["k3"],"severity":"Warning"}
                  ]
                }
        """.trimIndent()
        val set = json.decodeFromString(AdSignageRuleSet.serializer(), src)
        assertEquals(2, set.rules.size)
        assertEquals(1, set.version)
        assertEquals("a", set.rules[0].id)
        assertEquals(listOf("k1", "k2"), set.rules[0].keywords)
    }

    @Test
    fun load_parsesValidJsonIntoFoodLabelRules() {
        val json = kotlinx.serialization.json.Json {
            ignoreUnknownKeys = true
            isLenient = true
        }
        val src = """
            {
                  "version": 1,
                  "rules": [
                    {"id":"f1","category":"functional_claim","regulation":"x","keywords":["零添加"],"severity":"Violation"},
                    {"id":"f2","category":"specific_food","regulation":"y","keywords":["特殊医学用途"],"severity":"Info"}
                  ]
                }
        """.trimIndent()
        val set = json.decodeFromString(FoodLabelRuleSet.serializer(), src)
        assertEquals(2, set.rules.size)
        assertEquals("f1", set.rules[0].id)
        assertEquals(listOf("零添加"), set.rules[0].keywords)
        assertEquals("functional_claim", set.rules[0].category)
    }

    @Test
    fun load_parsesActualBundledAdSignageAssetShape() {
        // Parses the exact JSON we ship in app/src/main/assets/rules/ad_signage_rules.json.
        // Catches drift between the build-time constant in
        // prepare-ocr-rules.gradle.kts and the loader's expected shape.
        val json = kotlinx.serialization.json.Json {
            ignoreUnknownKeys = true
            isLenient = true
        }
        val src = java.io.File(
            "src/main/assets/rules/ad_signage_rules.json"
        ).readText(Charsets.UTF_8)
        val set = json.decodeFromString(AdSignageRuleSet.serializer(), src)
        assertEquals(12, set.version)
        assertTrue(
            "shipped ad_signage_rules.json must carry at least one rule (10 golden + 70+ incremental rules)",
            set.rules.size >= 1,
        )
        assertTrue(
            "shipped ad_signage_rules.json must bundle 10 golden + 70+ incremental rules (v1: 10, v2: +32, v3: +43 = 85, v4: +31 = 116, v5: +3 = 119, v6: +1 = 120, v7: +0 new + 1 keyword = 120, v8: +0 new + 4 regulation fixes = 120, v9: +8 new + 2 strengthened = 129, v10: +0 new + keyword extensions = 129, v11: +15 new = 144, v12: +2 new = 146)",
            set.rules.size >= 140,
        )
        assertTrue("every shipped rule must bundle its full provision text", set.rules.all { it.lawText.isNotBlank() })
        assertTrue(
            "every shipped ad rule id must be unique",
            set.rules.map { it.id }.toSet().size == set.rules.size,
        )
    }

    @Test
    fun load_parsesActualBundledFoodLabelAssetShape() {
        // Same drift guard for the food-label domain.
        val json = kotlinx.serialization.json.Json {
            ignoreUnknownKeys = true
            isLenient = true
        }
        val src = java.io.File(
            "src/main/assets/rules/food_label_rules.json"
        ).readText(Charsets.UTF_8)
        val set = json.decodeFromString(FoodLabelRuleSet.serializer(), src)
        assertEquals(4, set.version)
        assertTrue(
            "shipped food_label_rules.json must carry at least one rule",
            set.rules.size >= 1,
        )
        assertTrue(
            "shipped food_label_rules.json must bundle 6 golden + 30 incremental + 29 v3 + 1 v4-split rules",
            set.rules.size >= 65,
        )
        assertTrue(
            "every shipped food rule must bundle its full provision text",
            set.rules.all { it.lawText.isNotBlank() },
        )
        assertTrue(
            "every shipped food rule id must be unique",
            set.rules.map { it.id }.toSet().size == set.rules.size,
        )
    }

    @Test
    fun invalidJson_throwsSerializationError() {
        val bad = "{ not valid json"
        assertThrows(kotlinx.serialization.SerializationException::class.java) {
            kotlinx.serialization.json.Json.decodeFromString(
                AdSignageRuleSet.serializer(),
                bad,
            )
        }
    }
}
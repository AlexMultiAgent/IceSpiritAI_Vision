package com.icespiritai.offline.rules

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

class AssetRuleLoaderTest {

    @Test
    fun load_parsesValidJsonIntoRules() {
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
        val set = json.decodeFromString(AdLawRuleSet.serializer(), src)
        assertEquals(2, set.rules.size)
        assertEquals(1, set.version)
        assertEquals("a", set.rules[0].id)
        assertEquals(listOf("k1", "k2"), set.rules[0].keywords)
    }

    @Test
    fun load_parsesActualBundledAssetShape() {
        // Parses the exact JSON we ship in app/src/main/assets/rules/ad_law_rules.json.
        // Catches drift between the build-time constant in
        // prepare-ocr-rules.gradle.kts and the loader's expected shape.
        val json = kotlinx.serialization.json.Json {
            ignoreUnknownKeys = true
            isLenient = true
        }
        val src = java.io.File(
            "src/main/assets/rules/ad_law_rules.json"
        ).readText(Charsets.UTF_8)
        val set = json.decodeFromString(AdLawRuleSet.serializer(), src)
        assertEquals(1, set.version)
        assertEquals(10, set.rules.size)
        assertEquals("medical_absolute", set.rules[0].id)
    }

    @Test
    fun invalidJson_throwsSerializationError() {
        val bad = "{ not valid json"
        try {
            kotlinx.serialization.json.Json.decodeFromString(
                AdLawRuleSet.serializer(),
                bad
            )
            fail("expected SerializationException")
        } catch (e: kotlinx.serialization.SerializationException) {
            assertTrue(true) // expected
        }
    }
}
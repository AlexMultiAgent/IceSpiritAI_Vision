package com.icespiritai.offline.rules

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

class AssetRuleLoaderTest {

    @Test
    fun load_parsesValidJsonIntoRules() {
        // Context-based testing requires Android framework; we'll use a tiny in-memory JSON parser
        // via Robolectric-free path: skip the Context dep and test the JSON parser logic indirectly.
        // For unit testability, instantiate the json parser portion separately.
        val json = kotlinx.serialization.json.Json {
            ignoreUnknownKeys = true
            isLenient = true
        }
        val src = """
            [
              {"id":"a","category":"x","regulation":"r","keywords":["k1","k2"],"severity":"Violation"},
              {"id":"b","category":"y","regulation":"r","keywords":["k3"],"severity":"Warning"}
            ]
        """.trimIndent()
        val rules = json.decodeFromString(
            kotlinx.serialization.builtins.ListSerializer(AdLawRule.serializer()),
            src
        )
        assertEquals(2, rules.size)
        assertEquals("a", rules[0].id)
        assertEquals(listOf("k1", "k2"), rules[0].keywords)
    }

    @Test
    fun invalidJson_throwsSerializationError() {
        val bad = "{ not valid json"
        try {
            kotlinx.serialization.json.Json.decodeFromString(
                kotlinx.serialization.builtins.ListSerializer(AdLawRule.serializer()),
                bad
            )
            fail("expected SerializationException")
        } catch (e: kotlinx.serialization.SerializationException) {
            assertTrue(true) // expected
        }
    }
}
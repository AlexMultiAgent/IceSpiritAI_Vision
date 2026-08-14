package com.icespiritai.offline.rules

import android.content.Context
import com.icespiritai.offline.domain.RuleLoadFailed
import kotlinx.serialization.SerializationException
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json

class AssetRuleLoader(
    private val context: Context,
    private val path: String = "rules/ad_law_rules.json"
) {
    private val json = Json {
        ignoreUnknownKeys = true
        isLenient = true
    }

    fun load(): List<AdLawRule> {
        val raw = try {
            context.assets.open(path).bufferedReader(Charsets.UTF_8).use { it.readText() }
        } catch (e: Exception) {
            throw RuleLoadFailed("Failed to read assets/$path: ${e.message}", e)
        }
        return try {
            json.decodeFromString(ListSerializer(AdLawRule.serializer()), raw)
        } catch (e: SerializationException) {
            throw RuleLoadFailed("Failed to parse $path as List<AdLawRule>: ${e.message}", e)
        }
    }
}
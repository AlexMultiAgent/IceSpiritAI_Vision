package com.icespiritai.offline.rules

import android.content.Context
import com.icespiritai.offline.domain.RuleLoadFailed
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json

class AdSignageRuleLoader(
    private val context: Context,
    private val path: String = "rules/ad_signage_rules.json",
) {
    private val json = Json {
        ignoreUnknownKeys = true
        isLenient = true
    }

    fun load(): List<AdSignageRule> {
        val raw = try {
            context.assets.open(path).bufferedReader(Charsets.UTF_8).use { it.readText() }
        } catch (e: Exception) {
            throw RuleLoadFailed("Failed to read assets/$path: ${e.message}", e)
        }
        return try {
            val set = json.decodeFromString(AdSignageRuleSet.serializer(), raw)
            set.rules
        } catch (e: SerializationException) {
            throw RuleLoadFailed("Failed to parse $path as AdSignageRuleSet: ${e.message}", e)
        }
    }
}

package com.icespiritai.offline.rules

import android.content.Context
import com.icespiritai.offline.domain.RuleLoadFailed
import kotlinx.serialization.SerializationException
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
            // Hotfix: the bundled JSON is wrapped in `{ "version": N, "rules": [...] }`
            // for forward-compat (Phase 2 / 2026-08-15). The previous loader tried
            // to decode the raw text as a bare List<AdLawRule>, which threw
            // SerializationException → RuleLoadFailed → ErrorCode.RULES_FAILED →
            // "规则库加载失败" UI string. Decode the wrapper and return the inner list.
            val set = json.decodeFromString(AdLawRuleSet.serializer(), raw)
            set.rules
        } catch (e: SerializationException) {
            throw RuleLoadFailed("Failed to parse $path as AdLawRuleSet: ${e.message}", e)
        }
    }
}
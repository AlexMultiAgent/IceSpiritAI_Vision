package com.icespiritai.offline.rules

import com.icespiritai.offline.domain.Severity
import kotlinx.serialization.Serializable

@Serializable
data class AdLawRule(
    val id: String,
    val category: String,
    val regulation: String,
    val keywords: List<String>,
    val severity: Severity
)

/**
 * Top-level wrapper for the bundled `ad_law_rules.json` assets. The
 * `version` field is a forward-compatible extension anchor — future
 * rule-schema migrations (e.g. weighted keywords, ignoreCase flag) can
 * detect the old shape and dispatch appropriately. The `rules` list is the
 * authoritative payload.
 */
@Serializable
data class AdLawRuleSet(
    val version: Int,
    val rules: List<AdLawRule>,
)
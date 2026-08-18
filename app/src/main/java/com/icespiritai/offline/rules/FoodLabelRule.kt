package com.icespiritai.offline.rules

import com.icespiritai.offline.domain.Severity
import kotlinx.serialization.Serializable

@Serializable
data class FoodLabelRule(
    val id: String,
    val category: String,
    val regulation: String,
    val keywords: List<String>,
    val severity: Severity,
    /**
     * Full text of the cited provision(s), bundled with the rule so the result
     * card can show the exact legal wording offline without a network lookup.
     */
    val lawText: String = "",
)

/**
 * Top-level wrapper for the bundled `food_label_rules.json` asset. Mirrors
 * the AdSignageRuleSet shape so both rule domains can be loaded by parallel
 * `Loader` classes and consumed by structurally identical matchers.
 */
@Serializable
data class FoodLabelRuleSet(
    val version: Int,
    val rules: List<FoodLabelRule>,
)

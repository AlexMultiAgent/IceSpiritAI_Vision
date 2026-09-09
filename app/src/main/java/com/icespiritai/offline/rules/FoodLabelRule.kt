package com.icespiritai.offline.rules

import com.icespiritai.offline.domain.Severity
import kotlinx.serialization.Serializable

@Serializable
data class FoodLabelRule(
    override val id: String,
    override val category: String,
    override val regulation: String,
    override val keywords: List<String>,
    override val severity: Severity,
    /**
     * Full text of the cited provision(s), bundled with the rule so the result
     * card can show the exact legal wording offline without a network lookup.
     */
    override val lawText: String = "",
) : Rule

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

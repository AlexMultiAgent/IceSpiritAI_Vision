package com.icespiritai.offline.rules

import com.icespiritai.offline.domain.Severity
import kotlinx.serialization.Serializable

@Serializable
data class AdSignageRule(
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
    val sourceMarkers: List<String> = emptyList(),
    /**
     * Domain-anchor substrings: when this list is non-empty, the rule only
     * fires if the scanned text contains at least one of these anchors after
     * `TextNormalizer.forMatching` normalization. Inverse polarity to
     * `sourceMarkers` (which suppresses on presence; `categoryAnchors`
     * requires presence). Default empty = backward-compatible, no gating.
     *
     * Use case: domain-specific rules (pesticide / veterinary / medical /
     * cosmetic / minor) whose keywords may overlap with general categories
     * (e.g.「不如」「按摩」「美白」「儿童」) and would otherwise fire on
     * non-domain ads. The anchor gate scopes the rule to its real domain.
     */
    val categoryAnchors: List<String> = emptyList(),
)

/**
 * Top-level wrapper for the bundled `ad_signage_rules.json` asset. The
 * `version` field is a forward-compatible extension anchor — future
 * rule-schema migrations (e.g. weighted keywords, ignoreCase flag) can
 * detect the old shape and dispatch appropriately. The `rules` list is the
 * authoritative payload.
 */
@Serializable
data class AdSignageRuleSet(
    val version: Int,
    val rules: List<AdSignageRule>,
)

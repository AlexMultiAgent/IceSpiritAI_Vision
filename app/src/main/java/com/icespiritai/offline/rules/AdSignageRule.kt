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
    /**
     * Inverse-polarity domain-anchor: when this list is non-empty, the rule
     * ONLY fires if the scanned text contains NONE of these anchors after
     * `TextNormalizer.forMatching` normalization. Inverse of `categoryAnchors`
     * (which requires presence; `categoryAnchorsAbsent` requires absence).
     * Default empty = backward-compatible, no gating.
     *
     * Use case: rules whose legal target is "non-medical-institution ads
     * advertising disease treatment" (广告法 §17). The keywords are chronic
     * disease names + TCM medical terminology — those substrings are ALSO
     * legitimately used by registered medical institutions (hospital /
     * clinic / OTC product) to describe their own indications. The absent
     * gate scopes it to the illegal-target subset (non-medical ad text
     * mentioning those same terms).
     */
    val categoryAnchorsAbsent: List<String> = emptyList(),
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

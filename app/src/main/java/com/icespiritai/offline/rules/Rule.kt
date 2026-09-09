package com.icespiritai.offline.rules

import com.icespiritai.offline.domain.Severity

/**
 * Common shape for an OCR rule consumed by [AhoCorasickMatcher]. Both
 * [AdSignageRule] (广告招牌 domain) and [FoodLabelRule] (食品标识 domain)
 * implement this interface; the three "gating" lists
 * ([sourceMarkers] / [categoryAnchors] / [categoryAnchorsAbsent]) default
 * to empty for rules that don't need absence / domain-anchor filtering,
 * which is the common case (FoodLabel rules never use them today; 117 of
 * 129 AdSignage rules also leave them empty).
 *
 * The interface is intentionally separate from the @Serializable data
 * classes so the rule JSON schema isn't coupled to the matcher's internal
 * contract — and so adding a new domain (e.g. medical / cosmetic) is a
 * one-liner implementing [Rule] with the data class fields.
 *
 * Optimization note (2026-09-10): the AdSignageRuleMatcher +
 * FoodLabelRuleMatcher pair was 606 LOC of near-identical AC init +
 * scan logic before this refactor (~410 LOC of duplication). The base
 * class now owns all three dedup phases; only Phase 3 (per-domain
 * gating + RuleHit domain tag) is left to the subclass. Adding a third
 * domain is now ~30 LOC instead of ~200.
 */
interface Rule {
    val id: String
    val category: String
    val regulation: String
    val keywords: List<String>
    val severity: Severity
    val lawText: String
        get() = ""
    /** See [AdSignageRule.sourceMarkers] — empty default = no absence gating. */
    val sourceMarkers: List<String>
        get() = emptyList()
    /** See [AdSignageRule.categoryAnchors] — empty default = no anchor gating. */
    val categoryAnchors: List<String>
        get() = emptyList()
    /** See [AdSignageRule.categoryAnchorsAbsent] — empty default = no gating. */
    val categoryAnchorsAbsent: List<String>
        get() = emptyList()
}

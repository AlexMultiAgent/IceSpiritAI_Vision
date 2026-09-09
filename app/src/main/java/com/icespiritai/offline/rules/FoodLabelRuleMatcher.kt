package com.icespiritai.offline.rules

import com.icespiritai.offline.domain.CategoryDisplay
import com.icespiritai.offline.domain.RuleHit
import com.icespiritai.offline.domain.TextNormalizer

/**
 * 食品标识 domain matcher. Subclasses [AhoCorasickMatcher] to inherit
 * Phase 1/2/2.5 keyword scan + dedup and stamps each emitted
 * [RuleHit] with [CategoryDisplay.DOMAIN_FOOD]. No source-marker /
 * anchor presence gates — the bundled `food_label_rules.json` carries
 * no entries with `sourceMarkers` / `categoryAnchors` /
 * `categoryAnchorsAbsent` populated (all 66 current rules leave them
 * empty).
 *
 * If a future food rule needs absence / domain-anchor gating, add the
 * appropriate field(s) to [FoodLabelRule] + Rule interface, build the
 * extra AC pass + emit filter here, mirroring [AdSignageRuleMatcher]'s
 * shape.
 */
class FoodLabelRuleMatcher(rules: List<FoodLabelRule>) : AhoCorasickMatcher<FoodLabelRule>(rules) {

    override fun scan(text: String): List<RuleHit> {
        if (text.isEmpty()) return emptyList()
        val normalized = TextNormalizer.forMatching(text)
        if (normalized.isEmpty()) return emptyList()

        val substringDeduped = runKeywordScan(normalized)

        val hits = mutableListOf<RuleHit>()
        for ((key, matched) in substringDeduped) {
            val (ruleId, _) = key
            val rule = ruleById[ruleId] ?: continue
            hits.add(
                RuleHit(
                    ruleId = rule.id,
                    matchedText = matched,
                    category = rule.category,
                    regulation = rule.regulation,
                    lawText = rule.lawText,
                    severity = rule.severity,
                    domain = CategoryDisplay.DOMAIN_FOOD,
                )
            )
        }
        return hits
    }
}
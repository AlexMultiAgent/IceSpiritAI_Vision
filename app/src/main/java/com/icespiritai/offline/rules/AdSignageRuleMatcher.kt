package com.icespiritai.offline.rules

import com.hankcs.algorithm.AhoCorasickDoubleArrayTrie
import com.icespiritai.offline.domain.CategoryDisplay
import com.icespiritai.offline.domain.RuleHit
import com.icespiritai.offline.domain.TextNormalizer
import java.util.TreeMap

class AdSignageRuleMatcher(rules: List<AdSignageRule>) : RuleMatcher {

    private val ruleById: Map<String, AdSignageRule> = rules.associateBy { it.id }
    private val keywordTrie = AhoCorasickDoubleArrayTrie<List<String>>()
    private val sourceMarkerTrie = AhoCorasickDoubleArrayTrie<List<String>>()
    private val hasAnyAbsenceRule: Boolean

    init {
        // Two passes' worth of normalized keyword -> ALL rule ids that declare it.
        // A keyword shared by two rules (e.g. "第一" in both the education and the
        // general absolute-claim rules) must attribute the hit to every applicable
        // rule instead of silently shadowing all but the last one. TreeMap gives
        // deterministic (sorted) iteration order; the library accepts any
        // Map<String, V>.
        val keywordToRuleIds = TreeMap<String, List<String>>()
        val sourceMarkerToRuleIds = TreeMap<String, List<String>>()
        for (rule in rules) {
            for (kw in rule.keywords) {
                val key = TextNormalizer.forMatching(kw)
                if (key.isNotEmpty()) {
                    keywordToRuleIds[key] = (keywordToRuleIds[key] ?: emptyList()) + rule.id
                }
            }
            for (sm in rule.sourceMarkers) {
                val key = TextNormalizer.forMatching(sm)
                if (key.isNotEmpty()) {
                    sourceMarkerToRuleIds[key] = (sourceMarkerToRuleIds[key] ?: emptyList()) + rule.id
                }
            }
        }
        if (keywordToRuleIds.isNotEmpty()) keywordTrie.build(keywordToRuleIds)
        if (sourceMarkerToRuleIds.isNotEmpty()) sourceMarkerTrie.build(sourceMarkerToRuleIds)
        // Absence composite logic only runs when at least one rule declared
        // sourceMarkers. 117 existing rules all default to sourceMarkers =
        // emptyList(), so the legacy single-pass code path stays byte-equivalent.
        hasAnyAbsenceRule = rules.any { it.sourceMarkers.isNotEmpty() }
    }

    override fun scan(text: String): List<RuleHit> {
        if (text.isEmpty()) return emptyList()
        val normalized = TextNormalizer.forMatching(text)
        if (normalized.isEmpty()) return emptyList()

        val hits = mutableListOf<RuleHit>()
        val sourceMarkerHitRules = mutableSetOf<String>()

        val keywordHandler = AhoCorasickDoubleArrayTrie.IHit<List<String>> { begin, end, ruleIds ->
            val matched = normalized.substring(begin, end)
            for (ruleId in ruleIds) {
                val rule = ruleById[ruleId] ?: continue
                // Dedup policy:
                //   - Absence rules (sourceMarkers non-empty): one hit per rule
                //     (the rule as a whole either fires or doesn't; matchedText
                //     is the first claim keyword found).
                //   - Legacy rules (sourceMarkers empty): unchanged — one hit per
                //     (ruleId, matchedText) so a multi-keyword rule still emits
                //     one hit per keyword (e.g. "血压计 + 血糖仪 + 助听器" → 3 hits).
                val isNew = if (rule.sourceMarkers.isNotEmpty()) {
                    hits.none { it.ruleId == rule.id }
                } else {
                    hits.none { it.ruleId == rule.id && it.matchedText == matched }
                }
                if (isNew) {
                    hits.add(
                        RuleHit(
                            ruleId = rule.id,
                            matchedText = matched,
                            category = rule.category,
                            regulation = rule.regulation,
                            lawText = rule.lawText,
                            severity = rule.severity,
                            domain = CategoryDisplay.DOMAIN_AD,
                        )
                    )
                }
            }
        }

        // Source marker pass: only collect which rule ids had any source marker
        // hit. We don't materialize RuleHit for source matches — claim hits are
        // the user-visible output; source markers are just a gate.
        val sourceMarkerHandler = AhoCorasickDoubleArrayTrie.IHit<List<String>> { _, _, ruleIds ->
            for (ruleId in ruleIds) sourceMarkerHitRules += ruleId
        }

        keywordTrie.parseText(normalized, keywordHandler)
        if (hasAnyAbsenceRule) {
            sourceMarkerTrie.parseText(normalized, sourceMarkerHandler)
        }

        // For absence rules, suppress claim hits whenever the rule's source marker
        // was matched anywhere in the text (one source marker present = claim
        // considered cited, regardless of which claim keyword matched). Non-
        // absence rules (sourceMarkers empty) pass through unchanged.
        return hits.filter { hit ->
            val rule = ruleById[hit.ruleId] ?: return@filter true
            if (rule.sourceMarkers.isEmpty()) true
            else rule.id !in sourceMarkerHitRules
        }
    }
}
